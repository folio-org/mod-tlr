package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.folio.util.TestUtils.mockSystemUserService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.LoanStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.domain.mapper.CirculationMapperImpl;
import org.folio.domain.type.ErrorCode;
import org.folio.exception.ValidationException;
import org.folio.repository.EcsTlrRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;

public abstract class AbstractLoanActionServiceTest<R, CR> {

  protected static final UUID LOCAL_TENANT_LOAN_ID = randomUUID();
  protected static final UUID LENDING_TENANT_LOAN_ID = randomUUID();
  protected static final UUID USER_ID = randomUUID();
  protected static final UUID ITEM_ID = randomUUID();
  protected static final UUID ECS_REQUEST_ID = randomUUID();
  protected static final UUID SERVICE_POINT_ID = randomUUID();
  protected static final Date ACTION_DATE = new Date();
  protected static final String COMMENT = "Test comment";

  @Spy
  protected CirculationMapper circulationMapper = new CirculationMapperImpl();
  @Mock
  protected CirculationErrorForwardingClient circulationClient;
  @Mock
  protected LoanService loanService;
  @Mock
  protected RequestService requestService;
  @Mock
  protected EcsTlrRepository ecsTlrRepository;
  @Mock
  protected SystemUserScopedExecutionService systemUserService;

  protected abstract void performLoanAction(R request);
  protected abstract R buildRequestByLoanId();
  protected abstract R buildRequestByItemAndUserId();
  protected abstract CR buildCirculationRequest();
  protected abstract void mockClientAction(String loanId, CR circulationRequest);
  protected abstract void verifyClientAction(String loanId, CR circulationRequest);

