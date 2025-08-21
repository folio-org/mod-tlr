package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.folio.domain.dto.CirculationClaimItemReturnedRequest;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.service.impl.ClaimItemReturnedServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ClaimItemReturnedServiceTest extends
  AbstractLoanActionServiceTest<ClaimItemReturnedRequest, CirculationClaimItemReturnedRequest> {

  @InjectMocks
  private ClaimItemReturnedServiceImpl service;

  @Override
  protected void performLoanAction(ClaimItemReturnedRequest request) {
    service.claimItemReturned(request);
  }

  @Override
  protected ClaimItemReturnedRequest buildRequestByLoanId() {
    return new ClaimItemReturnedRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .itemClaimedReturnedDateTime(ACTION_DATE)
      .comment(COMMENT);
  }

  @Override
  protected ClaimItemReturnedRequest buildRequestByItemAndUserId() {
    return new ClaimItemReturnedRequest()
      .itemId(ITEM_ID)
      .userId(USER_ID)
      .itemClaimedReturnedDateTime(ACTION_DATE)
      .comment(COMMENT);
  }

  @Override
  protected CirculationClaimItemReturnedRequest buildCirculationRequest() {
    return new CirculationClaimItemReturnedRequest()
      .itemClaimedReturnedDateTime(ACTION_DATE)
      .comment(COMMENT);
  }

  @Override
  protected void mockClientAction(String loanId, CirculationClaimItemReturnedRequest circulationRequest) {
    when(circulationClient.claimItemReturned(loanId, circulationRequest))
      .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  protected void verifyClientAction(String loanId, CirculationClaimItemReturnedRequest circulationRequest) {
    verify(circulationClient, times(1))
      .claimItemReturned(loanId, circulationRequest);
  }

  private static Stream<Arguments> buildRequestsWithInvalidCombinationOfParameters() {
    return Stream.of(
      Arguments.of(new ClaimItemReturnedRequest()
        .loanId(randomUUID())
        .itemId(randomUUID())
        .userId(randomUUID())),
      Arguments.of(new ClaimItemReturnedRequest()
        .loanId(null)
        .itemId(null)
        .userId(null)),
      Arguments.of(new ClaimItemReturnedRequest()
        .loanId(randomUUID())
        .itemId(randomUUID())
        .userId(null)),
      Arguments.of(new ClaimItemReturnedRequest()
        .loanId(randomUUID())
        .itemId(null)
        .userId(randomUUID())),
      Arguments.of(new ClaimItemReturnedRequest()
        .loanId(null)
        .itemId(randomUUID())
        .userId(null)),
      Arguments.of(new ClaimItemReturnedRequest()
        .loanId(null)
        .itemId(null)
        .userId(randomUUID()))
    );
  }

}
