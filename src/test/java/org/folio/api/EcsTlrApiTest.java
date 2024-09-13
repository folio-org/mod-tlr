package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.folio.domain.dto.EcsTlr.RequestTypeEnum.HOLD;
import static org.folio.domain.dto.EcsTlr.RequestTypeEnum.PAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.EcsTlr.RequestTypeEnum;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestInstance;
import org.folio.domain.dto.RequestItem;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItem;
import org.folio.domain.dto.SearchItemStatus;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

class EcsTlrApiTest extends BaseIT {
  private static final String ITEM_ID = randomId();
  private static final String HOLDINGS_RECORD_ID = randomId();
  private static final String INSTANCE_ID = randomId();
  private static final String REQUESTER_ID = randomId();
  private static final String PICKUP_SERVICE_POINT_ID = randomId();
  private static final String PATRON_GROUP_ID_SECONDARY = randomId();
  private static final String PATRON_GROUP_ID_PRIMARY = randomId();
  private static final String REQUESTER_BARCODE = randomId();
  private static final String SECONDARY_REQUEST_ID = randomId();
  private static final String PRIMARY_REQUEST_ID = SECONDARY_REQUEST_ID;

  private static final String UUID_PATTERN =
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
  private static final String TLR_URL = "/tlr/ecs-tlr";
  private static final String INSTANCE_REQUESTS_URL = "/circulation/requests/instances";
  private static final String REQUESTS_URL = "/circulation/requests";
  private static final String USERS_URL = "/users";
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String SEARCH_INSTANCES_URL =
    "/search/instances\\?query=id==" + INSTANCE_ID + "&expandAll=true";
  private static final String ECS_REQUEST_TRANSACTIONS_URL = "/ecs-request-transactions";
  private static final String POST_ECS_REQUEST_TRANSACTION_URL_PATTERN =
    ECS_REQUEST_TRANSACTIONS_URL + "/" + UUID_PATTERN;

