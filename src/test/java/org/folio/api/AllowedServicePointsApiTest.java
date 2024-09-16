package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.folio.domain.dto.AllowedServicePointsInner;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItemResponse;
import org.folio.domain.dto.User;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AllowedServicePointsApiTest extends BaseIT {
  private static final String ITEM_ID = randomId();
  private static final String INSTANCE_ID = randomId();
  private static final String REQUESTER_ID = randomId();
  private static final String PATRON_GROUP_ID = randomId();
  private static final String ECS_TLR_ID = randomId();
  private static final String PRIMARY_REQUEST_ID = randomId();
  private static final String SECONDARY_REQUEST_ID = PRIMARY_REQUEST_ID;
  private static final String BORROWING_TENANT_ID = TENANT_ID_CONSORTIUM;
  private static final String LENDING_TENANT_ID = TENANT_ID_COLLEGE;
  private static final String ALLOWED_SERVICE_POINTS_URL = "/tlr/allowed-service-points";
  private static final String ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL =
    ALLOWED_SERVICE_POINTS_URL + "?operation=replace&requestId=" + PRIMARY_REQUEST_ID;
  private static final String ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL =
    "/circulation/requests/allowed-service-points";
  private static final String ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN =
    ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL + ".*";
  private static final String SEARCH_INSTANCES_URL = "/search/instances.*";
  private static final String USER_URL = "/users/" + REQUESTER_ID;
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";
  private static final String SEARCH_ITEM_URL = "/search/consortium/item/" + ITEM_ID;

  @Autowired
  private EcsTlrRepository ecsTlrRepository;

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
    ecsTlrRepository.deleteAll();
  }

  @Test
  void allowedServicePointReturnsEmptyResultWhenNoRoutingSpInResponsesFromDataTenants() {
    var item1 = new Item();
    item1.setTenantId(TENANT_ID_UNIVERSITY);

    var item2 = new Item();
    item2.setTenantId(TENANT_ID_COLLEGE);

    var searchInstancesResponse = new SearchInstancesResponse();
    searchInstancesResponse.setTotalRecords(1);
    searchInstancesResponse.setInstances(List.of(new Instance().items(List.of(item1, item2))));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(searchInstancesResponse), SC_OK)));

    var allowedSpResponseConsortium = new AllowedServicePointsResponse();
    allowedSpResponseConsortium.setHold(Set.of(
      buildAllowedServicePoint("SP_consortium_1"),
      buildAllowedServicePoint("SP_consortium_2")));
    allowedSpResponseConsortium.setPage(null);
    allowedSpResponseConsortium.setRecall(Set.of(
      buildAllowedServicePoint("SP_consortium_3")));

    var allowedSpResponseUniversity = new AllowedServicePointsResponse();
    allowedSpResponseUniversity.setHold(null);
    allowedSpResponseUniversity.setPage(null);
    allowedSpResponseUniversity.setRecall(null);

    var allowedSpResponseCollege = new AllowedServicePointsResponse();
    allowedSpResponseCollege.setHold(null);
    allowedSpResponseCollege.setPage(null);
    allowedSpResponseCollege.setRecall(null);

    var allowedSpResponseCollegeWithRouting = new AllowedServicePointsResponse();
    allowedSpResponseCollegeWithRouting.setHold(null);
    allowedSpResponseCollegeWithRouting.setPage(Set.of(
      buildAllowedServicePoint("SP_college_1")));
    allowedSpResponseCollegeWithRouting.setRecall(null);

    User requester = new User().patronGroup(PATRON_GROUP_ID);
    wireMockServer.stubFor(get(urlMatching(USER_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(requester), SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseConsortium), SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseUniversity), SC_OK)));

    var collegeStubMapping = wireMockServer.stubFor(
      get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .willReturn(jsonResponse(asJsonString(allowedSpResponseCollege), SC_OK)));

    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&instanceId=%s",
        REQUESTER_ID, INSTANCE_ID))
      .expectStatus().isEqualTo(200)
      .expectBody().json("{}");

    wireMockServer.removeStub(collegeStubMapping);
    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseCollegeWithRouting),
        SC_OK)));

    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&instanceId=%s",
        REQUESTER_ID, INSTANCE_ID))
      .expectStatus().isEqualTo(200)
      .expectBody().json(asJsonString(allowedSpResponseConsortium));

    wireMockServer.verify(getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withQueryParam("patronGroupId", equalTo(PATRON_GROUP_ID))
      .withQueryParam("instanceId", equalTo(INSTANCE_ID))
      .withQueryParam("operation", equalTo("create"))
      .withQueryParam("useStubItem", equalTo("true")));
  }

  @Test
  void allowedServicePointsShouldReturn422WhenParametersAreInvalid() {
    doGet(ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s", randomId()))
      .expectStatus().isEqualTo(422);
  }

  @Test
  void replaceForRequestLinkedToItemWhenPrimaryRequestTypeIsAllowedInBorrowingTenant() {
    createEcsTlr(true);

    var mockAllowedSpResponseFromBorrowingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("borrowing-page")))
      .hold(Set.of(buildAllowedServicePoint("borrowing-hold")))
      .recall(Set.of(buildAllowedServicePoint("borrowing-recall")));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&useStubItem=true", PRIMARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromBorrowingTenant), SC_OK)));

    doGet(ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL)
      .expectStatus().isEqualTo(200)
      .expectBody()
      .jsonPath("Page").doesNotExist()
      .jsonPath("Recall").doesNotExist()
      .jsonPath("Hold").value(hasSize(1))
      .jsonPath("Hold[0].name").value(is("borrowing-hold"));

    wireMockServer.verify(0, getRequestedFor(urlMatching(REQUEST_STORAGE_URL + ".*")));
    wireMockServer.verify(0, getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID)));
  }

  @Test
  void replaceForRequestLinkedToItemWhenPrimaryRequestTypeIsNotAllowedInBorrowingTenant() {
    createEcsTlr(true);

    var mockAllowedSpResponseFromBorrowingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("borrowing-page")))
      .hold(null)
      .recall(Set.of(buildAllowedServicePoint("borrowing-recall")));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&useStubItem=true", PRIMARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromBorrowingTenant), SC_OK)));

    doGet(ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL)
      .expectStatus().isEqualTo(200)
      .expectBody()
      .jsonPath("Page").doesNotExist()
      .jsonPath("Hold").doesNotExist()
      .jsonPath("Recall").doesNotExist();

    wireMockServer.verify(0, getRequestedFor(urlMatching(REQUEST_STORAGE_URL + ".*")));
    wireMockServer.verify(0, getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID)));
  }

  @Test
  void replaceForRequestNotLinkedToItemWhenSecondaryRequestTypeIsNoLongerAllowedInLendingTenant() {
    createEcsTlr(false);

    Request secondaryRequest = new Request().id(SECONDARY_REQUEST_ID)
      .requestType(Request.RequestTypeEnum.PAGE);

    wireMockServer.stubFor(get(urlMatching(REQUEST_STORAGE_URL + "/" + SECONDARY_REQUEST_ID))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), SC_OK)));

    var mockAllowedSpResponseFromLendingTenant = new AllowedServicePointsResponse()
      .page(null)
      .hold(Set.of(buildAllowedServicePoint("lending-hold")))
      .recall(Set.of(buildAllowedServicePoint("lending-recall")));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&ecsRequestRouting=true", SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromLendingTenant), SC_OK)));

    doGet(ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL)
      .expectStatus().isEqualTo(200)
      .expectBody()
      .jsonPath("Page").doesNotExist()
      .jsonPath("Recall").doesNotExist()
      .jsonPath("Hold").doesNotExist();

    wireMockServer.verify(0, getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID)));
  }

  @Test
  void replaceForRequestNotLinkedToItemWhenSecondaryRequestTypeIsAllowedInLendingTenant() {
    createEcsTlr(false);

    Request secondaryRequest = new Request().id(SECONDARY_REQUEST_ID)
      .requestType(Request.RequestTypeEnum.PAGE);

    wireMockServer.stubFor(get(urlMatching(REQUEST_STORAGE_URL + "/" + SECONDARY_REQUEST_ID))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), SC_OK)));

    var mockAllowedSpResponseFromLendingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("lending-page")))
      .hold(null)
      .recall(null);

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&ecsRequestRouting=true", SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromLendingTenant), SC_OK)));

    var mockAllowedSpResponseFromBorrowingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("borrowing-page")))
      .hold(Set.of(buildAllowedServicePoint("borrowing-hold")))
      .recall(Set.of(buildAllowedServicePoint("borrowing-recall")));

    wireMockServer.stubFor(
      get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
        format("\\?operation=replace&requestId=%s&useStubItem=true", PRIMARY_REQUEST_ID)))
        .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID))
        .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromBorrowingTenant), SC_OK)));

    doGet(ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL)
      .expectStatus().isEqualTo(200)
      .expectBody()
      .jsonPath("Page").doesNotExist()
      .jsonPath("Recall").doesNotExist()
      .jsonPath("Hold[0].name").value(is("borrowing-hold"));

    wireMockServer.verify(getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID)));
  }

  @Test
  void replaceForRequestNotLinkedToItemWhenPrimaryRequestTypeIsNotAllowedInBorrowingTenant() {
    createEcsTlr(false);

    Request secondaryRequest = new Request().id(SECONDARY_REQUEST_ID)
      .requestType(Request.RequestTypeEnum.PAGE);

    wireMockServer.stubFor(get(urlMatching(REQUEST_STORAGE_URL + "/" + SECONDARY_REQUEST_ID))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), SC_OK)));

    var mockAllowedSpResponseFromLendingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("lending-page")))
      .hold(null)
      .recall(null);

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&ecsRequestRouting=true", SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(LENDING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromLendingTenant), SC_OK)));

    var mockAllowedSpResponseFromBorrowingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("borrowing-page")))
      .hold(null)
      .recall(Set.of(buildAllowedServicePoint("borrowing-recall")));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&useStubItem=true", PRIMARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromBorrowingTenant), SC_OK)));

    doGet(ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL)
      .expectStatus().isEqualTo(200)
      .expectBody()
      .jsonPath("Page").doesNotExist()
      .jsonPath("Recall").doesNotExist()
      .jsonPath("Hold").doesNotExist();
  }

  @Test
  void replaceFailsWhenEcsTlrIsNotFound() {
    var mockAllowedSpResponseFromBorrowingTenant = new AllowedServicePointsResponse()
      .page(Set.of(buildAllowedServicePoint("borrowing-page")))
      .hold(Set.of(buildAllowedServicePoint("borrowing-hold")))
      .recall(Set.of(buildAllowedServicePoint("borrowing-recall")));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL +
      String.format("\\?operation=replace&requestId=%s&useStubItem=false", PRIMARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(BORROWING_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(mockAllowedSpResponseFromBorrowingTenant), SC_OK)));

    doGet(ALLOWED_SERVICE_POINTS_FOR_REPLACE_URL)
      .expectStatus().isEqualTo(500);
  }

  @Test
  void allowedSpWithItemLevelReturnsResultSpInResponsesFromDataTenant() {
    var searchItemResponse = new SearchItemResponse();
    searchItemResponse.setTenantId(TENANT_ID_COLLEGE);
    searchItemResponse.setInstanceId(INSTANCE_ID);
    searchItemResponse.setId(ITEM_ID);

    wireMockServer.stubFor(get(urlMatching(SEARCH_ITEM_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(searchItemResponse), SC_OK)));

    var allowedSpResponseConsortium = new AllowedServicePointsResponse();
    allowedSpResponseConsortium.setHold(Set.of(
      buildAllowedServicePoint("SP_consortium_1"),
      buildAllowedServicePoint("SP_consortium_2")));
    allowedSpResponseConsortium.setPage(null);
    allowedSpResponseConsortium.setRecall(Set.of(
      buildAllowedServicePoint("SP_consortium_3")));

    var allowedSpResponseCollege = new AllowedServicePointsResponse();
    allowedSpResponseCollege.setHold(Set.of(
      buildAllowedServicePoint("SP_college_1")));
    allowedSpResponseCollege.setPage(null);
    allowedSpResponseCollege.setRecall(Set.of(
      buildAllowedServicePoint("SP_college_2")));

    User requester = new User().patronGroup(PATRON_GROUP_ID);
    wireMockServer.stubFor(get(urlMatching(USER_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(requester), SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseConsortium), SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseCollege),
        SC_OK)));

    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&itemId=%s",
        REQUESTER_ID, ITEM_ID))
      .expectStatus().isEqualTo(200)
      .expectBody().json(asJsonString(allowedSpResponseConsortium));

    wireMockServer.verify(getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withQueryParam("patronGroupId", equalTo(PATRON_GROUP_ID))
      .withQueryParam("operation", equalTo("create"))
      .withQueryParam("ecsRequestRouting", equalTo("true"))
      .withQueryParam("itemId", equalTo(ITEM_ID)));

    wireMockServer.verify(getRequestedFor(urlMatching(
      ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withQueryParam("patronGroupId", equalTo(PATRON_GROUP_ID))
      .withQueryParam("instanceId", equalTo(INSTANCE_ID))
      .withQueryParam("operation", equalTo("create"))
      .withQueryParam("useStubItem", equalTo("true")));
  }

  private AllowedServicePointsInner buildAllowedServicePoint(String name) {
    return new AllowedServicePointsInner()
      .id(randomId())
      .name(name);
  }

  private EcsTlrEntity createEcsTlr(boolean withItemId) {
    return createEcsTlr(buildEcsTlr(withItemId));
  }

  private EcsTlrEntity createEcsTlr(EcsTlrEntity ecsTlr) {
    return ecsTlrRepository.save(ecsTlr);
  }

  private static EcsTlrEntity buildEcsTlr(boolean withItem) {
    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setId(UUID.fromString(ECS_TLR_ID));
    ecsTlr.setInstanceId(UUID.fromString(INSTANCE_ID));
    ecsTlr.setPrimaryRequestId(UUID.fromString(PRIMARY_REQUEST_ID));
    ecsTlr.setSecondaryRequestId(UUID.fromString(SECONDARY_REQUEST_ID));
    ecsTlr.setPrimaryRequestTenantId(TENANT_ID_CONSORTIUM);
    ecsTlr.setSecondaryRequestTenantId(TENANT_ID_COLLEGE);
    if (withItem) {
      ecsTlr.setItemId(UUID.fromString(ITEM_ID));
    }
    return ecsTlr;
  }
}
