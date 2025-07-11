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

import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.LoanStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.domain.mapper.CirculationMapperImpl;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.DeclareItemLostServiceImpl;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DeclareItemLostServiceTest {

  private static final UUID LOCAL_TENANT_LOAN_ID = randomUUID();
  private static final UUID LENDING_TENANT_LOAN_ID = randomUUID();
  private static final UUID USER_ID = randomUUID();
  private static final UUID ITEM_ID = randomUUID();
  private static final UUID ECS_REQUEST_ID = randomUUID();
  private static final UUID SERVICE_POINT_ID = randomUUID();
  private static final Date DECLARE_ITEM_LOST_DATE = new Date();
  private static final String DECLARE_ITEM_LOST_COMMENT = "Test comment";

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
  private DeclareItemLostServiceImpl declareItemLostService;

  @Test
  void declareItemLostByLoanIdInLocalAndLendingTenant() {
    mockSystemUserService(systemUserService);
    when(circulationClient.declareItemLost(anyString(), eq(buildCirculationDeclareItemLostRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.of(buildLoan(LENDING_TENANT_LOAN_ID)));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));

    declareItemLostService.declareItemLost(buildDeclareItemLostByLoanIdRequest());

    verify(circulationClient, times(1)).declareItemLost(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemLostRequest());
    verify(circulationClient, times(1)).declareItemLost(
      LENDING_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemLostRequest());
    verify(loanService, times(1)).findOpenLoan(USER_ID.toString(), ITEM_ID.toString());
  }

  @Test
  void itemIsDeclaredLostOnlyInLocalTenantWhenNoEcsRequestIsFound() {
    when(circulationClient.declareItemLost(anyString(), eq(buildCirculationDeclareItemLostRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.empty());

    declareItemLostService.declareItemLost(buildDeclareItemLostByLoanIdRequest());

    verify(circulationClient, times(1)).declareItemLost(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemLostRequest());
    verifyNoMoreInteractions(circulationClient);
  }

  @Test
  void itemIsDeclaredLostOnlyInLocalTenantWhenNoEcsTlrIsFound() {
    when(circulationClient.declareItemLost(anyString(), eq(buildCirculationDeclareItemLostRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.empty());

    declareItemLostService.declareItemLost(buildDeclareItemLostByLoanIdRequest());

    verify(circulationClient, times(1)).declareItemLost(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemLostRequest());
    verifyNoMoreInteractions(circulationClient);
  }

  @Test
  void declareItemLostFailsWhenLoanIsNotFoundInLendingTenant() {
    mockSystemUserService(systemUserService);
    when(circulationClient.declareItemLost(anyString(), eq(buildCirculationDeclareItemLostRequest())))
      .thenReturn(ResponseEntity.noContent().build());
    when(loanService.fetchLoan(LOCAL_TENANT_LOAN_ID.toString()))
      .thenReturn(buildLoan(LOCAL_TENANT_LOAN_ID));
    when(requestService.findEcsRequestForLoan(buildLoan(LOCAL_TENANT_LOAN_ID)))
      .thenReturn(Optional.of(buildEcsRequest()));
    when(ecsTlrRepository.findByPrimaryRequestId(ECS_REQUEST_ID))
      .thenReturn(Optional.of(buildEcsTlr()));
    when(loanService.findOpenLoan(USER_ID.toString(), ITEM_ID.toString()))
      .thenReturn(Optional.empty());

    DeclareItemLostRequest request = buildDeclareItemLostByLoanIdRequest();
    assertThrows(NoSuchElementException.class,
      () -> declareItemLostService.declareItemLost(request));

    verify(circulationClient, times(1)).declareItemLost(
      LOCAL_TENANT_LOAN_ID.toString(), buildCirculationDeclareItemLostRequest());
    verifyNoMoreInteractions(circulationClient);
  }

  private static DeclareItemLostRequest buildDeclareItemLostByLoanIdRequest() {
    return new DeclareItemLostRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(DECLARE_ITEM_LOST_DATE)
      .comment(DECLARE_ITEM_LOST_COMMENT);
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

  private static CirculationDeclareItemLostRequest buildCirculationDeclareItemLostRequest() {
    return new CirculationDeclareItemLostRequest()
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(DECLARE_ITEM_LOST_DATE)
      .comment(DECLARE_ITEM_LOST_COMMENT);
  }
}