package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.folio.client.feign.DcbTransactionClient;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.service.impl.DcbServiceImpl;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DcbServiceTest {

  @Mock
  private DcbTransactionClient dcbTransactionClient;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @InjectMocks
  private DcbServiceImpl dcbService;

  @BeforeEach
  public void setup() {
    // Bypass the use of system user and return the result of Callable immediately
    when(executionService.executeSystemUserScoped(any(String.class), any(Callable.class)))
      .thenAnswer(invocation -> invocation.getArgument(1, Callable.class).call());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "PICKUP, CREATED, OPEN, true",
    "PICKUP, OPEN, AWAITING_PICKUP, false",
    "PICKUP, AWAITING_PICKUP, ITEM_CHECKED_OUT, false",
    "PICKUP, ITEM_CHECKED_OUT, ITEM_CHECKED_IN, false",
    "PICKUP, ITEM_CHECKED_IN, CLOSED, true",
    "PICKUP, OPEN, CANCELLED, true",

    "BORROWING-PICKUP, CREATED, OPEN, true",
    "BORROWING-PICKUP, OPEN, AWAITING_PICKUP, false",
    "BORROWING-PICKUP, AWAITING_PICKUP, ITEM_CHECKED_OUT, false",
    "BORROWING-PICKUP, ITEM_CHECKED_OUT, ITEM_CHECKED_IN, false",
    "BORROWING-PICKUP, ITEM_CHECKED_IN, CLOSED, true",
    "BORROWING-PICKUP, OPEN, CANCELLED, true",

    "BORROWER, CREATED, OPEN, true",
    "BORROWER, OPEN, AWAITING_PICKUP, true",
    "BORROWER, AWAITING_PICKUP, ITEM_CHECKED_OUT, true",
    "BORROWER, ITEM_CHECKED_OUT, ITEM_CHECKED_IN, true",
    "BORROWER, ITEM_CHECKED_IN, CLOSED, true",
    "BORROWER, OPEN, CANCELLED, true",

    "LENDER, CREATED, OPEN, true",
    "LENDER, OPEN, AWAITING_PICKUP, true",
    "LENDER, AWAITING_PICKUP, ITEM_CHECKED_OUT, true",
    "LENDER, ITEM_CHECKED_OUT, ITEM_CHECKED_IN, true",
    "LENDER, ITEM_CHECKED_IN, CLOSED, false",
    "LENDER, OPEN, CANCELLED, true",
    "LENDER, EXPIRED, EXPIRED, false",
  })
  void updateTransactionStatusesUpdatesAllTransactions(String role, String oldStatus,
    String newStatus, boolean transactionUpdateIsExpected) {

    String transactionId = randomUUID().toString();
    TransactionStatus newTransactionStatus = new TransactionStatus().status(
      TransactionStatus.StatusEnum.fromValue(newStatus));

    TransactionStatusResponse mockGetStatusResponse = buildTransactionStatusResponse(role, oldStatus);
    TransactionStatusResponse mockUpdateStatusResponse = buildTransactionStatusResponse(role, newStatus);

    when(dcbTransactionClient.getDcbTransactionStatus(transactionId))
      .thenReturn(mockGetStatusResponse);

    if (transactionUpdateIsExpected) {
      when(dcbTransactionClient.changeDcbTransactionStatus(transactionId, newTransactionStatus))
        .thenReturn(mockUpdateStatusResponse);
    }

    dcbService.updateTransactionStatus(UUID.fromString(transactionId),
      newTransactionStatus.getStatus(), "test_tenant");

    verify(dcbTransactionClient, times(transactionUpdateIsExpected ? 1 : 0))
      .changeDcbTransactionStatus(transactionId, newTransactionStatus);
  }

  private static TransactionStatusResponse buildTransactionStatusResponse(String role, String status) {
    return new TransactionStatusResponse()
      .role(TransactionStatusResponse.RoleEnum.fromValue(role))
      .status(TransactionStatusResponse.StatusEnum.fromValue(status));
  }

}
