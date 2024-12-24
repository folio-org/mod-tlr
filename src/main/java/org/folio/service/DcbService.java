package org.folio.service;

import java.util.UUID;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;

public interface DcbService {
  void createTransactions(EcsTlrEntity ecsTlr, Request secondaryRequest);
  void createLendingTransaction(EcsTlrEntity ecsTlr);
  void createBorrowerTransaction(EcsTlrEntity ecsTlr, Request request);
  void createBorrowingPickupTransaction(EcsTlrEntity ecsTlr, Request request);
  void createPickupTransaction(EcsTlrEntity ecsTlr, Request request);
  void updateTransactionStatuses(TransactionStatus.StatusEnum newStatus, EcsTlrEntity ecsTlr);
  TransactionStatusResponse getTransactionStatus(UUID transactionId, String tenantId);
  void updateTransactionStatus(UUID transactionId, TransactionStatus.StatusEnum newStatus,
    String tenantId);
}
