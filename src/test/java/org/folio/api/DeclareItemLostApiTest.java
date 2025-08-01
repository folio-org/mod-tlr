package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.repository.EcsTlrRepository;
import org.folio.support.MockDataUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

class DeclareItemLostApiTest extends LoanActionBaseIT {

  private static final String DECLARE_ITEM_LOST_URL = "/tlr/loans/declare-item-lost";
  private static final String CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE =
    "/circulation/loans/%s/declare-item-lost";

  @Autowired
  private EcsTlrRepository ecsTlrRepository;

  @BeforeEach
  void beforeEach() {
    wireMockServer.resetAll();
    ecsTlrRepository.deleteAll();
  }

  @Test
  void declareItemLostByLoanId() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());
    Date declareItemLostDate = new Date();

    setupLocalLoanMock(loanCreationDate, LOCAL_TENANT_LOAN_ID.toString());
    setupLendingTenantLoanMock(loanCreationDate);
    setupEcsRequestMock(Date.from(now.minusSeconds(30).toInstant()));
    mockEcsTlrEntity();
    setupCirculationMocks(declareItemLostDate);

    DeclareItemLostRequest declareItemLostRequest = MockDataUtils.buildDeclareItemLostRequest(
      LOCAL_TENANT_LOAN_ID, SERVICE_POINT_ID, declareItemLostDate, ACTION_COMMENT);

    declareItemLost(declareItemLostRequest)
      .expectStatus().isNoContent();

    verifyCirculationCalls(declareItemLostDate);
  }

  @Test
  void declareItemLostByUserIdAndItemId() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());
    Date declareItemLostDate = new Date();

    setupLocalLoanMockWithQuery(loanCreationDate);
    setupLendingTenantLoanMock(loanCreationDate);
    setupEcsRequestMock(Date.from(now.minusSeconds(30).toInstant()));
    mockEcsTlrEntity();
    setupCirculationMocks(declareItemLostDate);

    DeclareItemLostRequest declareItemLostRequest = MockDataUtils.buildDeclareItemLostRequest(
      USER_ID, ITEM_ID, SERVICE_POINT_ID, declareItemLostDate, ACTION_COMMENT);

    declareItemLost(declareItemLostRequest)
      .expectStatus().isNoContent();

    verifyCirculationCalls(declareItemLostDate);
  }

  @ParameterizedTest
  @ValueSource(ints = { 400, 404, 422, 500 })
  void circulationApiErrorsAreForwarded(int circulationStatusCode) {
    setupLocalLoanMock(new Date(), LOCAL_TENANT_LOAN_ID.toString());

    mockErrorCode(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString()),
      circulationStatusCode);

    DeclareItemLostRequest request = new DeclareItemLostRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(new Date())
      .comment(ACTION_COMMENT);

    declareItemLost(request)
      .expectStatus().isEqualTo(circulationStatusCode);
  }

  @Test
  void declareItemLostFailsWhenRequestDoesNotHaveServicePointId() {
    declareItemLost(new DeclareItemLostRequest().loanId(LOCAL_TENANT_LOAN_ID).servicePointId(null))
      .expectStatus().isBadRequest();
  }

  private WebTestClient.ResponseSpec declareItemLost(DeclareItemLostRequest declareItemLostRequest) {
    return doPost(DECLARE_ITEM_LOST_URL, declareItemLostRequest);
  }

  private void setupCirculationMocks(Date declareItemLostDate) {
    CirculationDeclareItemLostRequest expectedCirculationRequestInLocalTenant =
      MockDataUtils.buildCirculationDeclareItemLostRequest(SERVICE_POINT_ID, declareItemLostDate,
        ACTION_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLocalTenant)))
      .willReturn(noContent()));

    CirculationDeclareItemLostRequest expectedCirculationRequestInLendingTenant =
      MockDataUtils.buildCirculationDeclareItemLostRequest(SERVICE_POINT_ID, declareItemLostDate,
        ACTION_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLendingTenant)))
      .willReturn(noContent()));
  }

  private void verifyCirculationCalls(Date declareItemLostDate) {
    CirculationDeclareItemLostRequest expectedLocalRequest =
      MockDataUtils.buildCirculationDeclareItemLostRequest(SERVICE_POINT_ID, declareItemLostDate,
        ACTION_COMMENT);
    CirculationDeclareItemLostRequest expectedLendingRequest =
      MockDataUtils.buildCirculationDeclareItemLostRequest(SERVICE_POINT_ID, declareItemLostDate,
        ACTION_COMMENT);

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedLocalRequest))));

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedLendingRequest))));
  }

}
