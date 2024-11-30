package org.folio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.client.feign.DcbEcsTransactionClient;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.TransactionStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DcbEcsTransactionClientTest {
  @Mock
  private DcbEcsTransactionClient dcbEcsTransactionClient;

  @Test
  void canCreateDcbTransaction() {
    String requestId = UUID.randomUUID().toString();
    String dcbTransactionId = UUID.randomUUID().toString();
    DcbTransaction dcbTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .requestId(requestId);
    TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CANCELLED)
      .message("test message")
      .item(dcbTransaction.getItem())
      .role(TransactionStatusResponse.RoleEnum.BORROWER)
      .requestId(requestId);
    when(dcbEcsTransactionClient.createTransaction(dcbTransactionId, dcbTransaction))
      .thenReturn(transactionStatusResponse);
    var response = dcbEcsTransactionClient.createTransaction(dcbTransactionId,
      dcbTransaction);
    assertNotNull(response);
    assertEquals(TransactionStatusResponse.RoleEnum.BORROWER, response.getRole());
    assertEquals(requestId, response.getRequestId());
  }

}
