package org.folio.service.impl;

import static org.folio.domain.dto.DcbTransaction.RoleEnum.BORROWER;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.LENDER;

import java.util.UUID;

import org.folio.client.feign.DcbEcsTransactionClient;
import org.folio.client.feign.DcbTransactionClient;
import org.folio.domain.dto.DcbTransaction;
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
  public void createTransactions(EcsTlrEntity ecsTlr) {
    log.info("createTransactions:: creating DCB transactions for ECS TLR {}", ecsTlr::getId);
    final UUID borrowerTransactionId = createTransaction(ecsTlr.getPrimaryRequestId(), BORROWER,
      ecsTlr.getPrimaryRequestTenantId());
    final UUID lenderTransactionId = createTransaction(ecsTlr.getSecondaryRequestId(), LENDER,
      ecsTlr.getSecondaryRequestTenantId());
    ecsTlr.setPrimaryRequestDcbTransactionId(borrowerTransactionId);
    ecsTlr.setSecondaryRequestDcbTransactionId(lenderTransactionId);
    log.info("createTransactions:: DCB transactions for ECS TLR {} created", ecsTlr::getId);
  }

  private UUID createTransaction(UUID requestId, DcbTransaction.RoleEnum role, String tenantId) {
    final UUID transactionId = UUID.randomUUID();
    log.info("createTransaction:: creating {} transaction {} for request {} in tenant {}", role,
      transactionId, requestId, tenantId);
    final DcbTransaction transaction = new DcbTransaction()
      .requestId(requestId.toString())
      .role(role);
    var response = executionService.executeSystemUserScoped(tenantId,
      () -> dcbEcsTransactionClient.createTransaction(transactionId.toString(), transaction));
    log.info("createTransaction:: {} transaction {} created", role, transactionId);
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

}