  private static final String INSTANCE_TITLE = "Test title";
  private static final String ITEM_BARCODE = "test_item_barcode";
  private static final Date REQUEST_DATE = new Date();
  private static final Date REQUEST_EXPIRATION_DATE = new Date();

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "PAGE, true,  true",
    "PAGE, true,  false",
    "PAGE, false, true",
    "PAGE, false, false",
    "HOLD, true,  true",
    "HOLD, true,  false",
    "HOLD, false, true",
    "HOLD, false, false",
    "RECALL, true,  true",
    "RECALL, true,  false",
    "RECALL, false, true",
    "RECALL, false, false"
  })
  void ecsTlrIsCreated(RequestTypeEnum requestType, boolean secondaryRequestRequesterExists,
    boolean secondaryRequestPickupServicePointExists) {

    EcsTlr ecsTlr = buildEcsTlr(requestType);

    // 1. Create stubs for other modules
    // 1.1 Mock search endpoint

    List<SearchItem> items;
    if (requestType == HOLD) {
      items = List.of(
        buildItem(randomId(), TENANT_ID_UNIVERSITY, "Paged"),
        buildItem(randomId(), TENANT_ID_UNIVERSITY, "Declared lost"),
        buildItem(ITEM_ID, TENANT_ID_COLLEGE, "Checked out"));
    } else {
      items = List.of(
        buildItem(randomId(), TENANT_ID_UNIVERSITY, "Checked out"),
        buildItem(randomId(), TENANT_ID_UNIVERSITY, "In transit"),
        buildItem(ITEM_ID, TENANT_ID_COLLEGE, "Available"));
    }

    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(new SearchInstance()
        .id(INSTANCE_ID)
        .tenantId(TENANT_ID_CONSORTIUM)
        .items(items)
      ));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    // 1.2 Mock user endpoints

    User primaryRequestRequester = buildPrimaryRequestRequester(REQUESTER_ID);
    User secondaryRequestRequester = buildSecondaryRequestRequester(primaryRequestRequester,
      secondaryRequestRequesterExists);

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(primaryRequestRequester, HttpStatus.SC_OK)));

    ResponseDefinitionBuilder mockGetSecondaryRequesterResponse = secondaryRequestRequesterExists
      ? jsonResponse(secondaryRequestRequester, HttpStatus.SC_OK)
      : notFound();

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(mockGetSecondaryRequesterResponse));

    wireMockServer.stubFor(post(urlMatching(USERS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(secondaryRequestRequester, HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(put(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(primaryRequestRequester, HttpStatus.SC_NO_CONTENT)));

    // 1.3 Mock service point endpoints

    ServicePoint primaryRequestPickupServicePoint =
      buildPrimaryRequestPickupServicePoint(PICKUP_SERVICE_POINT_ID);
    ServicePoint secondaryRequestPickupServicePoint =
      buildSecondaryRequestPickupServicePoint(primaryRequestPickupServicePoint);

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(primaryRequestPickupServicePoint), HttpStatus.SC_OK)));

    var mockGetSecondaryRequestPickupServicePointResponse = secondaryRequestPickupServicePointExists
      ? jsonResponse(asJsonString(secondaryRequestPickupServicePoint), HttpStatus.SC_OK)
      : notFound();

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(mockGetSecondaryRequestPickupServicePointResponse));

    wireMockServer.stubFor(post(urlMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(secondaryRequestPickupServicePoint), HttpStatus.SC_CREATED)));

    // 1.4 Mock request endpoints

    Request secondaryRequestPostRequest = buildSecondaryRequest(ecsTlr);
    Request mockPostSecondaryRequestResponse = buildSecondaryRequest(ecsTlr).id(SECONDARY_REQUEST_ID);

    if (requestType != HOLD) {
      mockPostSecondaryRequestResponse
        .itemId(ITEM_ID)
        .holdingsRecordId(HOLDINGS_RECORD_ID)
        .item(new RequestItem().barcode(ITEM_BARCODE))
        .instance(new RequestInstance().title(INSTANCE_TITLE));
    }

    Request primaryRequestPostRequest = buildPrimaryRequest(secondaryRequestPostRequest);
    Request mockPostPrimaryRequestResponse = buildPrimaryRequest(mockPostSecondaryRequestResponse);

    wireMockServer.stubFor(post(urlMatching(INSTANCE_REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(secondaryRequestPostRequest)))
      .willReturn(jsonResponse(asJsonString(mockPostSecondaryRequestResponse), HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(primaryRequestPostRequest)))
      .willReturn(jsonResponse(asJsonString(mockPostPrimaryRequestResponse), HttpStatus.SC_CREATED)));

    // 1.5 Mock DCB endpoints

    DcbTransaction borrowerTransactionPostRequest = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .item(new DcbItem()
        .id(ITEM_ID)
        .barcode(ITEM_BARCODE)
        .title(INSTANCE_TITLE))
      .requestId(PRIMARY_REQUEST_ID);

    DcbTransaction lenderTransactionPostRequest = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.LENDER)
      .requestId(SECONDARY_REQUEST_ID);

    TransactionStatusResponse mockPostEcsDcbTransactionResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CREATED);

    wireMockServer.stubFor(post(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(borrowerTransactionPostRequest)))
      .willReturn(jsonResponse(mockPostEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(lenderTransactionPostRequest)))
      .willReturn(jsonResponse(mockPostEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    // 2. Create ECS TLR

    EcsTlr expectedPostEcsTlrResponse = buildEcsTlr(requestType)
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .primaryRequestTenantId(TENANT_ID_CONSORTIUM)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .secondaryRequestTenantId(TENANT_ID_COLLEGE)
      .itemId(requestType == HOLD ? null : ITEM_ID);

    assertEquals(TENANT_ID_CONSORTIUM, getCurrentTenantId());
    var response = doPostWithTenant(TLR_URL, ecsTlr, TENANT_ID_CONSORTIUM)
      .expectStatus().isCreated()
      .expectBody()
      .jsonPath("$.id").exists()
      .json(asJsonString(expectedPostEcsTlrResponse));
    assertEquals(TENANT_ID_CONSORTIUM, getCurrentTenantId());

    if (requestType != HOLD) {
      response.jsonPath("$.primaryRequestDcbTransactionId").exists()
        .jsonPath("$.secondaryRequestDcbTransactionId").exists();
    }

    // 3. Verify calls to other modules

    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));

    wireMockServer.verify(postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)) // because this tenant has available item
      .withRequestBody(equalToJson(asJsonString(secondaryRequestPostRequest))));

    wireMockServer.verify(postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(primaryRequestPostRequest))));

    if (secondaryRequestRequesterExists) {
      wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(USERS_URL)));
      wireMockServer.verify(exactly(1), putRequestedFor(urlMatching(USERS_URL + "/" + REQUESTER_ID)));
    } else {
      wireMockServer.verify(postRequestedFor(urlMatching(USERS_URL))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(secondaryRequestRequester))));
    }

    if (secondaryRequestPickupServicePointExists) {
      wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(SERVICE_POINTS_URL)));
    } else {
      wireMockServer.verify(postRequestedFor(urlMatching(SERVICE_POINTS_URL))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(secondaryRequestPickupServicePoint))));
    }

    if (requestType != HOLD) {
      wireMockServer.verify(postRequestedFor(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
        .withRequestBody(equalToJson(asJsonString(borrowerTransactionPostRequest))));

      wireMockServer.verify(postRequestedFor(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(lenderTransactionPostRequest))));
    } else {
      wireMockServer.verify(0, postRequestedFor(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN)));
    }
  }

  @Test
  void getByIdNotFound() {
    doGet(TLR_URL + "/" + UUID.randomUUID())
      .expectStatus().isEqualTo(NOT_FOUND);
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToExtractBorrowingTenantIdFromToken() {
    EcsTlr ecsTlr = buildEcsTlr(PAGE, randomId(), randomId());
    doPostWithToken(TLR_URL, ecsTlr, "not_a_token")
      .expectStatus().isEqualTo(500);

    wireMockServer.verify(exactly(0), getRequestedFor(urlMatching(SEARCH_INSTANCES_URL)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToPickLendingTenant() {
    EcsTlr ecsTlr = buildEcsTlr(PAGE, randomId(), randomId());
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(0)
      .instances(List.of());

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR);

    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(USERS_URL)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToFindRequesterInBorrowingTenant() {
    String requesterId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(PAGE, requesterId, randomId());
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new SearchInstance().id(INSTANCE_ID)
          .tenantId(TENANT_ID_CONSORTIUM)
          .items(List.of(buildItem(randomId(), TENANT_ID_UNIVERSITY, "Available")))
      ));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + requesterId))
      .willReturn(notFound()));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR);

    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(REQUESTS_URL)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToFindPickupServicePointInBorrowingTenant() {
    String pickupServicePointId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(PAGE, REQUESTER_ID, pickupServicePointId);
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new SearchInstance().id(INSTANCE_ID)
          .tenantId(TENANT_ID_CONSORTIUM)
          .items(List.of(buildItem(randomId(), TENANT_ID_UNIVERSITY, "Available")))
      ));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .willReturn(jsonResponse(buildPrimaryRequestRequester(REQUESTER_ID), HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .willReturn(notFound()));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR);

    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(REQUESTS_URL)));
  }

  private static EcsTlr buildEcsTlr(RequestTypeEnum requestType) {
    return buildEcsTlr(requestType, REQUESTER_ID, PICKUP_SERVICE_POINT_ID);
  }

  private static EcsTlr buildEcsTlr(RequestTypeEnum requestType, String requesterId,
    String pickupServicePointId) {

    return new EcsTlr()
      .instanceId(INSTANCE_ID)
      .requesterId(requesterId)
      .pickupServicePointId(pickupServicePointId)
      .requestLevel(EcsTlr.RequestLevelEnum.TITLE)
      .requestType(requestType)
      .fulfillmentPreference(EcsTlr.FulfillmentPreferenceEnum.DELIVERY)
      .patronComments("random comment")
      .requestDate(REQUEST_DATE)
      .requestExpirationDate(REQUEST_EXPIRATION_DATE);
  }

  private static Request buildSecondaryRequest(EcsTlr ecsTlr) {
    return new Request()
      .requesterId(ecsTlr.getRequesterId())
      .requestLevel(Request.RequestLevelEnum.fromValue(ecsTlr.getRequestLevel().getValue()))
      .requestType(Request.RequestTypeEnum.fromValue(ecsTlr.getRequestType().getValue()))
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.SECONDARY)
      .instanceId(ecsTlr.getInstanceId())
      .pickupServicePointId(ecsTlr.getPickupServicePointId())
      .requestDate(ecsTlr.getRequestDate())
      .requestExpirationDate(ecsTlr.getRequestExpirationDate())
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.fromValue(ecsTlr.getFulfillmentPreference().getValue()))
      .patronComments(ecsTlr.getPatronComments());
  }

  private static Request buildPrimaryRequest(Request secondaryRequest) {
    return new Request()
      .id(PRIMARY_REQUEST_ID)
      .itemId(secondaryRequest.getItemId())
      .holdingsRecordId(secondaryRequest.getHoldingsRecordId())
      .instanceId(secondaryRequest.getInstanceId())
      .item(secondaryRequest.getItem())
      .instance(secondaryRequest.getInstance())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(secondaryRequest.getPickupServicePointId());
  }

  private static SearchItem buildItem(String id, String tenantId, String status) {
    return new SearchItem()
      .id(id)
      .tenantId(tenantId)
      .status(new SearchItemStatus().name(status));
  }

  private static User buildPrimaryRequestRequester(String userId) {
    return new User()
      .id(userId)
      .username("test_user")
      .patronGroup(PATRON_GROUP_ID_PRIMARY)
      .type("patron")
      .active(true)
      .barcode(REQUESTER_BARCODE)
      .personal(new UserPersonal()
        .firstName("First")
        .middleName("Middle")
        .lastName("Last"));
  }

  private static User buildSecondaryRequestRequester(User primaryRequestRequester,
    boolean secondaryRequestRequesterExists) {
    
    return new User()
      .id(primaryRequestRequester.getId())
      .patronGroup(secondaryRequestRequesterExists ? PATRON_GROUP_ID_SECONDARY : PATRON_GROUP_ID_PRIMARY)
      .type(UserType.SHADOW.getValue())
      .barcode(primaryRequestRequester.getBarcode())
      .active(true);
  }

  private static ServicePoint buildPrimaryRequestPickupServicePoint(String id) {
    return new ServicePoint()
      .id(id)
      .name("Service point")
      .code("TSP")
      .description("Test service point")
      .discoveryDisplayName("Test service point")
      .pickupLocation(true);
  }

  private static ServicePoint buildSecondaryRequestPickupServicePoint(
    ServicePoint primaryRequestPickupServicePoint) {

    return new ServicePoint()
      .id(primaryRequestPickupServicePoint.getId())
      .name("DCB_" + primaryRequestPickupServicePoint.getName())
      .code(primaryRequestPickupServicePoint.getCode())
      .discoveryDisplayName(primaryRequestPickupServicePoint.getDiscoveryDisplayName())
      .pickupLocation(primaryRequestPickupServicePoint.getPickupLocation());
  }

}
