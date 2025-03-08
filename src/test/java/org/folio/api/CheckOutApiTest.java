package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.BatchIds;
import org.folio.domain.dto.CheckOutDryRunRequest;
import org.folio.domain.dto.CheckOutDryRunResponse;
import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.folio.domain.dto.ConsortiumItem;
import org.folio.domain.dto.ConsortiumItems;
import org.folio.domain.dto.LoanPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.reactive.server.WebTestClient;

class CheckOutApiTest extends BaseIT {

  private static final String CHECK_OUT_URL = "/tlr/loans/check-out-by-barcode";
  private static final String CIRCULATION_CHECK_OUT_URL = "/circulation/check-out-by-barcode";
  private static final String CIRCULATION_CHECK_OUT_DRY_RUN_URL = "/circulation/check-out-by-barcode-dry-run";
  private static final String LOAN_POLICY_STORAGE_URL = "/loan-policy-storage/loan-policies";
  private static final String SEARCH_ITEMS_URL = "/search/consortium/batch/items";

  private static final String ITEM_ID = randomId();
  private static final String USER_ID = randomId();
  private static final String ITEM_BARCODE = "test_item_barcode";
  private static final String USER_BARCODE = "test_user_barcode";
  private static final UUID SERVICE_POINT_ID = randomUUID();

  @Test
  void checkOutSuccess() {
    String loanPolicyId = randomId();
    CheckOutRequest checkOutRequest = buildCheckoutRequest(loanPolicyId);
    CheckOutDryRunRequest checkOutDryRunRequest = buildCheckoutDryRunRequest();
    CheckOutResponse checkOutResponse = buildCheckOutResponse();
    CheckOutDryRunResponse checkOutDryRunResponse = buildCheckoutDryRunResponse(loanPolicyId);

    BatchIds itemsSearchRequest = new BatchIds()
      .identifierType(BatchIds.IdentifierTypeEnum.BARCODE)
        .identifierValues(List.of(checkOutRequest.getItemBarcode()));
    ConsortiumItems itemsSearchResponse = new ConsortiumItems()
      .totalRecords(1)
      .items(List.of(new ConsortiumItem()
        .id(ITEM_ID)
        .barcode(ITEM_BARCODE)
        .tenantId(TENANT_ID_COLLEGE)));

    wireMockServer.stubFor(post(urlEqualTo(CIRCULATION_CHECK_OUT_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(checkOutRequest)))
      .willReturn(jsonResponse(asJsonString(checkOutResponse), HttpStatus.SC_OK)));

    wireMockServer.stubFor(post(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(itemsSearchRequest)))
      .willReturn(jsonResponse(asJsonString(itemsSearchResponse), HttpStatus.SC_OK)));

    wireMockServer.stubFor(post(urlEqualTo(CIRCULATION_CHECK_OUT_DRY_RUN_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(checkOutDryRunRequest)))
      .willReturn(jsonResponse(asJsonString(checkOutDryRunResponse), HttpStatus.SC_CREATED)));

