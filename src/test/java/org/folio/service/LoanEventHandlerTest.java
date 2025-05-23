package org.folio.service;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.folio.support.kafka.EventType.UPDATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.folio.client.feign.LoanStorageClient;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Loans;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.LoanEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.CqlQuery;
import org.folio.support.kafka.DefaultKafkaEvent;
import org.folio.support.kafka.EventType;
import org.folio.support.kafka.KafkaEvent;
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

  private static final EnumSet<EventType> SUPPORTED_EVENT_TYPES = EnumSet.of(UPDATE);
  private static final String TENANT_ID_CONSORTIUM = "consortium";
  private static final String TENANT_ID_COLLEGE = "college";

  @Mock
  private DcbService dcbService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @Mock
  private LoanStorageClient loanStorageClient;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock
  private ConsortiaService consortiaService;
  @InjectMocks
  private LoanEventHandler loanEventHandler;

  @ParameterizedTest
  @EnumSource(EventType.class)
  void eventsOfUnsupportedTypesAreIgnored(EventType eventType) {
    if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
      loanEventHandler.handle(new DefaultKafkaEvent<Loan>(null, null, null, 0L, null)
        .withGenericType(eventType));
      verifyNoInteractions(ecsTlrRepository, dcbService);
    }
  }

  @Test
  void updateEventForLoanWithUnsupportedActionIsIgnored() {
    Loan loan = new Loan().action("random_action");
    KafkaEvent<Loan> event = createEvent(loan);
    loanEventHandler.handle(event);
    verifyNoInteractions(ecsTlrRepository, dcbService);
  }

  @Test
  void updateEventForLoanWithEqualRenewalCountIsIgnored() {
    Loan newloan = new Loan().action("checkedout").renewalCount(1);
    KafkaEvent<Loan> event = createEvent(newloan);
    loanEventHandler.handle(event);
    verifyNoInteractions(loanStorageClient);
  }

  @Test
  void updateEventForNotFoundLoansIsIgnored() {
    KafkaEvent<Loan> event = buildLoanRenewalEvent();
    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id(TENANT_ID_COLLEGE), new Tenant().id(TENANT_ID_CONSORTIUM)));
    when(loanStorageClient.getByQuery(any(CqlQuery.class), eq(1)))
      .thenReturn(new Loans(Collections.emptyList(), 0));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(executionService).executeAsyncSystemUserScoped(anyString(), any(Runnable.class));

    loanEventHandler.handle(event);

    var expectedQuery = CqlQuery.exactMatch("itemId", event.getNewVersion().getItemId())
      .and(CqlQuery.exactMatch("status.name", "Open"));
    verify(loanStorageClient, times(1)).getByQuery(expectedQuery, 1);
    verify(loanStorageClient, times(0)).updateLoan(event.getOldVersion().getId(),
      event.getNewVersion());
  }

  @Test
  void updateRenewalEventIsHandled() {
    KafkaEvent<Loan> event = buildLoanRenewalEvent();

    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id(TENANT_ID_COLLEGE), new Tenant().id(TENANT_ID_CONSORTIUM)));
    when(loanStorageClient.getByQuery(any(CqlQuery.class), eq(1)))
      .thenReturn(new Loans(List.of(event.getNewVersion()), 1));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(executionService).executeAsyncSystemUserScoped(anyString(), any(Runnable.class));

    loanEventHandler.handle(event);

    var expectedQuery = CqlQuery.exactMatch("itemId", event.getNewVersion().getItemId())
      .and(CqlQuery.exactMatch("status.name", "Open"));
    verify(loanStorageClient, times(1)).getByQuery(expectedQuery, 1);
    verify(loanStorageClient, times(1)).updateLoan(event.getOldVersion().getId(), event.getNewVersion());
    verify(executionService, times(1)).executeAsyncSystemUserScoped(eq(TENANT_ID_COLLEGE),
      any(Runnable.class));
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

    when(ecsTlrRepository.findByItemId(itemId))
      .thenReturn(emptyList());

    KafkaEvent<Loan> event = createEvent(loan);
    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemId(itemId);
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

    when(ecsTlrRepository.findByItemId(itemId))
      .thenReturn(List.of(new EcsTlrEntity()));

    KafkaEvent<Loan> event = createEvent(loan);
    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemId(itemId);
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

    when(ecsTlrRepository.findByItemId(itemId))
      .thenReturn(List.of(ecsTlr));

    KafkaEvent<Loan> event = createEvent(loan);
    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemId(itemId);
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

    when(ecsTlrRepository.findByItemId(itemId))
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

    DefaultKafkaEvent.DefaultKafkaEventData<Loan> eventData =
      new DefaultKafkaEvent.DefaultKafkaEventData<>(loan, loan);
    KafkaEvent<Loan> event = new DefaultKafkaEvent<>(randomUUID().toString(), eventTenant,
      DefaultKafkaEvent.DefaultKafkaEventType.UPDATED, 0L, eventData)
      .withTenantIdHeaderValue(eventTenant)
      .withUserIdHeaderValue("random_user_id");

    loanEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemId(itemId);
    verify(dcbService).getTransactionStatus(primaryTransactionId, primaryRequestTenant);
    verify(dcbService).getTransactionStatus(secondaryTransactionId, secondaryRequestTenant);
    verify(dcbService).updateTransactionStatuses(expectedNewStatus, mockEcsTlr);
  }

  private static TransactionStatusResponse buildTransactionStatusResponse(String role, String status) {
    return new TransactionStatusResponse()
      .role(TransactionStatusResponse.RoleEnum.fromValue(role))
      .status(TransactionStatusResponse.StatusEnum.fromValue(status));
  }

  private KafkaEvent<Loan> buildLoanRenewalEvent() {
    Date newDueDate = Date.from(ZonedDateTime.now().plusDays(1).toInstant());
    Date oldDueDate = Date.from(ZonedDateTime.now().plusHours(1).toInstant());
    String itemId = randomUUID().toString();
    Loan newloan = new Loan()
      .renewalCount(1)
      .dueDate(newDueDate)
      .itemId(itemId);
    Loan oldloan = new Loan()
      .dueDate(oldDueDate)
      .itemId(itemId);
    return createEvent(newloan, oldloan);
  }

  private static KafkaEvent<Loan> createEvent(Loan loan) {
    return createEvent(loan, loan);
  }

  private static KafkaEvent<Loan> createEvent(Loan newLoan, Loan oldLoan) {
    return new DefaultKafkaEvent<>(randomUUID().toString(), TENANT_ID_CONSORTIUM,
      DefaultKafkaEvent.DefaultKafkaEventType.UPDATED, 0L,
      new DefaultKafkaEvent.DefaultKafkaEventData<>(oldLoan, newLoan))
        .withTenantIdHeaderValue(TENANT_ID_CONSORTIUM)
        .withUserIdHeaderValue("test_user");
  }
}
