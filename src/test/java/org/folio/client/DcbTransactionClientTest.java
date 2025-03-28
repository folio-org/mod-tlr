package org.folio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.client.feign.DcbTransactionClient;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DcbTransactionClientTest {
  @Mock
  private DcbTransactionClient dcbClient;

  @Test
  void canGetDcbTransactionStatus() {
    String requestId = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    DcbTransaction dcbTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .requestId(requestId);
    TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CANCELLED)
      .message("test message")
      .item(dcbTransaction.getItem())
      .requestId(requestId);
    when(dcbClient.getDcbTransactionStatus(transactionId)).thenReturn(transactionStatusResponse);
    var response = dcbClient.getDcbTransactionStatus(transactionId);
    assertNotNull(response);
    assertEquals(TransactionStatusResponse.StatusEnum.CANCELLED, response.getStatus());
  }

  @Test
  void canChangeDcbTransactionStatus() {
    String requestId = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    TransactionStatus targetStatus = new TransactionStatus()
      .status(TransactionStatus.StatusEnum.CANCELLED)
      .message("test message");
    DcbTransaction dcbTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .requestId(requestId);
    TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CANCELLED)
      .message("test message")
      .item(dcbTransaction.getItem())
      .requestId(requestId);
    when(dcbClient.changeDcbTransactionStatus(transactionId, targetStatus))
      .thenReturn(transactionStatusResponse);
    var response = dcbClient.changeDcbTransactionStatus(transactionId, targetStatus);
    assertNotNull(response);
    assertEquals(TransactionStatusResponse.StatusEnum.CANCELLED, response.getStatus());
  }
}
