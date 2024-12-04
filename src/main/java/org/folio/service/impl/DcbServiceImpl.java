package org.folio.service.impl;

import static org.folio.domain.dto.DcbTransaction.RoleEnum.BORROWER;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.BORROWING_PICKUP;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.LENDER;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.PICKUP;

import java.util.UUID;

import org.folio.client.feign.DcbEcsTransactionClient;
import org.folio.client.feign.DcbTransactionClient;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.DcbTransaction.RoleEnum;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.DcbService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    DcbItem dcbItem = new DcbItem()
      .id(request.getItemId())
      .title(request.getInstance().getTitle())
      .barcode(request.getItem().getBarcode());
    DcbTransaction transaction = new DcbTransaction()
      .requestId(ecsTlr.getIntermediateRequestId().toString())
      .item(dcbItem)
      .role(BORROWER);
    final UUID transactionId = createTransaction(transaction, ecsTlr.getIntermediateRequestTenantId());
    ecsTlr.setIntermediateRequestDcbTransactionId(transactionId);
    log.info("createBorrowerTransaction:: borrower transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  @Override
  public void createBorrowingPickupTransaction(EcsTlrEntity ecsTlr, Request request) {
    log.info("createBorrowingPickupTransaction:: creating borrowing-pickup transaction for ECS TLR {}",
      ecsTlr::getId);
    final UUID transactionId = createPickupTransaction(ecsTlr, request, BORROWING_PICKUP);
    log.info("createBorrowingPickupTransaction:: borrowing-pickup transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  @Override
  public void createPickupTransaction(EcsTlrEntity ecsTlr, Request request) {
    log.info("createPickupTransaction:: creating pickup transaction for ECS TLR {}", ecsTlr.getId());
    final UUID transactionId = createPickupTransaction(ecsTlr, request, PICKUP);
    log.info("createPickupTransaction:: pickup transaction {} for ECS TLR {} created",
      () -> transactionId, ecsTlr::getId);
  }

  private UUID createPickupTransaction(EcsTlrEntity ecsTlr, Request request, RoleEnum role) {
    DcbItem dcbItem = new DcbItem()
      .id(request.getItemId())
      .title(request.getInstance().getTitle())
      .barcode(request.getItem().getBarcode());
    DcbTransaction transaction = new DcbTransaction()
      .requestId(ecsTlr.getPrimaryRequestId().toString())
      .item(dcbItem)
      .role(role);
    final UUID transactionId = createTransaction(transaction, ecsTlr.getPrimaryRequestTenantId());
    ecsTlr.setPrimaryRequestDcbTransactionId(transactionId);

    return transactionId;
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
  public TransactionStatusResponse updateTransactionStatus(UUID transactionId,
    TransactionStatus.StatusEnum newStatus, String tenantId) {

    log.info("updateTransactionStatus:: transactionId={}, newStatus={}, tenantId={}",
      transactionId, newStatus, tenantId);

    return executionService.executeSystemUserScoped(tenantId,
      () -> dcbTransactionClient.changeDcbTransactionStatus(
        transactionId.toString(), new TransactionStatus().status(newStatus)));
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

}
