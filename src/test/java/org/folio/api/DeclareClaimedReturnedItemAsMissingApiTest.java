package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.support.MockDataUtils.buildDeclareItemMissingRequest;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.stringContainsInOrder;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.repository.EcsTlrRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

class DeclareClaimedReturnedItemAsMissingApiTest extends LoanActionBaseIT {

  private static final String DECLARE_ITEM_MISSING_URL =
    "/tlr/loans/declare-claimed-returned-item-as-missing";
  private static final String CIRCULATION_DECLARE_ITEM_MISSING_URL_TEMPLATE =
    "/circulation/loans/%s/declare-claimed-returned-item-as-missing";

  @Autowired
  private EcsTlrRepository ecsTlrRepository;

  @BeforeEach
  void beforeEach() {
    wireMockServer.resetAll();
    ecsTlrRepository.deleteAll();
  }

  @Test
  void declareItemMissingByLoanId() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());

    setupLocalLoanMock(loanCreationDate, LOCAL_TENANT_LOAN_ID.toString());
    setupLendingTenantLoanMock(loanCreationDate);
    setupEcsRequestMock(Date.from(now.minusSeconds(30).toInstant()));
    mockEcsTlrEntity();
    setupCirculationMocks();

    declareItemMissing(buildDeclareItemMissingRequest(LOCAL_TENANT_LOAN_ID, ACTION_COMMENT))
      .expectStatus().isNoContent();

    verifyCirculationCalls();
  }

  @Test
  void declareItemMissingByUserIdAndItemId() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());

    setupLocalLoanMockWithQuery(loanCreationDate);
    setupLendingTenantLoanMock(loanCreationDate);
    setupEcsRequestMock(Date.from(now.minusSeconds(30).toInstant()));
    mockEcsTlrEntity();
    setupCirculationMocks();

    declareItemMissing(buildDeclareItemMissingRequest(USER_ID, ITEM_ID, ACTION_COMMENT))
      .expectStatus().isNoContent();

    verifyCirculationCalls();
  }

  @ParameterizedTest
  @ValueSource(ints = { 400, 404, 422, 500 })
  void circulationApiErrorsAreForwarded(int circulationStatusCode) {
    setupLocalLoanMock(new Date(), LOCAL_TENANT_LOAN_ID.toString());

    mockErrorCode(
      CIRCULATION_DECLARE_ITEM_MISSING_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString()),
      circulationStatusCode);


    declareItemMissing(buildDeclareItemMissingRequest(LOCAL_TENANT_LOAN_ID, ACTION_COMMENT))
      .expectStatus().isEqualTo(circulationStatusCode);
  }

  @Test
  void declareItemMissingFailsWhenRequestDoesNotHaveComment() {
    declareItemMissing(new DeclareClaimedReturnedItemAsMissingRequest().comment(null))
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectBody()
      .jsonPath("$.errors").value(hasSize(1))
      .jsonPath("$.errors[0].type").isEqualTo("MethodArgumentNotValidException")
      .jsonPath("$.errors[0].message").value(stringContainsInOrder(
        "Validation failed for argument", "must not be null"));
  }

  @Test
  void declareItemMissingFailsWhenLocalLoanIsNotFound() {
    mockErrorCode(LOAN_STORAGE_URL + "/" + LOCAL_TENANT_LOAN_ID, 404);
    DeclareClaimedReturnedItemAsMissingRequest request = buildDeclareItemMissingRequest(
      LOCAL_TENANT_LOAN_ID, ACTION_COMMENT);

    declareItemMissing(request)
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectBody()
      .jsonPath("$.errors").value(hasSize(1))
      .jsonPath("$.errors[0].code").isEqualTo("LOAN_NOT_FOUND")
      .jsonPath("$.errors[0].message").isEqualTo("Loan not found")
      .jsonPath("$.errors[0].type").isEqualTo("ValidationException")
      .jsonPath("$.errors[0].parameters").value(hasSize(1))
      .jsonPath("$.errors[0].parameters").value(containsInAnyOrder(
        allOf(hasEntry("key", "id"), hasEntry("value", LOCAL_TENANT_LOAN_ID.toString()))
      ));
  }

  @Test
  void declareItemMissingFailsWhenRequestIsInvalid() {
    DeclareClaimedReturnedItemAsMissingRequest request = new DeclareClaimedReturnedItemAsMissingRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .itemId(ITEM_ID)
      .userId(USER_ID)
      .comment(ACTION_COMMENT);

    declareItemMissing(request)
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectBody()
      .jsonPath("$.errors").value(hasSize(1))
      .jsonPath("$.errors[0].code").isEqualTo("INVALID_LOAN_ACTION_REQUEST")
      .jsonPath("$.errors[0].message").isEqualTo(INVALID_REQUEST_ERROR_MESSAGE)
      .jsonPath("$.errors[0].type").isEqualTo("ValidationException")
      .jsonPath("$.errors[0].parameters").value(hasSize(3))
      .jsonPath("$.errors[0].parameters").value(containsInAnyOrder(
        allOf(hasEntry("key", "loanId"), hasEntry("value", LOCAL_TENANT_LOAN_ID.toString())),
        allOf(hasEntry("key", "userId"), hasEntry("value", USER_ID.toString())),
        allOf(hasEntry("key", "itemId"), hasEntry("value", ITEM_ID.toString()))
      ));
  }

  @Test
  void unexpectedErrorIsHandledCorrectly() {
    wireMockServer.stubFor(get(urlEqualTo(LOAN_STORAGE_URL + "/" + LOCAL_TENANT_LOAN_ID))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson("not a json")));

    DeclareClaimedReturnedItemAsMissingRequest request = new DeclareClaimedReturnedItemAsMissingRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .comment(ACTION_COMMENT);

    declareItemMissing(request)
      .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
      .expectBody()
      .jsonPath("$.errors").value(hasSize(1))
      .jsonPath("$.errors[0].type").isEqualTo("DecodeException")
      .jsonPath("$.errors[0].code").isEqualTo("INTERNAL_SERVER_ERROR")
      .jsonPath("$.errors[0].message").value(startsWith("Error while extracting response"));
  }

  private WebTestClient.ResponseSpec declareItemMissing(
    DeclareClaimedReturnedItemAsMissingRequest request) {

    return doPost(DECLARE_ITEM_MISSING_URL, request);
  }

  private void setupCirculationMocks() {
    var expectedCirculationRequest = new DeclareClaimedReturnedItemAsMissingRequest(ACTION_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_MISSING_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequest)))
      .willReturn(noContent()));

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_MISSING_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequest)))
      .willReturn(noContent()));
  }

  private void verifyCirculationCalls() {
    var expectedCirculationRequest = new DeclareClaimedReturnedItemAsMissingRequest(ACTION_COMMENT);

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_MISSING_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequest))));

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_MISSING_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequest))));
  }

}
