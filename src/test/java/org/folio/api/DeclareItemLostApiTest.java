package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Loans;
import org.folio.domain.dto.Metadata;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

public class DeclareItemLostApiTest extends BaseIT {

  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID LOCAL_TENANT_LOAN_ID = UUID.randomUUID();
  private static final UUID LENDING_TENANT_LOAN_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ECS_REQUEST_ID = UUID.randomUUID();

  private static final String DECLARE_ITEM_LOST_URL = "/tlr/loans/declare-item-lost";
  private static final String LOAN_STORAGE_URL = "/loan-storage/loans";
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";
  private static final String CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE =
    "/circulation/loans/%s/declare-item-lost";

  private static final String DECLARE_ITEM_LOST_COMMENT = "Test comment";

  @Autowired
  private EcsTlrRepository ecsTlrRepository;
  @Autowired private CirculationClient circulationClient;

  @Test
  void declareItemLost() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Date loanCreationDate = Date.from(now.toInstant());
    Date requestUpdateDate = Date.from(now.minusSeconds(30).toInstant());
    Date declareItemLostDate = new Date();

    // mock local loan

    Loan mockLocalLoan = new Loan()
      .id(LOCAL_TENANT_LOAN_ID.toString())
      .userId(USER_ID.toString())
      .itemId(ITEM_ID.toString())
      .metadata(new Metadata().createdDate(loanCreationDate));

    wireMockServer.stubFor(get(urlEqualTo(LOAN_STORAGE_URL + "/" + LOCAL_TENANT_LOAN_ID))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockLocalLoan))));

    // mock loan in lending tenant

    Loan mockLendingTenantLoan = new Loan()
      .id(LENDING_TENANT_LOAN_ID.toString())
      .userId(USER_ID.toString())
      .itemId(ITEM_ID.toString())
      .metadata(new Metadata().createdDate(loanCreationDate));

    wireMockServer.stubFor(get(urlPathEqualTo(LOAN_STORAGE_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Loans().addLoansItem(mockLendingTenantLoan)))));

    // mock local ECS request

    Request mockEcsRequest = new Request()
      .id(ECS_REQUEST_ID.toString())
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .requesterId(USER_ID.toString())
      .itemId(ITEM_ID.toString())
      .status(Request.StatusEnum.CLOSED_FILLED)
      .metadata(new Metadata().updatedDate(requestUpdateDate));

    wireMockServer.stubFor(get(urlPathEqualTo(REQUEST_STORAGE_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(new Requests().addRequestsItem(mockEcsRequest)))));

    // mock ECS TLR

    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setItemId(ITEM_ID);
    ecsTlr.setRequesterId(USER_ID);
    ecsTlr.setPrimaryRequestId(ECS_REQUEST_ID);
    ecsTlr.setSecondaryRequestId(ECS_REQUEST_ID);
    ecsTlr.setPrimaryRequestTenantId(TENANT_ID_CONSORTIUM);
    ecsTlr.setSecondaryRequestTenantId(TENANT_ID_COLLEGE);
    ecsTlrRepository.save(ecsTlr);

    // mock declare item lost in local circulation

    CirculationDeclareItemLostRequest expectedCirculationRequestInLocalTenant =
      new CirculationDeclareItemLostRequest()
        .servicePointId(SERVICE_POINT_ID)
        .declaredLostDateTime(declareItemLostDate)
        .comment(DECLARE_ITEM_LOST_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLocalTenant)))
      .willReturn(noContent()));

    // mock declare item lost in lending tenant circulation

    CirculationDeclareItemLostRequest expectedCirculationRequestInLendingTenant =
      new CirculationDeclareItemLostRequest()
        .servicePointId(SERVICE_POINT_ID)
        .declaredLostDateTime(declareItemLostDate)
        .comment(DECLARE_ITEM_LOST_COMMENT);

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLendingTenant)))
      .willReturn(noContent()));

    // declare item lost

    DeclareItemLostRequest declareItemLostRequest = new DeclareItemLostRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(declareItemLostDate)
      .comment(DECLARE_ITEM_LOST_COMMENT);

    declareItemLost(declareItemLostRequest)
      .expectStatus().isNoContent();

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLocalTenant))));

    wireMockServer.verify(1, postRequestedFor(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LENDING_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(expectedCirculationRequestInLendingTenant))));
  }

  @ParameterizedTest
  @ValueSource(ints = { 400, 404, 422, 500 })
  void circulationApiErrorsAreForwarded(int circulationStatusCode) {
    Loan mockLocalLoan = new Loan()
      .id(LOCAL_TENANT_LOAN_ID.toString())
      .userId(USER_ID.toString())
      .itemId(ITEM_ID.toString())
      .metadata(new Metadata().createdDate(new Date()));

    wireMockServer.stubFor(get(urlEqualTo(LOAN_STORAGE_URL + "/" + LOCAL_TENANT_LOAN_ID))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockLocalLoan))));

    wireMockServer.stubFor(post(urlEqualTo(
      CIRCULATION_DECLARE_ITEM_LOST_URL_TEMPLATE.formatted(LOCAL_TENANT_LOAN_ID.toString())))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(aResponse().withStatus(circulationStatusCode)));

    DeclareItemLostRequest request = new DeclareItemLostRequest()
      .loanId(LOCAL_TENANT_LOAN_ID)
      .servicePointId(SERVICE_POINT_ID)
      .declaredLostDateTime(new Date())
      .comment(DECLARE_ITEM_LOST_COMMENT);

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

}
