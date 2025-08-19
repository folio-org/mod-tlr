package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.folio.util.TestUtils.mockSystemUserService;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.CirculationDeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.LoanStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.domain.mapper.CirculationMapperImpl;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.DeclareClaimedReturnedItemAsMissingServiceImpl;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DeclareClaimedReturnedItemAsMissingServiceTest {

  private static final UUID LOCAL_TENANT_LOAN_ID = randomUUID();
  private static final UUID LENDING_TENANT_LOAN_ID = randomUUID();
  private static final UUID USER_ID = randomUUID();
  private static final UUID ITEM_ID = randomUUID();
  private static final UUID ECS_REQUEST_ID = randomUUID();
  private static final String COMMENT = "Test comment";

  @Spy
  private CirculationMapper circulationMapper = new CirculationMapperImpl();
  @Mock
  private CirculationErrorForwardingClient circulationClient;
  @Mock
  private LoanService loanService;
  @Mock
  private RequestService requestService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @Mock
  private SystemUserScopedExecutionService systemUserService;
  @InjectMocks
  private DeclareClaimedReturnedItemAsMissingServiceImpl service;

  @Test
  void declareItemMissingByLoanIdInLocalAndLendingTenant() {
    mockSystemUserService(systemUserService);
    when(circulationClient.declareClaimedReturnedItemAsMissing(anyString(),
      eq(buildCirculationDeclareItemMissingRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LENDING_TENANT_LOAN_ID)));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));

    service.declareMissing(buildDeclareItemMissingByLoanIdRequest());

    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LENDING_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verify(loanService, times(1)).findOpenLoan(USER_ID.toString(), ITEM_ID.toString());
  }

  @Test
  void declareItemMissingByUserAndItemIdInLocalAndLendingTenant() {
    mockSystemUserService(systemUserService);
    when(circulationClient.declareClaimedReturnedItemAsMissing(anyString(),
      eq(buildCirculationDeclareItemMissingRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LOCAL_TENANT_LOAN_ID))) // loan in local tenant
      .thenReturn(Optional.of(buildLoan(LENDING_TENANT_LOAN_ID))); // loan in lending tenant
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));

    service.declareMissing(buildDeclareItemMissingByItemAndUserIdRequest());

    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LENDING_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verify(loanService, times(2)).findOpenLoan(USER_ID.toString(), ITEM_ID.toString());
    verify(loanService, times(0)).fetchLoan(anyString());
  }

  @Test
  void itemIsDeclaredMissingOnlyInLocalTenantWhenNoEcsRequestIsFound() {
    when(circulationClient.declareClaimedReturnedItemAsMissing(anyString(),
      eq(buildCirculationDeclareItemMissingRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.empty());

    service.declareMissing(buildDeclareItemMissingByLoanIdRequest());

    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verifyNoMoreInteractions(circulationClient);
  }

  @Test
  void itemIsDeclaredMissingOnlyInLocalTenantWhenNoEcsTlrIsFound() {
    when(circulationClient.declareClaimedReturnedItemAsMissing(anyString(),
      eq(buildCirculationDeclareItemMissingRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.empty());

    service.declareMissing(buildDeclareItemMissingByLoanIdRequest());

    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verifyNoMoreInteractions(circulationClient);
  }

  @Test
  void declareItemMissingFailsWhenLoanIsNotFoundInLendingTenant() {
    mockSystemUserService(systemUserService);
    when(circulationClient.declareClaimedReturnedItemAsMissing(anyString(),
      eq(buildCirculationDeclareItemMissingRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.empty());

    DeclareClaimedReturnedItemAsMissingRequest request = buildDeclareItemMissingByLoanIdRequest();
    assertThrows(NoSuchElementException.class,
      () -> service.declareMissing(request));

    verify(circulationClient, times(1)).declareClaimedReturnedItemAsMissing(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemMissingRequest());
    verifyNoMoreInteractions(circulationClient);
  }

  @ParameterizedTest
  @MethodSource("invalidDeclareItemMissingRequests")
  void declareItemMissingFailsWhenRequestHasInvalidCombinationOfParameters(
    DeclareClaimedReturnedItemAsMissingRequest request) {

    assertThrows(IllegalArgumentException.class,
      () -> service.declareMissing(request));
  }

  private static Stream<Arguments> invalidDeclareItemMissingRequests() {
    return Stream.of(
      Arguments.of(new DeclareClaimedReturnedItemAsMissingRequest()
        .loanId(randomUUID())
        .itemId(randomUUID())
        .userId(randomUUID())),
      Arguments.of(new DeclareClaimedReturnedItemAsMissingRequest()
        .loanId(null)
        .itemId(null)
        .userId(null)),
      Arguments.of(new DeclareClaimedReturnedItemAsMissingRequest()
        .loanId(randomUUID())
        .itemId(randomUUID())
        .userId(null)),
      Arguments.of(new DeclareClaimedReturnedItemAsMissingRequest()
        .loanId(randomUUID())
        .itemId(null)
        .userId(randomUUID())),
      Arguments.of(new DeclareClaimedReturnedItemAsMissingRequest()
        .loanId(null)
        .itemId(randomUUID())
        .userId(null)),
      Arguments.of(new DeclareClaimedReturnedItemAsMissingRequest()
        .loanId(null)
        .itemId(null)
        .userId(randomUUID()))
    );
  }

  private static DeclareClaimedReturnedItemAsMissingRequest buildDeclareItemMissingByLoanIdRequest() {
    return new DeclareClaimedReturnedItemAsMissingRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .comment(COMMENT);
  }

  private static DeclareClaimedReturnedItemAsMissingRequest buildDeclareItemMissingByItemAndUserIdRequest() {
    return new DeclareClaimedReturnedItemAsMissingRequest()
      .itemId(ITEM_ID)
      .userId(USER_ID)
      .comment(COMMENT);
  }

  private static CirculationDeclareClaimedReturnedItemAsMissingRequest
  buildCirculationDeclareItemMissingRequest() {

    return new CirculationDeclareClaimedReturnedItemAsMissingRequest()
      .comment(COMMENT);
  }

  private static EcsTlrEntity buildEcsTlr() {
    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setItemId(ITEM_ID);
    ecsTlr.setRequesterId(USER_ID);
    ecsTlr.setPrimaryRequestId(ECS_REQUEST_ID);
    ecsTlr.setSecondaryRequestId(ECS_REQUEST_ID);
    ecsTlr.setPrimaryRequestTenantId("central");
    ecsTlr.setSecondaryRequestTenantId("lending");
    return ecsTlr;
  }

  private static Request buildEcsRequest() {
    return new Request()
      .id(ECS_REQUEST_ID.toString())
      .itemId(ITEM_ID.toString())
      .requesterId(USER_ID.toString())
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .status(Request.StatusEnum.CLOSED_FILLED);
  }

  private static Loan buildLoan(UUID loanId) {
    return new Loan()
      .id(loanId.toString())
      .status(new LoanStatus().name("Open"))
      .itemId(ITEM_ID.toString())
      .userId(USER_ID.toString());
  }


}
