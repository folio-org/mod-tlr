package org.folio.service;

import java.util.UUID;

import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;

public interface DcbService {
  void createTransactions(EcsTlrEntity ecsTlr);
  TransactionStatusResponse getTransactionStatus(UUID transactionId, String tenantId);
  TransactionStatusResponse updateTransactionStatus(UUID transactionId,
    TransactionStatus.StatusEnum newStatus, String tenantId);
}