  @Test
  void loanActionByLoanIdInLocalAndLendingTenant() {
    CR circulationRequest = buildCirculationRequest();
    mockSystemUserService(systemUserService);
    mockClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LOCAL_TENANT_LOAN_ID)));
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LENDING_TENANT_LOAN_ID)));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));

    performLoanAction(buildRequestByLoanId());

    verifyClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    verifyClientAction(LENDING_TENANT_LOAN_ID.toString(), circulationRequest);
    verify(loanService, times(1)).findOpenLoan(USER_ID.toString(), ITEM_ID.toString());
  }

  @Test
  void loanActionByUserAndItemIdInLocalAndLendingTenant() {
    CR circulationRequest = buildCirculationRequest();
    mockSystemUserService(systemUserService);
    mockClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LOCAL_TENANT_LOAN_ID))) // loan in local tenant
      .thenReturn(Optional.of(buildLoan(LENDING_TENANT_LOAN_ID))); // loan in lending tenant
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));

    performLoanAction(buildRequestByItemAndUserId());

    verifyClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    verifyClientAction(LENDING_TENANT_LOAN_ID.toString(), circulationRequest);
    verify(loanService, times(2)).findOpenLoan(USER_ID.toString(), ITEM_ID.toString());
    verify(loanService, times(0)).fetchLoan(anyString());
  }

  @Test
  void loanActionOnlyInLocalTenantWhenNoEcsRequestIsFound() {
    CR circulationRequest = buildCirculationRequest();
    mockClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LOCAL_TENANT_LOAN_ID)));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.empty());

    performLoanAction(buildRequestByLoanId());

    verifyClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    verifyNoMoreInteractions(circulationClient);
  }

  @Test
  void loanActionOnlyInLocalTenantWhenNoEcsTlrIsFound() {
    CR circulationRequest = buildCirculationRequest();
    mockClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LOCAL_TENANT_LOAN_ID)));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.empty());

    performLoanAction(buildRequestByLoanId());

    verifyClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    verifyNoMoreInteractions(circulationClient);
  }

  @Test
  void loanActionFailsWhenLoanIsNotFoundByIdInLocalTenant() {
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(Optional.empty());

    R request = buildRequestByLoanId();
    ValidationException exception = assertThrows(ValidationException.class,
      () -> performLoanAction(request));

    Map<String, String> expectedErrorParameters = Map.of("id", LOCAL_TENANT_LOAN_ID.toString());
    assertEquals(expectedErrorParameters, exception.getParameters());
    assertEquals(ErrorCode.LOAN_NOT_FOUND, exception.getCode());
    assertEquals("Loan not found", exception.getMessage());
    assertEquals("ValidationException", exception.getType());

    verify(loanService, times(1)).fetchLoan(LOCAL_TENANT_LOAN_ID.toString());
    verifyNoMoreInteractions(loanService);
    verifyNoInteractions(circulationClient);
    verifyNoInteractions(requestService);
    verifyNoInteractions(ecsTlrRepository);
    verifyNoInteractions(systemUserService);
  }

  @Test
  void loanActionFailsWhenLoanIsNotFoundByUserIdAndItemIdInLocalTenant() {
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.empty());

    R request = buildRequestByItemAndUserId();
    ValidationException exception = assertThrows(ValidationException.class,
      () -> performLoanAction(request));

    Map<String, String> expectedErrorParameters = Map.of(
      "userId", USER_ID.toString(),
      "itemId", ITEM_ID.toString());

    assertEquals(expectedErrorParameters, exception.getParameters());
    assertEquals(ErrorCode.LOAN_NOT_FOUND, exception.getCode());
    assertEquals("Open loan not found", exception.getMessage());
    assertEquals("ValidationException", exception.getType());

    verify(loanService, times(1)).findOpenLoan(USER_ID.toString(), ITEM_ID.toString());
    verifyNoMoreInteractions(loanService);
    verifyNoInteractions(circulationClient);
    verifyNoInteractions(requestService);
    verifyNoInteractions(ecsTlrRepository);
    verifyNoInteractions(systemUserService);
  }

  @Test
  void loanActionFailsWhenLoanIsNotFoundInLendingTenant() {
    CR circulationRequest = buildCirculationRequest();
    mockSystemUserService(systemUserService);
    mockClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LOCAL_TENANT_LOAN_ID)));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.empty());

    R request = buildRequestByLoanId();
    ValidationException exception = assertThrows(ValidationException.class,
      () -> performLoanAction(request));

    Map<String, String> expectedErrorParameters = Map.of(
      "userId", USER_ID.toString(),
      "itemId", ITEM_ID.toString());

    assertEquals(expectedErrorParameters, exception.getParameters());
    assertEquals(ErrorCode.LOAN_NOT_FOUND, exception.getCode());
    assertEquals("Open loan not found", exception.getMessage());
    assertEquals("ValidationException", exception.getType());

    verifyClientAction(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest);
    verifyNoMoreInteractions(circulationClient);
  }

  @ParameterizedTest
  @MethodSource("buildRequestsWithInvalidCombinationOfParameters")
  void loanActionFailsWhenRequestHasInvalidCombinationOfParameters(R request) {
    ValidationException exception = assertThrows(ValidationException.class,
      () -> performLoanAction(request));

    assertEquals(ErrorCode.INVALID_LOAN_ACTION_REQUEST, exception.getCode());
    assertEquals("Invalid request: must have either loanId or (itemId and userId)", exception.getMessage());
    assertEquals("ValidationException", exception.getType());
    assertEquals(3, exception.getParameters().size());
  }

  protected static Loan buildLoan(UUID loanId) {
    return new Loan()
      .id(loanId.toString())
      .status(new LoanStatus().name("Open"))
      .itemId(ITEM_ID.toString())
      .userId(USER_ID.toString());
  }

  protected static Request buildEcsRequest() {
    return new Request()
      .id(ECS_REQUEST_ID.toString())
      .itemId(ITEM_ID.toString())
      .requesterId(USER_ID.toString())
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .status(Request.StatusEnum.CLOSED_FILLED);
  }

  protected static EcsTlrEntity buildEcsTlr() {
    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setItemId(ITEM_ID);
    ecsTlr.setRequesterId(USER_ID);
    ecsTlr.setPrimaryRequestId(ECS_REQUEST_ID);
    ecsTlr.setSecondaryRequestId(ECS_REQUEST_ID);
    ecsTlr.setPrimaryRequestTenantId("central");
    ecsTlr.setSecondaryRequestTenantId("lending");
    return ecsTlr;
  }
}
