package org.folio.service.impl;

import static org.folio.domain.dto.DcbTransaction.RoleEnum.BORROWER;
import static org.folio.domain.dto.DcbTransaction.RoleEnum.LENDER;

import java.util.UUID;

import org.folio.client.feign.DcbClient;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.DcbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class DcbServiceImpl implements DcbService {

  private final DcbClient dcbClient;

  public DcbServiceImpl(@Autowired DcbClient dcbClient) {
    this.dcbClient = dcbClient;
  }

  public void createTransactions(EcsTlrEntity ecsTlr) {
    log.info("createTransaction:: creating DCB transactions for ECS TLR {}", ecsTlr.getId());
    final UUID borrowerTransactionId = createTransaction(ecsTlr.getPrimaryRequestId(), BORROWER);
    final UUID lenderTransactionId = createTransaction(ecsTlr.getSecondaryRequestId(), LENDER);
    ecsTlr.setPrimaryRequestDcbTransactionId(borrowerTransactionId);
    ecsTlr.setSecondaryRequestDcbTransactionId(lenderTransactionId);
    log.info("createTransaction:: DCB transactions for ECS TLR {} created", ecsTlr.getId());
  }

  private UUID createTransaction(UUID requestId, DcbTransaction.RoleEnum role) {
    final UUID transactionId = UUID.randomUUID();
    log.info("createTransaction:: creating {} transaction {} for request {}", role, transactionId, requestId);
    final DcbTransaction transaction = new DcbTransaction()
      .requestId(requestId.toString())
      .role(role);
    var response = dcbClient.createDcbTransaction(transactionId.toString(), transaction);
    log.info("createTransaction:: {} transaction {} created", role, transactionId);
    log.debug("createTransaction:: {}", () -> response);

    return transactionId;
  }

}
