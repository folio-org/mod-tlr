package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.folio.domain.dto.CirculationDeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.service.impl.DeclareClaimedReturnedItemAsMissingServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DeclareClaimedReturnedItemAsMissingServiceTest extends
  AbstractLoanActionServiceTest<DeclareClaimedReturnedItemAsMissingRequest,
        CirculationDeclareClaimedReturnedItemAsMissingRequest> {

  @InjectMocks
  private DeclareClaimedReturnedItemAsMissingServiceImpl service;

  @Override
  protected void performLoanAction(DeclareClaimedReturnedItemAsMissingRequest request) {
    service.declareMissing(request);
  }

  @Override
  protected DeclareClaimedReturnedItemAsMissingRequest buildRequestByLoanId() {
    return new DeclareClaimedReturnedItemAsMissingRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .comment(COMMENT);
  }

  @Override
  protected DeclareClaimedReturnedItemAsMissingRequest buildRequestByItemAndUserId() {
    return new DeclareClaimedReturnedItemAsMissingRequest()
      .itemId(ITEM_ID)
      .userId(USER_ID)
      .comment(COMMENT);
  }

  @Override
  protected CirculationDeclareClaimedReturnedItemAsMissingRequest buildCirculationRequest() {
    return new CirculationDeclareClaimedReturnedItemAsMissingRequest()
      .comment(COMMENT);
  }

  @Override
  protected void mockClientAction(String loanId,
    CirculationDeclareClaimedReturnedItemAsMissingRequest circulationRequest) {

    when(circulationClient.declareClaimedReturnedItemAsMissing(LOCAL_TENANT_LOAN_ID.toString(), circulationRequest))
      .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  protected void verifyClientAction(String loanId,
    CirculationDeclareClaimedReturnedItemAsMissingRequest circulationRequest) {

    verify(circulationClient, times(1))
      .declareClaimedReturnedItemAsMissing(loanId, circulationRequest);
  }

  protected static Stream<Arguments> buildRequestsWithInvalidCombinationOfParameters() {
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

}
