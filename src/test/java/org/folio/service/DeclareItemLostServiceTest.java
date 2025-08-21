package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.service.impl.DeclareItemLostServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DeclareItemLostServiceTest extends
  AbstractLoanActionServiceTest<DeclareItemLostRequest, CirculationDeclareItemLostRequest> {

  @InjectMocks
  private DeclareItemLostServiceImpl service;

  @Override
  protected void performLoanAction(DeclareItemLostRequest request) {
    service.declareItemLost(request);
  }

  @Override
  protected DeclareItemLostRequest buildRequestByLoanId() {
    return new DeclareItemLostRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(ACTION_DATE)
      .comment(COMMENT);
  }

  @Override
  protected DeclareItemLostRequest buildRequestByItemAndUserId() {
    return new DeclareItemLostRequest()
      .itemId(ITEM_ID)
      .userId(USER_ID)
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(ACTION_DATE)
      .comment(COMMENT);
  }

  @Override
  protected CirculationDeclareItemLostRequest buildCirculationRequest() {
    return new CirculationDeclareItemLostRequest()
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(ACTION_DATE)
      .comment(COMMENT);
  }

  @Override
  protected void mockClientAction(String loanId, CirculationDeclareItemLostRequest circulationRequest) {
    when(circulationClient.declareItemLost(loanId, circulationRequest))
      .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  protected void verifyClientAction(String loanId, CirculationDeclareItemLostRequest circulationRequest) {
    verify(circulationClient, times(1))
      .declareItemLost(loanId, circulationRequest);
  }

  private static Stream<Arguments> buildRequestsWithInvalidCombinationOfParameters() {
    return Stream.of(
      Arguments.of(new DeclareItemLostRequest()
        .loanId(randomUUID())
        .itemId(randomUUID())
        .userId(randomUUID())),
      Arguments.of(new DeclareItemLostRequest()
        .loanId(null)
        .itemId(null)
        .userId(null)),
      Arguments.of(new DeclareItemLostRequest()
        .loanId(randomUUID())
        .itemId(randomUUID())
        .userId(null)),
      Arguments.of(new DeclareItemLostRequest()
        .loanId(randomUUID())
        .itemId(null)
        .userId(randomUUID())),
      Arguments.of(new DeclareItemLostRequest()
        .loanId(null)
        .itemId(randomUUID())
        .userId(null)),
      Arguments.of(new DeclareItemLostRequest()
        .loanId(null)
        .itemId(null)
        .userId(randomUUID()))
    );
  }

}