    LoanPolicy loanPolicy = new LoanPolicy().id(loanPolicyId).name("test loanPolicy");
    wireMockServer.stubFor(get(urlEqualTo(LOAN_POLICY_STORAGE_URL + "/" + loanPolicyId))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(loanPolicy), HttpStatus.SC_OK)));

    LoanPolicy clonedLoanPolicy = loanPolicy.name("COPY_OF_" + loanPolicy.getName());
    wireMockServer.stubFor(post(urlEqualTo(LOAN_POLICY_STORAGE_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(loanPolicy.name(clonedLoanPolicy.getName()))))
      .willReturn(jsonResponse(asJsonString(clonedLoanPolicy), HttpStatus.SC_OK)));

    checkOut(checkOutRequest).expectStatus().isOk();

    wireMockServer.verify(postRequestedFor(urlEqualTo(CIRCULATION_CHECK_OUT_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));
    wireMockServer.verify(postRequestedFor(urlEqualTo(CIRCULATION_CHECK_OUT_DRY_RUN_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE)));
    wireMockServer.verify(getRequestedFor(urlEqualTo(LOAN_POLICY_STORAGE_URL + "/" + loanPolicyId))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE)));
    wireMockServer.verify(postRequestedFor(urlEqualTo(LOAN_POLICY_STORAGE_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));
    wireMockServer.verify(postRequestedFor(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));
  }

  @Test
  void checkOutFailsIfItemIsNotFound() {
    String loanPolicyId = randomId();
    CheckOutRequest checkOutRequest = buildCheckoutRequest(loanPolicyId);

    BatchIds itemsSearchRequest = new BatchIds()
      .identifierType(BatchIds.IdentifierTypeEnum.BARCODE)
      .identifierValues(List.of(checkOutRequest.getItemBarcode()));

    ConsortiumItems itemsSearchResponse = new ConsortiumItems()
      .totalRecords(0)
      .items(emptyList());

    wireMockServer.stubFor(post(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(itemsSearchRequest)))
      .willReturn(jsonResponse(asJsonString(itemsSearchResponse), HttpStatus.SC_OK)));

    checkOut(checkOutRequest)
      .expectStatus().is5xxServerError();

    wireMockServer.verify(postRequestedFor(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));
  }

  @ParameterizedTest
  @ValueSource(ints = { 400, 422, 500 })
  void circulationCheckOutErrorsAreForwarded(int statusCode) {
    String loanPolicyId = randomId();
    CheckOutRequest checkOutRequest = buildCheckoutRequest(loanPolicyId);
    CheckOutDryRunRequest checkOutDryRunRequest = buildCheckoutDryRunRequest();

    BatchIds itemsSearchRequest = new BatchIds()
      .identifierType(BatchIds.IdentifierTypeEnum.BARCODE)
      .identifierValues(List.of(checkOutDryRunRequest.getItemBarcode()));

    ConsortiumItems itemsSearchResponse = new ConsortiumItems()
      .totalRecords(1)
      .items(List.of(new ConsortiumItem()
        .id(ITEM_ID)
        .barcode(ITEM_BARCODE)
        .tenantId(TENANT_ID_COLLEGE)));

    wireMockServer.stubFor(post(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(itemsSearchRequest)))
      .willReturn(jsonResponse(asJsonString(itemsSearchResponse), HttpStatus.SC_OK)));

    wireMockServer.stubFor(post(urlEqualTo(CIRCULATION_CHECK_OUT_DRY_RUN_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(checkOutDryRunRequest)))
      .willReturn(aResponse().withStatus(statusCode).withBody("Status code is " + statusCode)));

    checkOut(checkOutRequest)
      .expectStatus().isEqualTo(statusCode)
      .expectBody(String.class).isEqualTo("Status code is " + statusCode);

    wireMockServer.verify(postRequestedFor(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(postRequestedFor(urlEqualTo(CIRCULATION_CHECK_OUT_DRY_RUN_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE)));
  }

  private static CheckOutRequest buildCheckoutRequest(String loanPolicyId) {
    return new CheckOutRequest()
      .itemBarcode(ITEM_BARCODE)
      .servicePointId(SERVICE_POINT_ID)
      .userBarcode(USER_BARCODE)
      .forceLoanPolicyId(UUID.fromString(loanPolicyId));
  }

  private static CheckOutDryRunRequest buildCheckoutDryRunRequest() {
    return new CheckOutDryRunRequest()
      .itemBarcode(ITEM_BARCODE)
      .userBarcode(USER_BARCODE);
  }

  private static CheckOutResponse buildCheckOutResponse() {
    return new CheckOutResponse()
      .id(randomId())
      .itemId(ITEM_ID)
      .userId(USER_ID);
  }

  private CheckOutDryRunResponse buildCheckoutDryRunResponse(String loanPolicyId) {
    return new CheckOutDryRunResponse()
      .loanPolicyId(loanPolicyId)
      .overdueFinePolicyId(randomId())
      .lostItemPolicyId(randomId())
      .patronNoticePolicyId(randomId());
  }

  private WebTestClient.ResponseSpec checkOut(CheckOutRequest request) {
    return doPost(CHECK_OUT_URL, request);
  }
}
