package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.support.MockDataUtils.buildCirculationClaimItemReturnedRequest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.folio.domain.dto.CirculationClaimItemReturnedRequest;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.repository.EcsTlrRepository;
import org.folio.support.MockDataUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

class ClaimItemReturnedApiTest extends LoanActionApiTest {

  private static final String CLAIM_ITEM_RETURNED_URL = "/tlr/loans/claim-item-returned";
  private static final String CIRCULATION_CLAIM_ITEM_RETURNED_URL_TEMPLATE =
    "/circulation/loans/%s/claim-item-returned";

  @Autowired
  private EcsTlrRepository ecsTlrRepository;

  @BeforeEach
  void beforeEach() {
    wireMockServer.resetAll();
    ecsTlrRepository.deleteAll();
  }

  @Test
  void claimItemReturnedByLoanId() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());
    Date claimItemReturnedDate = new Date();

    setupLocalLoanMock(loanCreationDate, LOCAL_TENANT_LOAN_ID.toString());
    setupLendingTenantLoanMock(loanCreationDate);
    setupEcsRequestMock(Date.from(now.minusSeconds(30).toInstant()));
    mockEcsTlrEntity();
    setupCirculationMocks(claimItemReturnedDate);

    ClaimItemReturnedRequest claimItemReturnedRequest = MockDataUtils.buildClaimItemReturnedRequest(
      LOCAL_TENANT_LOAN_ID, claimItemReturnedDate, ACTION_COMMENT);

    claimItemReturned(claimItemReturnedRequest).expectStatus().isNoContent();

    verifyCirculationCalls(claimItemReturnedDate);
  }

  @Test
  void claimItemReturnedByUserIdAndItemId() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());
    Date claimItemReturnedDate = new Date();

    setupLocalLoanMockWithQuery(loanCreationDate);
    setupLendingTenantLoanMock(loanCreationDate);
    setupEcsRequestMock(Date.from(now.minusSeconds(30).toInstant()));
    mockEcsTlrEntity();
    setupCirculationMocks(claimItemReturnedDate);

    ClaimItemReturnedRequest claimItemReturnedRequest = MockDataUtils.buildClaimItemReturnedRequest(
      USER_ID, ITEM_ID, claimItemReturnedDate, ACTION_COMMENT);

    claimItemReturned(claimItemReturnedRequest)
      .expectStatus().isNoContent();

    verifyCirculationCalls(claimItemReturnedDate);
  }

  @ParameterizedTest
  @ValueSource(ints = { 400, 404, 422, 500 })
  void circulationApiErrorsAreForwarded(int circulationStatusCode) {
    setupLocalLoanMock(new Date(), LOCAL_TENANT_LOAN_ID.toString());

    mockErrorCode(
      CIRCULATION_CLAIM_ITEM_RETURNED_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString()),
      circulationStatusCode);

    ClaimItemReturnedRequest request = new ClaimItemReturnedRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .itemClaimedReturnedDateTime(new Date())
      .comment(ACTION_COMMENT);

    claimItemReturned(request)
      .expectStatus().isEqualTo(circulationStatusCode);
  }

  @Test
  void claimItemReturnedFailsWhenRequestDoesNotHaveItemClaimedReturnedDateTime() {
    claimItemReturned(new ClaimItemReturnedRequest().loanId(LOCAL_TENANT_LOAN_ID)
      .itemClaimedReturnedDateTime(null)).expectStatus().isBadRequest();
  }

  private WebTestClient.ResponseSpec claimItemReturned(
    ClaimItemReturnedRequest claimItemReturnedRequest) {

    return doPost(CLAIM_ITEM_RETURNED_URL, claimItemReturnedRequest);
  }

  private void setupCirculationMocks(Date claimItemReturnedDate) {
    CirculationClaimItemReturnedRequest expectedCirculationRequestInLocalTenant =
      buildCirculationClaimItemReturnedRequest(claimItemReturnedDate, ACTION_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_CLAIM_ITEM_RETURNED_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLocalTenant)))
      .willReturn(noContent()));

    CirculationClaimItemReturnedRequest expectedCirculationRequestInLendingTenant =
      buildCirculationClaimItemReturnedRequest(claimItemReturnedDate, ACTION_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_CLAIM_ITEM_RETURNED_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLendingTenant)))
      .willReturn(noContent()));
  }

  private void verifyCirculationCalls(Date claimItemReturnedDate) {
    CirculationClaimItemReturnedRequest expectedLocalRequest =
      buildCirculationClaimItemReturnedRequest(claimItemReturnedDate, ACTION_COMMENT);
    CirculationClaimItemReturnedRequest expectedLendingRequest =
      buildCirculationClaimItemReturnedRequest(claimItemReturnedDate, ACTION_COMMENT);

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_CLAIM_ITEM_RETURNED_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedLocalRequest))));

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_CLAIM_ITEM_RETURNED_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedLendingRequest))));
  }

}
