package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.support.MockDataUtils.buildDeclareItemLostRequest;
import static org.hamcrest.Matchers.hasSize;

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

    DeclareItemLostRequest declareItemLostRequest = buildDeclareItemLostRequest(
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

    DeclareItemLostRequest declareItemLostRequest = buildDeclareItemLostRequest(
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
  void declareItemLostFailsWhenLocalLoanIsNotFound() {
    mockErrorCode(LOAN_STORAGE_URL + "/" + LOCAL_TENANT_LOAN_ID, 404);
    DeclareItemLostRequest request = buildDeclareItemLostRequest(
      LOCAL_TENANT_LOAN_ID, SERVICE_POINT_ID, new Date(), ACTION_COMMENT);

    declareItemLost(request)
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.errors").value(hasSize(1))
      .jsonPath("$.errors[0].code").isEqualTo("LOAN_NOT_FOUND")
      .jsonPath("$.errors[0].message").isEqualTo("Loan not found")
      .jsonPath("$.errors[0].parameters").value(hasSize(1))
      .jsonPath("$.errors[0].parameters[0].key").isEqualTo("id")
      .jsonPath("$.errors[0].parameters[0].value").isEqualTo(LOCAL_TENANT_LOAN_ID.toString());
  }

  @Test
  void declareItemLostFailsWhenRequestIsInvalid() {
    DeclareItemLostRequest invalidRequest = new DeclareItemLostRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .itemId(ITEM_ID)
      .userId(USER_ID)
      .servicePointId(SERVICE_POINT_ID);

    declareItemLost(invalidRequest)
      .expectStatus().isBadRequest()
      .expectBody()
      .jsonPath("$.errors").value(hasSize(1))
      .jsonPath("$.errors[0].code").isEqualTo("INVALID_LOAN_ACTION_REQUEST")
      .jsonPath("$.errors[0].message").isEqualTo(INVALID_REQUEST_ERROR_MESSAGE)
      .jsonPath("$.errors[0].parameters").value(hasSize(3))
      .jsonPath("$.errors[0].parameters[0].key").isEqualTo("loanId")
      .jsonPath("$.errors[0].parameters[0].value").isEqualTo(LOCAL_TENANT_LOAN_ID.toString())
      .jsonPath("$.errors[0].parameters[1].key").isEqualTo("userId")
      .jsonPath("$.errors[0].parameters[1].value").isEqualTo(USER_ID.toString())
      .jsonPath("$.errors[0].parameters[2].key").isEqualTo("itemId")
      .jsonPath("$.errors[0].parameters[2].value").isEqualTo(ITEM_ID.toString());
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
