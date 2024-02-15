package org.folio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.client.feign.DcbClient;
import org.folio.domain.dto.DcbTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DcbClientTest {
  @Mock
  private DcbClient dcbClient;

  @Test
  void canCreateDcbTransaction() {
    String requestId = UUID.randomUUID().toString();
    DcbTransaction dcbTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .requestId(requestId);
    when(dcbClient.createDcbTransaction(dcbTransaction)).thenReturn(dcbTransaction);
    var response = dcbClient.createDcbTransaction(dcbTransaction);
    assertNotNull(response);
    assertEquals(response.getRole(), DcbTransaction.RoleEnum.BORROWER);
    assertEquals(response.getRequestId(), requestId);
  }
}
