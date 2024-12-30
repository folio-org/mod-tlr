package org.folio.service.impl;

import static org.folio.domain.dto.TransactionStatusResponse.RoleEnum.BORROWING_PICKUP;
import static org.folio.domain.dto.TransactionStatusResponse.RoleEnum.LENDER;
import static org.folio.domain.dto.TransactionStatusResponse.RoleEnum.PICKUP;
import static org.folio.domain.dto.TransactionStatusResponse.StatusEnum.CLOSED;
import static org.folio.domain.dto.TransactionStatusResponse.StatusEnum.ITEM_CHECKED_IN;
import static org.folio.domain.dto.TransactionStatusResponse.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.support.KafkaEvent.EventType.UPDATED;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.Loan;
import org.folio.domain.dto.TransactionStatus.StatusEnum;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.dto.TransactionStatusResponse.RoleEnum;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DcbService;
import org.folio.service.KafkaEventHandler;
import org.folio.support.KafkaEvent;
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

  @Override
  public void handle(KafkaEvent<Loan> event) {
    log.info("handle:: processing loan event: {}", event::getId);
    if (event.getType() == UPDATED) {
      handleUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getType);
    }
    log.info("handle:: loan event processed: {}", event::getId);
  }

  private void handleUpdateEvent(KafkaEvent<Loan> event) {
    Loan loan = event.getData().getNewVersion();
    String loanAction = loan.getAction();
    log.info("handle:: loan action: {}", loanAction);
    if (LOAN_ACTION_CHECKED_IN.equals(loanAction)) {
      log.info("handleUpdateEvent:: processing loan check-in event: {}", event::getId);
      handleCheckInEvent(event);
    } else {
      log.info("handleUpdateEvent:: ignoring loan update event with unsupported loan action: {}", loanAction);
    }
  }

  private void handleCheckInEvent(KafkaEvent<Loan> event) {
    updateEcsTlr(event.getData().getNewVersion(), event.getTenant());
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
  }

}
