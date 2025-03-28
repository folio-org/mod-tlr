package org.folio.service.impl;

import static org.folio.domain.dto.DcbTransaction.RoleEnum.BORROWER;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.BORROWING_PICKUP;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.LENDER;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.PICKUP;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.AWAITING_PICKUP;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.CANCELLED;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.CLOSED;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.CREATED;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.ITEM_CHECKED_IN;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.OPEN;

import java.util.UUID;

import org.folio.client.feign.DcbEcsTransactionClient;
import org.folio.client.feign.DcbTransactionClient;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.DcbTransaction.RoleEnum;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatus.StatusEnum;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.DcbService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class DcbServiceImpl implements DcbService {

  private final DcbEcsTransactionClient dcbEcsTransactionClient;
  private final DcbTransactionClient dcbTransactionClient;
  private final SystemUserScopedExecutionService executionService;

  public DcbServiceImpl(@Autowired DcbEcsTransactionClient dcbEcsTransactionClient,
    @Autowired DcbTransactionClient dcbTransactionClient,
    @Autowired SystemUserScopedExecutionService executionService) {

    this.dcbEcsTransactionClient = dcbEcsTransactionClient;
    this.dcbTransactionClient = dcbTransactionClient;
    this.executionService = executionService;
  }

  @Override
  public void createLendingTransaction(EcsTlrEntity ecsTlr) {
    log.info("createTransactions:: creating lending transaction for ECS TLR {}", ecsTlr::getId);
    DcbTransaction transaction = new DcbTransaction()
      .requestId(ecsTlr.getSecondaryRequestId().toString())
      .role(LENDER);
    final UUID transactionId = createTransaction(transaction, ecsTlr.getSecondaryRequestTenantId());
    ecsTlr.setSecondaryRequestDcbTransactionId(transactionId);
    log.info("createTransactions:: lending transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  @Override
  public void createBorrowerTransaction(EcsTlrEntity ecsTlr, Request request) {
    log.info("createBorrowerTransaction:: creating borrower transaction for ECS TLR {}", ecsTlr::getId);
    DcbTransaction transaction = buildTransaction(request, BORROWER, ecsTlr.getIntermediateRequestId());
    final UUID transactionId = createTransaction(transaction, ecsTlr.getIntermediateRequestTenantId());
    ecsTlr.setIntermediateRequestDcbTransactionId(transactionId);
    log.info("createBorrowerTransaction:: borrower transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  @Override
  public void createBorrowingPickupTransaction(EcsTlrEntity ecsTlr, Request request) {
    log.info("createBorrowingPickupTransaction:: creating borrowing-pickup transaction for ECS TLR {}",
      ecsTlr::getId);
    DcbTransaction transaction = buildTransaction(request, BORROWING_PICKUP, ecsTlr.getPrimaryRequestId());
    final UUID transactionId = createTransaction(transaction, ecsTlr.getPrimaryRequestTenantId());
    ecsTlr.setPrimaryRequestDcbTransactionId(transactionId);
    log.info("createBorrowingPickupTransaction:: borrowing-pickup transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  @Override
  public void createPickupTransaction(EcsTlrEntity ecsTlr, Request request) {
    log.info("createPickupTransaction:: creating pickup transaction for ECS TLR {}", ecsTlr.getId());
    DcbTransaction transaction = buildTransaction(request, PICKUP, ecsTlr.getPrimaryRequestId());
    final UUID transactionId = createTransaction(transaction, ecsTlr.getPrimaryRequestTenantId());
    ecsTlr.setPrimaryRequestDcbTransactionId(transactionId);
    log.info("createPickupTransaction:: pickup transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  private DcbTransaction buildTransaction(Request request, RoleEnum role, UUID requestId) {
    DcbItem dcbItem = new DcbItem()
      .id(request.getItemId())
      .title(request.getInstance().getTitle())
      .barcode(buildNonEmptyBarcode(request.getItem().getBarcode(), request.getItemId()));

    return new DcbTransaction()
      .requestId(requestId.toString())
      .item(dcbItem)
      .role(role);
  }

  private String buildNonEmptyBarcode(String barcode, String itemId) {
    if (barcode == null || barcode.isBlank()) {
      String autoGeneratedBarcode = "AUTO_" + itemId;
      log.info("buildNonEmptyBarcode:: barcode is null or empty, using autogenerated barcode: {}",
        autoGeneratedBarcode);
      return autoGeneratedBarcode;
    }

    return barcode;
  }

  private UUID createTransaction(DcbTransaction transaction, String tenantId) {
    final UUID transactionId = UUID.randomUUID();
    log.info("createTransaction:: creating transaction {} in tenant {}", transactionId, tenantId);
    var response = executionService.executeSystemUserScoped(tenantId,
      () -> dcbEcsTransactionClient.createTransaction(transactionId.toString(), transaction));
    log.info("createTransaction:: {} transaction {} created", transaction.getRole(), transactionId);
    log.debug("createTransaction:: {}", () -> response);

    return transactionId;
  }

  @Override
  public TransactionStatusResponse getTransactionStatus(UUID transactionId, String tenantId) {
    log.info("getTransactionStatus:: transactionId={}, tenantId={}", transactionId, tenantId);

    return executionService.executeSystemUserScoped(tenantId,
      () -> dcbTransactionClient.getDcbTransactionStatus(transactionId.toString()));
  }

  @Override
  public void createTransactions(EcsTlrEntity ecsTlr, Request secondaryRequest) {
    log.info("createTransactions:: creating transactions for ECS TLR {}", ecsTlr::getId);
    if (secondaryRequest.getItemId() == null) {
      log.info("createDcbTransactions:: secondary request has no item ID");
      return;
    }
    createLendingTransaction(ecsTlr);
    log.info("createTransactions:: intermediate request ID: {}", ecsTlr::getIntermediateRequestId);
    if (ecsTlr.getIntermediateRequestId() == null) {
      createBorrowingPickupTransaction(ecsTlr, secondaryRequest);
    } else {
      createBorrowerTransaction(ecsTlr, secondaryRequest);
      createPickupTransaction(ecsTlr, secondaryRequest);
    }
  }

  @Override
  public void updateTransactionStatuses(TransactionStatus.StatusEnum newStatus, EcsTlrEntity ecsTlr) {
    log.info("updateTransactionStatuses:: updating primary transaction status to {}", newStatus::getValue);
    updateTransactionStatus(ecsTlr.getPrimaryRequestDcbTransactionId(), newStatus,
      ecsTlr.getPrimaryRequestTenantId());

    log.info("updateTransactionStatuses:: updating intermediate transaction status to {}", newStatus::getValue);
    updateTransactionStatus(ecsTlr.getIntermediateRequestDcbTransactionId(), newStatus,
      ecsTlr.getIntermediateRequestTenantId());

    log.info("updateTransactionStatuses:: updating secondary transaction status to {}", newStatus::getValue);
    updateTransactionStatus(ecsTlr.getSecondaryRequestDcbTransactionId(), newStatus,
      ecsTlr.getSecondaryRequestTenantId());
  }

  @Override
  public void updateTransactionStatus(UUID transactionId, StatusEnum newStatus, String tenantId) {
    if (transactionId == null) {
      log.info("updateTransactionStatus:: transaction ID is null, doing nothing");
      return;
    }
    if (tenantId == null) {
      log.info("updateTransactionStatus:: tenant ID is null, doing nothing");
      return;
    }

    try {
      if (isTransactionStatusChangeAllowed(transactionId, newStatus, tenantId)) {
        log.info("updateTransactionStatus: changing status of transaction {} in tenant {} to {}",
          transactionId, tenantId, newStatus.getValue());

        executionService.executeSystemUserScoped(tenantId,
          () -> dcbTransactionClient.changeDcbTransactionStatus(transactionId.toString(),
            new TransactionStatus().status(newStatus)));
      }
    } catch (FeignException.NotFound e) {
      log.error("updateTransactionStatus:: transaction {} not found: {}", transactionId, e.getMessage());
    } catch (Exception e) {
      log.error("updateTransactionStatus:: failed to update transaction status: {}", e::getMessage);
      log.debug("updateTransactionStatus:: ", e);
    }
  }

  private boolean isTransactionStatusChangeAllowed(UUID transactionId, StatusEnum newStatus,
    String tenantId) {

    TransactionStatusResponse transaction = getTransactionStatus(transactionId, tenantId);
    RoleEnum transactionRole = RoleEnum.fromValue(transaction.getRole().getValue());
    StatusEnum currentStatus = StatusEnum.fromValue(transaction.getStatus().getValue());

    return isTransactionStatusChangeAllowed(transactionRole, currentStatus, newStatus);
  }

  private static boolean isTransactionStatusChangeAllowed(RoleEnum role, StatusEnum oldStatus,
    StatusEnum newStatus) {

    log.info("isTransactionStatusChangeAllowed:: role={}, oldStatus={}, newStatus={}", role,
      oldStatus, newStatus);

    boolean isStatusChangeAllowed = false;

    if (role == LENDER) {
      isStatusChangeAllowed = (oldStatus == CREATED && newStatus == OPEN) ||
        (oldStatus == OPEN && newStatus == AWAITING_PICKUP) ||
        (oldStatus == AWAITING_PICKUP && newStatus == ITEM_CHECKED_OUT) ||
        (oldStatus == ITEM_CHECKED_OUT && newStatus == ITEM_CHECKED_IN) ||
        (oldStatus != CANCELLED && newStatus == CANCELLED);
    }
    else if (role == BORROWER) {
      isStatusChangeAllowed = (oldStatus == CREATED && newStatus == OPEN) ||
        (oldStatus == OPEN && newStatus == AWAITING_PICKUP) ||
        (oldStatus == AWAITING_PICKUP && newStatus == ITEM_CHECKED_OUT) ||
        (oldStatus == ITEM_CHECKED_OUT && newStatus == ITEM_CHECKED_IN) ||
        (oldStatus == ITEM_CHECKED_IN && newStatus == CLOSED) ||
        (oldStatus != CANCELLED && newStatus == CANCELLED);
    }
    else if (role == BORROWING_PICKUP || role == PICKUP) {
      isStatusChangeAllowed = (oldStatus == CREATED && newStatus == OPEN) ||
        (oldStatus == ITEM_CHECKED_IN && newStatus == CLOSED) ||
        (oldStatus != CANCELLED && newStatus == CANCELLED);
    }
    log.info("isTransactionStatusChangeAllowed:: status change is allowed: {}", isStatusChangeAllowed);
    return isStatusChangeAllowed;
  }

}
