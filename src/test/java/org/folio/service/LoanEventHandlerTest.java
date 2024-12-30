package org.folio.service;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.Loan;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.LoanEventHandler;
import org.folio.support.KafkaEvent;
import org.folio.support.KafkaEvent.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanEventHandlerTest {

  private static final EnumSet<EventType> SUPPORTED_EVENT_TYPES = EnumSet.of(UPDATED);

  @Mock
  private DcbService dcbService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @InjectMocks
  private LoanEventHandler loanEventHandler;

  @ParameterizedTest
  @EnumSource(EventType.class)
  void eventsOfUnsupportedTypesAreIgnored(EventType eventType) {
    if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
      loanEventHandler.handle(new KafkaEvent<>(null, null, eventType, 0L, null, null));
      verifyNoInteractions(ecsTlrRepository, dcbService);
    }
  }

  @Test
  void updateEventForLoanWithUnsupportedActionInIgnored() {
    Loan loan = new Loan().action("random_action");
    KafkaEvent<Loan> event = new KafkaEvent<>(randomUUID().toString(), "test_tenant", UPDATED,
      0L, new KafkaEvent.EventData<>(loan, loan), "test_tenant");
    loanEventHandler.handle(event);
    verifyNoInteractions(ecsTlrRepository, dcbService);
  }

  @Test
  void checkInEventIsIgnoredWhenEcsTlrForUpdatedLoanIsNotFound() {
    UUID itemId = randomUUID();
    UUID userId = randomUUID();
    Loan loan = new Loan()
      .id(randomUUID().toString())
      .action("checkedin")
      .itemId(itemId.toString())
      .userId(userId.toString());

    when(ecsTlrRepository.findByItemIdAndRequesterId(itemId, userId))
      .thenReturn(emptyList());

    KafkaEvent<Loan> event = new KafkaEvent<>(randomUUID().toString(), "test_tenant", UPDATED,
      0L, new KafkaEvent.EventData<>(loan, loan), "test_tenant");
    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemIdAndRequesterId(itemId, userId);
    verifyNoInteractions(dcbService);
  }

  @Test
  void checkInEventIsIgnoredWhenEcsTlrDoesNotContainsNoTransactionIds() {
    UUID itemId = randomUUID();
    UUID userId = randomUUID();
    Loan loan = new Loan()
      .id(randomUUID().toString())
      .action("checkedin")
      .itemId(itemId.toString())
      .userId(userId.toString());

    when(ecsTlrRepository.findByItemIdAndRequesterId(itemId, userId))
      .thenReturn(List.of(new EcsTlrEntity()));

    KafkaEvent<Loan> event = new KafkaEvent<>(randomUUID().toString(), "test_tenant", UPDATED,
      0L, new KafkaEvent.EventData<>(loan, loan), "test_tenant");
    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemIdAndRequesterId(itemId, userId);
    verifyNoInteractions(dcbService);
  }

  @Test
  void checkInEventIsIgnoredWhenEventTenantDoesNotMatchEcsRequestTransactionTenants() {
    UUID itemId = randomUUID();
    UUID userId = randomUUID();
    Loan loan = new Loan()
      .id(randomUUID().toString())
      .action("checkedin")
      .itemId(itemId.toString())
      .userId(userId.toString());

    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setPrimaryRequestTenantId("borrowing_tenant");
    ecsTlr.setSecondaryRequestTenantId("lending_tenant");
    ecsTlr.setPrimaryRequestDcbTransactionId(randomUUID());
    ecsTlr.setSecondaryRequestDcbTransactionId(randomUUID());

    when(ecsTlrRepository.findByItemIdAndRequesterId(itemId, userId))
      .thenReturn(List.of(ecsTlr));

    KafkaEvent<Loan> event = new KafkaEvent<>(randomUUID().toString(), "test_tenant", UPDATED,
      0L, new KafkaEvent.EventData<>(loan, loan), "test_tenant");
    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemIdAndRequesterId(itemId, userId);
    verifyNoInteractions(dcbService);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "BORROWING-PICKUP, ITEM_CHECKED_OUT, LENDER, ITEM_CHECKED_OUT, borrowing_tenant, ITEM_CHECKED_IN",
    "BORROWING-PICKUP, ITEM_CHECKED_IN, LENDER, ITEM_CHECKED_OUT, borrowing_tenant, ITEM_CHECKED_IN",
    "PICKUP, ITEM_CHECKED_OUT, LENDER, ITEM_CHECKED_OUT, borrowing_tenant, ITEM_CHECKED_IN",
    "PICKUP, ITEM_CHECKED_IN, LENDER, ITEM_CHECKED_OUT, borrowing_tenant, ITEM_CHECKED_IN",

    "BORROWING-PICKUP, ITEM_CHECKED_IN, LENDER, ITEM_CHECKED_IN, lending_tenant, CLOSED",
    "BORROWING-PICKUP, ITEM_CHECKED_IN, LENDER, CLOSED, lending_tenant, CLOSED",
    "PICKUP, ITEM_CHECKED_IN, LENDER, ITEM_CHECKED_IN, lending_tenant, CLOSED",
    "PICKUP, ITEM_CHECKED_IN, LENDER, CLOSED, lending_tenant, CLOSED"
  })
  void checkInEventIsHandled(String primaryTransactionRole, String primaryTransactionStatus,
    String secondaryTransactionRole, String secondaryTransactionStatus, String eventTenant,
    String expectedNewTransactionStatus) {

    String primaryRequestTenant = "borrowing_tenant";
    String secondaryRequestTenant = "lending_tenant";
    UUID primaryTransactionId = randomUUID();
    UUID secondaryTransactionId = randomUUID();
    UUID itemId = randomUUID();
    UUID userId = randomUUID();
    Loan loan = new Loan()
      .action("checkedin")
      .itemId(itemId.toString())
      .userId(userId.toString());

    EcsTlrEntity mockEcsTlr = new EcsTlrEntity();
    mockEcsTlr.setId(randomUUID());
    mockEcsTlr.setPrimaryRequestTenantId(primaryRequestTenant);
    mockEcsTlr.setSecondaryRequestTenantId(secondaryRequestTenant);
    mockEcsTlr.setPrimaryRequestDcbTransactionId(primaryTransactionId);
    mockEcsTlr.setSecondaryRequestDcbTransactionId(secondaryTransactionId);

    when(ecsTlrRepository.findByItemIdAndRequesterId(itemId, userId))
      .thenReturn(List.of(mockEcsTlr));

    TransactionStatusResponse mockPrimaryTransactionResponse = buildTransactionStatusResponse(
      primaryTransactionRole, primaryTransactionStatus);
    TransactionStatusResponse mockSecondaryTransactionResponse = buildTransactionStatusResponse(
      secondaryTransactionRole, secondaryTransactionStatus);

    when(dcbService.getTransactionStatus(primaryTransactionId, primaryRequestTenant))
      .thenReturn(mockPrimaryTransactionResponse);
    when(dcbService.getTransactionStatus(secondaryTransactionId, secondaryRequestTenant))
      .thenReturn(mockSecondaryTransactionResponse);

    TransactionStatus.StatusEnum expectedNewStatus = TransactionStatus.StatusEnum.fromValue(
      expectedNewTransactionStatus);
    doNothing().when(dcbService).updateTransactionStatuses(expectedNewStatus, mockEcsTlr);

    KafkaEvent.EventData<Loan> eventData = new KafkaEvent.EventData<>(loan, loan);
    KafkaEvent<Loan> event = new KafkaEvent<>(randomUUID().toString(), eventTenant,
      UPDATED, 0L, eventData, eventTenant);

    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemIdAndRequesterId(itemId, userId);
    verify(dcbService).getTransactionStatus(primaryTransactionId, primaryRequestTenant);
    verify(dcbService).getTransactionStatus(secondaryTransactionId, secondaryRequestTenant);
    verify(dcbService).updateTransactionStatuses(expectedNewStatus, mockEcsTlr);
  }

  private static TransactionStatusResponse buildTransactionStatusResponse(String role, String status) {
    return new TransactionStatusResponse()
      .role(TransactionStatusResponse.RoleEnum.fromValue(role))
      .status(TransactionStatusResponse.StatusEnum.fromValue(status));
  }
}
