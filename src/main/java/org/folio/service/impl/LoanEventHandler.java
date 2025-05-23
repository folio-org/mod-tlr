package org.folio.service.impl;

import static org.folio.domain.dto.TransactionStatusResponse.RoleEnum.BORROWING_PICKUP;
import static org.folio.domain.dto.TransactionStatusResponse.RoleEnum.LENDER;
import static org.folio.domain.dto.TransactionStatusResponse.RoleEnum.PICKUP;
import static org.folio.domain.dto.TransactionStatusResponse.StatusEnum.CLOSED;
import static org.folio.domain.dto.TransactionStatusResponse.StatusEnum.ITEM_CHECKED_IN;
import static org.folio.domain.dto.TransactionStatusResponse.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.support.kafka.EventType.UPDATE;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.folio.client.feign.LoanStorageClient;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TransactionStatus.StatusEnum;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.dto.TransactionStatusResponse.RoleEnum;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.ConsortiaService;
import org.folio.service.DcbService;
import org.folio.service.KafkaEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.CqlQuery;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class LoanEventHandler implements KafkaEventHandler<Loan> {
  private static final String LOAN_ACTION_CHECKED_IN = "checkedin";
  private static final EnumSet<TransactionStatusResponse.StatusEnum>
    RELEVANT_TRANSACTION_STATUSES_FOR_CHECK_IN = EnumSet.of(ITEM_CHECKED_OUT, ITEM_CHECKED_IN, CLOSED);

  private final DcbService dcbService;
  private final EcsTlrRepository ecsTlrRepository;
  private final SystemUserScopedExecutionService executionService;
  private final ConsortiaService consortiaService;
  private final LoanStorageClient loanStorageClient;

  @Override
  public void handle(KafkaEvent<Loan> event) {
    log.info("handle:: processing loan event: {}", event::getId);
    if (event.getGenericType() == UPDATE) {
      handleUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getGenericType);
    }
    log.info("handle:: loan event processed: {}", event::getId);
  }

  private void handleUpdateEvent(KafkaEvent<Loan> event) {
    if (isRenewalEvent(event)) {
      log.info("handleUpdateEvent:: processing renewal update event");
      updateLoans(event.getNewVersion(), event.getTenant());
      return;
    }
    Loan loan = event.getNewVersion();
    String loanAction = loan.getAction();
    log.info("handle:: loan action: {}", loanAction);
    if (LOAN_ACTION_CHECKED_IN.equals(loanAction)) {
      log.info("handleUpdateEvent:: processing loan check-in event: {}", event::getId);
      handleCheckInEvent(event);
    } else {
      log.info("handleUpdateEvent:: ignoring loan update event with unsupported loan action: {}", loanAction);
    }
  }

  private boolean isRenewalEvent(KafkaEvent<Loan> event) {
    int newRenewalCount = getRenewalCountOrDefault(event.getNewVersion());
    int oldRenewalCount = getRenewalCountOrDefault(event.getOldVersion());

    log.info("isRenewalEvent:: comparing renewal counts - newRenewalCount: {}, oldRenewalCount: {}",
      newRenewalCount, oldRenewalCount);

    return newRenewalCount > oldRenewalCount;
  }

  private int getRenewalCountOrDefault(Loan loan) {
    Integer renewalCount = loan.getRenewalCount();
    return renewalCount != null ? renewalCount : 0;
  }

  private void updateLoans(Loan updatedLoan, String eventTenantId) {
    log.info("updateLoans:: parameters updatedLoan: {}, eventTenantId: {}, ",
      updatedLoan::getId, () -> eventTenantId);

    consortiaService.getAllConsortiumTenants().stream()
      .map(Tenant::getId)
      .filter(tenantId -> !tenantId.equals(eventTenantId))
      .forEach(tenantId -> executionService.executeAsyncSystemUserScoped(tenantId,
        () -> synchronizeOpenLoanWithUpdatedLoan(updatedLoan)));
  }

  private void synchronizeOpenLoanWithUpdatedLoan(Loan updatedLoan) {
    try {
      var loans = loanStorageClient.getByQuery(CqlQuery.exactMatch("itemId", updatedLoan.getItemId())
        .and(CqlQuery.exactMatch("status.name", "Open")), 1).getLoans();
      if (loans.isEmpty()) {
        log.info("synchronizeOpenLoanWithUpdatedLoan:: no open loans found for itemId: {}",
          updatedLoan.getItemId());
        return;
      }
      var loan = loans.get(0);
      log.info("synchronizeOpenLoanWithUpdatedLoan:: found loan with id: {}", loan::getId);
      loan.setRenewalCount(updatedLoan.getRenewalCount());
      loan.setDueDate(updatedLoan.getDueDate());
      loanStorageClient.updateLoan(loan.getId(), loan);
      log.info("synchronizeOpenLoanWithUpdatedLoan:: successfully updated loan with id: {}", loan::getId);
    } catch (Exception e) {
      log.error("synchronizeOpenLoanWithUpdatedLoan:: Failed to update loan for itemId: {}",
        updatedLoan::getItemId, () -> e);
    }
  }

  private void handleCheckInEvent(KafkaEvent<Loan> event) {
    updateEcsTlr(event.getNewVersion(), event.getTenant());
  }

  private void updateEcsTlr(Loan loan, String tenantId) {
    Collection<EcsTlrEntity> ecsTlrs = findEcsTlrs(loan);
    for (EcsTlrEntity ecsTlr : ecsTlrs) {
      log.info("updateEcsTlr:: checking ECS TLR {}", ecsTlr::getId);
      String primaryTenantId = ecsTlr.getPrimaryRequestTenantId();
      String secondaryTenantId = ecsTlr.getSecondaryRequestTenantId();
      UUID primaryTransactionId = ecsTlr.getPrimaryRequestDcbTransactionId();
      UUID secondaryTransactionId = ecsTlr.getSecondaryRequestDcbTransactionId();

      if (primaryTransactionId == null || secondaryTransactionId == null) {
        log.info("updateEcsTlr:: ECS TLR does not have primary/secondary transaction, skipping");
        continue;
      }

      boolean eventTenantIdIsPrimaryTenantId = tenantId.equals(primaryTenantId);
      boolean eventTenantIdIsSecondaryTenantId = tenantId.equals(secondaryTenantId);
      if (!(eventTenantIdIsPrimaryTenantId || eventTenantIdIsSecondaryTenantId)) {
        log.info("updateEcsTlr:: event tenant ID does not match ECS TLR's primary/secondary request " +
          "tenant ID, skipping");
        continue;
      }

      TransactionStatusResponse primaryTransaction = dcbService.getTransactionStatus(
        primaryTransactionId, primaryTenantId);
      TransactionStatusResponse.StatusEnum primaryTransactionStatus = primaryTransaction.getStatus();
      RoleEnum primaryTransactionRole = primaryTransaction.getRole();
      log.info("updateEcsTlr:: primary request transaction: status={}, role={}",
        primaryTransactionStatus, primaryTransactionRole);
      if (!RELEVANT_TRANSACTION_STATUSES_FOR_CHECK_IN.contains(primaryTransactionStatus)) {
        log.info("updateEcsTlrForLoan:: irrelevant primary request transaction status: {}",
          primaryTransaction);
        continue;
      }

      TransactionStatusResponse secondaryTransaction = dcbService.getTransactionStatus(
        secondaryTransactionId, secondaryTenantId);
      TransactionStatusResponse.StatusEnum secondaryTransactionStatus = secondaryTransaction.getStatus();
      RoleEnum secondaryTransactionRole = secondaryTransaction.getRole();
      log.info("updateEcsTlr:: secondary request transaction: status={}, role={}",
        secondaryTransactionStatus, secondaryTransactionRole);
      if (!RELEVANT_TRANSACTION_STATUSES_FOR_CHECK_IN.contains(secondaryTransactionStatus)) {
        log.info("updateEcsTlr:: irrelevant secondary request transaction status: {}",
          secondaryTransactionStatus);
        continue;
      }

      if (eventTenantIdIsPrimaryTenantId &&
        (primaryTransactionRole == BORROWING_PICKUP || primaryTransactionRole == PICKUP) &&
        (primaryTransactionStatus == ITEM_CHECKED_OUT || primaryTransactionStatus == ITEM_CHECKED_IN) &&
        secondaryTransactionRole == LENDER && secondaryTransactionStatus == ITEM_CHECKED_OUT) {

        log.info("updateEcsTlr:: check-in happened in primary request tenant ({}), updating transactions",
          primaryTenantId);
        dcbService.updateTransactionStatuses(StatusEnum.ITEM_CHECKED_IN, ecsTlr);
        return;
      }
      else if (eventTenantIdIsSecondaryTenantId && secondaryTransactionRole == LENDER &&
        (secondaryTransactionStatus == ITEM_CHECKED_IN || secondaryTransactionStatus == CLOSED) &&
        (primaryTransactionRole == BORROWING_PICKUP || primaryTransactionRole == PICKUP) &&
        primaryTransactionStatus == ITEM_CHECKED_IN) {

        log.info("updateEcsTlr:: check-in happened in secondary request tenant ({}), updating transactions", secondaryTenantId);
        dcbService.updateTransactionStatuses(StatusEnum.CLOSED, ecsTlr);
        return;
      }
      log.info("updateEcsTlr:: ECS TLR {} does not match loan update event, skipping", ecsTlr::getId);
    }
    log.info("updateEcsTlr:: suitable ECS TLR for loan {} in tenant {} was not found", loan.getId(), tenantId);
  }

  private Collection<EcsTlrEntity> findEcsTlrs(Loan loan) {
    log.info("findEcsTlrs:: searching ECS TLRs for item {}", loan::getItemId);
    List<EcsTlrEntity> ecsTlrs = ecsTlrRepository.findByItemId(UUID.fromString(loan.getItemId()));
    log.info("findEcsTlrs:: found {} ECS TLRs", ecsTlrs::size);

    return ecsTlrs;
  }

}
