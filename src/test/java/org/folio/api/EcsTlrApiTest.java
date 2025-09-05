package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.folio.domain.dto.EcsTlr.RequestTypeEnum.HOLD;
import static org.folio.domain.dto.EcsTlr.RequestTypeEnum.PAGE;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.INTERMEDIATE;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.EcsTlr.RequestTypeEnum;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestInstance;
import org.folio.domain.dto.RequestItem;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItem;
import org.folio.domain.dto.SearchItemStatus;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.SharingInstance;
import org.folio.domain.dto.Status;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.folio.domain.entity.TlrSettingsEntity;
import org.folio.repository.TlrSettingsRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

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
  private static final String INTERMEDIATE_REQUEST_ID = SECONDARY_REQUEST_ID;
  private static final String CONSORTIUM_ID = "23085034-7e68-4927-9d17-1de20a06a512";

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
  private static final String SHARE_INSTANCE_URL = "/consortia/" + CONSORTIUM_ID + "/sharing/instances";
  private static final String INSTANCES_URL = "/instance-storage/instances";

  private static final String INSTANCE_TITLE = "Test title";
  private static final String ITEM_BARCODE = "test_item_barcode";
  private static final Date REQUEST_DATE = new Date();
  private static final Date REQUEST_EXPIRATION_DATE = new Date();
  private static final String TLR_SETTINGS_ID = "b937cf15-da8b-4480-aa87-4bc32f17219b";

  @Autowired
  private TlrSettingsRepository tlrSettingsRepository;

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
    tlrSettingsRepository.deleteAll();
  }

  private void setupTlrSettings(List<String> excludeTenants) {
    TlrSettingsEntity tlrSettings = TlrSettingsEntity.builder()
      .id(UUID.fromString(TLR_SETTINGS_ID))
      .ecsTlrFeatureEnabled(true)
      .excludeFromEcsRequestLendingTenantSearch(excludeTenants)
      .build();
    tlrSettingsRepository.save(tlrSettings);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "PAGE, true,  true, TITLE,",
    "PAGE, true,  false, TITLE,",
    "PAGE, false, true, TITLE,",
    "PAGE, false, false, TITLE,",
    "HOLD, true,  true, TITLE,",
    "HOLD, true,  false, TITLE,",
    "HOLD, false, true, TITLE,",
    "HOLD, false, false, TITLE,",
    "RECALL, true,  true, TITLE,",
    "RECALL, true,  false, TITLE,",
    "RECALL, false, true, TITLE,",
    "RECALL, false, false, TITLE,",
    "PAGE, true,  true, ITEM,",
    "PAGE, true,  false, ITEM,",
    "PAGE, false, true, ITEM,",
    "PAGE, false, false, ITEM,",
    "HOLD, true,  true, ITEM,",
    "HOLD, true,  false, ITEM,",
    "HOLD, false, true, ITEM,",
    "HOLD, false, false, ITEM,",
    "RECALL, true,  true, ITEM,",
    "RECALL, true,  false, ITEM,",
    "RECALL, false, true, ITEM,",
    "RECALL, false, false, ITEM,",
    "PAGE, true, true, TITLE, ''",
    "PAGE, true, true, TITLE, null",
    "PAGE, true, true, TITLE, university"
  })
  void ecsTlrIsCreated(RequestTypeEnum requestType, boolean requesterClonesExist,
    boolean pickupServicePointClonesExist, EcsTlr.RequestLevelEnum requestLevel,
    String excludeTenants) {

    setupTlrSettings(excludeTenants == null || excludeTenants.isEmpty() || "null".equals(excludeTenants)
      ? null
      : List.of(excludeTenants.split(",?\s*")));

    EcsTlr ecsTlr = buildEcsTlr(requestType, requestLevel)
      .id(randomId())
      .primaryRequestTenantId(TENANT_ID_UNIVERSITY);

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
    User requesterClone = buildRequesterClone(primaryRequestRequester,
      requesterClonesExist);

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .willReturn(jsonResponse(primaryRequestRequester, HttpStatus.SC_OK)));

    ResponseDefinitionBuilder mockGetClonedRequesterResponse = requesterClonesExist
      ? jsonResponse(requesterClone, HttpStatus.SC_OK)
      : notFound();

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(mockGetClonedRequesterResponse));

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(mockGetClonedRequesterResponse));

    wireMockServer.stubFor(post(urlMatching(USERS_URL))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_UNIVERSITY)))
      .willReturn(jsonResponse(requesterClone, HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(put(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_UNIVERSITY)))
      .willReturn(jsonResponse(primaryRequestRequester, HttpStatus.SC_NO_CONTENT)));

    // 1.3 Mock service point endpoints

    ServicePoint primaryRequestPickupServicePoint =
      buildPrimaryRequestPickupServicePoint(PICKUP_SERVICE_POINT_ID);
    ServicePoint servicePointClone = buildPickupServicePointClone(primaryRequestPickupServicePoint);

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .willReturn(jsonResponse(asJsonString(primaryRequestPickupServicePoint), HttpStatus.SC_OK)));

    var mockGetClonedPickupServicePointResponse = pickupServicePointClonesExist
      ? jsonResponse(asJsonString(servicePointClone), HttpStatus.SC_OK)
      : notFound();

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_UNIVERSITY)))
      .willReturn(mockGetClonedPickupServicePointResponse));

    wireMockServer.stubFor(post(urlMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_UNIVERSITY)))
      .willReturn(jsonResponse(asJsonString(servicePointClone), HttpStatus.SC_CREATED)));

    // 1.4 Mock request endpoints

    Request secondaryRequestPostRequest = buildSecondaryRequest(ecsTlr);
    Request mockPostSecondaryRequestResponse = buildSecondaryRequest(ecsTlr)
      .id(SECONDARY_REQUEST_ID)
      .itemId(ITEM_ID)
      .holdingsRecordId(HOLDINGS_RECORD_ID)
      .item(new RequestItem().barcode(ITEM_BARCODE))
      .instance(new RequestInstance().title(INSTANCE_TITLE));

    Request primaryRequestPostRequest = buildRequest(secondaryRequestPostRequest, PRIMARY);
    Request mockPostPrimaryRequestResponse = buildRequest(mockPostSecondaryRequestResponse, PRIMARY);

    Request intermediateRequestPostRequest = buildRequest(secondaryRequestPostRequest, INTERMEDIATE);
    Request mockPostIntermediateRequestResponse = buildRequest(mockPostSecondaryRequestResponse, INTERMEDIATE);

    wireMockServer.stubFor(post(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(secondaryRequestPostRequest)))
      .willReturn(jsonResponse(asJsonString(mockPostSecondaryRequestResponse), HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .withRequestBody(equalToJson(asJsonString(primaryRequestPostRequest)))
      .willReturn(jsonResponse(asJsonString(mockPostPrimaryRequestResponse), HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(intermediateRequestPostRequest)))
      .willReturn(jsonResponse(asJsonString(mockPostIntermediateRequestResponse), HttpStatus.SC_CREATED)));

    // 1.5 Mock DCB endpoints

    DcbTransaction pickupTransactionPostRequest = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.PICKUP)
      .item(new DcbItem()
        .id(ITEM_ID)
        .barcode(ITEM_BARCODE)
        .title(INSTANCE_TITLE))
      .requestId(PRIMARY_REQUEST_ID);

    DcbTransaction borrowerTransactionPostRequest = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .item(new DcbItem()
        .id(ITEM_ID)
        .barcode(ITEM_BARCODE)
        .title(INSTANCE_TITLE))
      .requestId(INTERMEDIATE_REQUEST_ID);

    DcbTransaction lenderTransactionPostRequest = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.LENDER)
      .requestId(SECONDARY_REQUEST_ID);

    TransactionStatusResponse mockPostEcsDcbTransactionResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CREATED);

    wireMockServer.stubFor(post(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .withRequestBody(equalToJson(asJsonString(pickupTransactionPostRequest)))
      .willReturn(jsonResponse(mockPostEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(borrowerTransactionPostRequest)))
      .willReturn(jsonResponse(mockPostEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(lenderTransactionPostRequest)))
      .willReturn(jsonResponse(mockPostEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    // 1.6 Mock circulation item endpoints

    wireMockServer.stubFor(get(urlMatching("/circulation-item/" + ITEM_ID))
      .willReturn(notFound()));

    Item mockItem = new Item()
      .id(ITEM_ID)
      .status(requestType == HOLD
        ? new ItemStatus(ItemStatus.NameEnum.CHECKED_OUT)
        : new ItemStatus(ItemStatus.NameEnum.AVAILABLE));

    wireMockServer.stubFor(get(urlMatching("/item-storage/items.*"))
      .willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(asJsonString(mockItem))
        .withStatus(HttpStatus.SC_OK)));

    Instance mockInventoryInstance = new Instance().title(INSTANCE_TITLE);
    // Instance already exists in central and secondary tenant
    wireMockServer.stubFor(get(urlMatching(INSTANCES_URL + "/" + INSTANCE_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(mockInventoryInstance, HttpStatus.SC_OK)));
    wireMockServer.stubFor(get(urlMatching(INSTANCES_URL + "/" + INSTANCE_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(mockInventoryInstance, HttpStatus.SC_OK)));

    // Instance is found in primary tenant only after its shadow is created (see consortia stubs below)
    wireMockServer.stubFor(get(urlMatching(INSTANCES_URL + "/" + INSTANCE_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .inScenario("Create ECS TLR")
      .whenScenarioStateIs(Scenario.STARTED)
      .willReturn(notFound()));
    wireMockServer.stubFor(get(urlMatching(INSTANCES_URL + "/" + INSTANCE_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .inScenario("Create ECS TLR")
      .whenScenarioStateIs("Shadow instance created")
      .willReturn(jsonResponse(asJsonString(mockInventoryInstance), HttpStatus.SC_OK)));

    wireMockServer.stubFor(post(urlMatching("/circulation-item.*"))
      .willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(asJsonString(mockItem))
        .withStatus(HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(put(urlMatching("/circulation-item.*"))
      .willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(asJsonString(mockItem))
        .withStatus(HttpStatus.SC_OK)));

    // 1.7 Mock consortia endpoints

    SharingInstance mockShareInstanceResponse = new SharingInstance()
      .id(UUID.randomUUID())
      .instanceIdentifier(UUID.fromString(INSTANCE_ID))
      .sourceTenantId(TENANT_ID_CONSORTIUM)
      .targetTenantId(TENANT_ID_UNIVERSITY)
      .status(Status.COMPLETE);

    wireMockServer.stubFor(post(urlMatching(SHARE_INSTANCE_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .inScenario("Create ECS TLR")
      .whenScenarioStateIs(Scenario.STARTED)
      .willSetStateTo("Shadow instance created")
      .willReturn(jsonResponse(asJsonString(mockShareInstanceResponse), HttpStatus.SC_CREATED)));

    // 2. Create ECS TLR

    EcsTlr expectedPostEcsTlrResponse = buildEcsTlr(requestType, requestLevel)
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .primaryRequestTenantId(TENANT_ID_UNIVERSITY)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .secondaryRequestTenantId(TENANT_ID_COLLEGE)
      .intermediateRequestId(INTERMEDIATE_REQUEST_ID)
      .intermediateRequestTenantId(TENANT_ID_CONSORTIUM)
      .itemId(requestType == HOLD ? null : ITEM_ID);

    assertEquals(TENANT_ID_CONSORTIUM, getCurrentTenantId());
    var response = doPostWithTenant(TLR_URL, ecsTlr, TENANT_ID_CONSORTIUM)
      .expectStatus().isCreated()
      .expectBody()
      .jsonPath("$.id").exists()
      .jsonPath("$.id").value(not(Matchers.equalTo(ecsTlr.getId())))
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

    wireMockServer.verify(postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(secondaryRequestPostRequest))));

    wireMockServer.verify(postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(intermediateRequestPostRequest))));

    wireMockServer.verify(postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .withRequestBody(equalToJson(asJsonString(primaryRequestPostRequest))));

    if (requesterClonesExist) {
      wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(USERS_URL)));
      wireMockServer.verify(exactly(2), putRequestedFor(urlMatching(USERS_URL + "/" + REQUESTER_ID)));
    } else {
      wireMockServer.verify(postRequestedFor(urlMatching(USERS_URL))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(requesterClone))));
    }

    if (pickupServicePointClonesExist) {
      wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(SERVICE_POINTS_URL)));
    } else {
      wireMockServer.verify(postRequestedFor(urlMatching(SERVICE_POINTS_URL))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(servicePointClone))));
      wireMockServer.verify(postRequestedFor(urlMatching(SERVICE_POINTS_URL))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
        .withRequestBody(equalToJson(asJsonString(servicePointClone))));
    }

    wireMockServer.verify(postRequestedFor(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(borrowerTransactionPostRequest))));

    wireMockServer.verify(postRequestedFor(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .withRequestBody(equalToJson(asJsonString(lenderTransactionPostRequest))));

    wireMockServer.verify(postRequestedFor(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .withRequestBody(equalToJson(asJsonString(pickupTransactionPostRequest))));

    wireMockServer.verify(getRequestedFor(urlMatching(INSTANCES_URL + "/" + INSTANCE_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));

    wireMockServer.verify(postRequestedFor(urlMatching(SHARE_INSTANCE_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .withRequestBody(equalToJson(asJsonString(
        new SharingInstance()
          .instanceIdentifier(UUID.fromString(INSTANCE_ID))
          .sourceTenantId(TENANT_ID_CONSORTIUM)
          .targetTenantId(TENANT_ID_UNIVERSITY)
      ))));
  }

  @Test
  void getByIdNotFound() {
    doGet(TLR_URL + "/" + UUID.randomUUID())
      .expectStatus().isEqualTo(NOT_FOUND);
  }

  @ParameterizedTest
  @EnumSource(EcsTlr.RequestLevelEnum.class)
  void canNotCreateEcsTlrWhenFailedToPickLendingTenant(EcsTlr.RequestLevelEnum requestLevel) {
    EcsTlr ecsTlr = buildEcsTlr(PAGE, randomId(), randomId(), requestLevel);
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(0)
      .instances(List.of());

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR);

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(USERS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(SEARCH_INSTANCES_URL)));
  }

  @ParameterizedTest
  @EnumSource(EcsTlr.RequestLevelEnum.class)
  void canNotCreateEcsTlrWhenFailedToFindRequesterInBorrowingTenant(
    EcsTlr.RequestLevelEnum requestLevel) {

    String requesterId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(PAGE, requesterId, randomId(), requestLevel);
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

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(SEARCH_INSTANCES_URL)));
  }

  @ParameterizedTest
  @EnumSource(EcsTlr.RequestLevelEnum.class)
  void canNotCreateEcsTlrWhenFailedToFindPickupServicePointInBorrowingTenant(
    EcsTlr.RequestLevelEnum requestLevel) {

    String pickupServicePointId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(PAGE, REQUESTER_ID, pickupServicePointId, requestLevel);
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

  @ParameterizedTest
  @EnumSource(EcsTlr.RequestLevelEnum.class)
  void canNotPlaceEcsTlrForInactivePatron(EcsTlr.RequestLevelEnum requestLevel) {

    EcsTlr ecsTlr = buildEcsTlr(PAGE, REQUESTER_ID, randomId(), requestLevel);
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new SearchInstance().id(INSTANCE_ID)
          .tenantId(TENANT_ID_CONSORTIUM)
          .items(List.of(buildItem(randomId(), TENANT_ID_UNIVERSITY, "Available")))
      ));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    User primaryRequestRequester = buildPrimaryRequestRequester(REQUESTER_ID, false);

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(primaryRequestRequester, HttpStatus.SC_OK)));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY)
      .expectBody()
      .jsonPath("$.errors[0].code").isEqualTo("ECS_REQUEST_CANNOT_BE_PLACED_FOR_INACTIVE_PATRON");

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(SEARCH_INSTANCES_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(REQUESTS_URL)));
  }

  @Test
  void ecsTlrExcludesAllLendingTenantsFromSecondaryRequests() {
    String excludedTenants = TENANT_ID_COLLEGE + "," + TENANT_ID_UNIVERSITY;
    setupTlrSettings(List.of(TENANT_ID_COLLEGE, TENANT_ID_UNIVERSITY));

    // Build ECS TLR
    EcsTlr ecsTlr = buildEcsTlr(PAGE, REQUESTER_ID, PICKUP_SERVICE_POINT_ID, EcsTlr.RequestLevelEnum.TITLE)
      .id(randomId())
      .primaryRequestTenantId(TENANT_ID_CONSORTIUM);

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + REQUESTER_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(buildPrimaryRequestRequester(REQUESTER_ID), HttpStatus.SC_OK)));

    List<SearchItem> items = List.of(
      buildItem(randomId(), TENANT_ID_UNIVERSITY, "Available"),
      buildItem(randomId(), TENANT_ID_COLLEGE, "Available"));
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(new SearchInstance()
        .id(INSTANCE_ID)
        .tenantId(TENANT_ID_CONSORTIUM)
        .items(items)
      ));
    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    ServicePoint primaryRequestPickupServicePoint = buildPrimaryRequestPickupServicePoint(PICKUP_SERVICE_POINT_ID);
    ServicePoint secondaryRequestServicePoint = buildPickupServicePointClone(primaryRequestPickupServicePoint);

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(secondaryRequestServicePoint), HttpStatus.SC_OK)));
    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(primaryRequestPickupServicePoint), HttpStatus.SC_OK)));

    // Post ECS TLR
    doPostWithTenant(TLR_URL, ecsTlr, TENANT_ID_CONSORTIUM)
      .expectStatus().is5xxServerError();

    // Assert excluded tenants do NOT receive secondary requests
    wireMockServer.verify(0, postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));
    wireMockServer.verify(0, postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));
    // Since all lending tenants are excluded, no secondary requests should be made at all
    wireMockServer.verify(0, postRequestedFor(urlMatching(REQUESTS_URL)));
  }

  private static EcsTlr buildEcsTlr(RequestTypeEnum requestType, EcsTlr.RequestLevelEnum requestLevel) {
    return buildEcsTlr(requestType, REQUESTER_ID, PICKUP_SERVICE_POINT_ID, requestLevel);
  }

  private static EcsTlr buildEcsTlr(RequestTypeEnum requestType, String requesterId,
    String pickupServicePointId, EcsTlr.RequestLevelEnum requestLevel) {

    return new EcsTlr()
      .instanceId(INSTANCE_ID)
      .requesterId(requesterId)
      .pickupServicePointId(pickupServicePointId)
      .requestLevel(requestLevel)
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

  private static Request buildRequest(Request secondaryRequest,
    Request.EcsRequestPhaseEnum ecsRequestPhase) {

    return new Request()
      .id(PRIMARY_REQUEST_ID)
      .itemId(ITEM_ID)
      .holdingsRecordId(HOLDINGS_RECORD_ID)
      .instanceId(secondaryRequest.getInstanceId())
      .item(secondaryRequest.getItem())
      .instance(secondaryRequest.getInstance())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestLevel(secondaryRequest.getRequestLevel())
      .requestType(secondaryRequest.getRequestType())
      .ecsRequestPhase(ecsRequestPhase)
      .fulfillmentPreference(secondaryRequest.getFulfillmentPreference())
      .pickupServicePointId(secondaryRequest.getPickupServicePointId())
      .patronComments(secondaryRequest.getPatronComments());
  }

  private static SearchItem buildItem(String id, String tenantId, String status) {
    return new SearchItem()
      .id(id)
      .tenantId(tenantId)
      .status(new SearchItemStatus().name(status));
  }

  private static User buildPrimaryRequestRequester(String userId) {
    return buildPrimaryRequestRequester(userId, true);
  }

  private static User buildPrimaryRequestRequester(String userId, boolean active) {
    return new User()
      .id(userId)
      .username("test_user")
      .patronGroup(PATRON_GROUP_ID_PRIMARY)
      .type("patron")
      .active(active)
      .barcode(REQUESTER_BARCODE)
      .personal(new UserPersonal()
        .firstName("First")
        .middleName("Middle")
        .lastName("Last"))
      .customFields(null);
  }

  private static User buildRequesterClone(User primaryRequestRequester,
    boolean secondaryRequestRequesterExists) {

    return new User()
      .id(primaryRequestRequester.getId())
      .patronGroup(secondaryRequestRequesterExists ? PATRON_GROUP_ID_SECONDARY : PATRON_GROUP_ID_PRIMARY)
      .type(UserType.SHADOW.getValue())
      .barcode(primaryRequestRequester.getBarcode())
      .active(true)
      .customFields(null);
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

  private static ServicePoint buildPickupServicePointClone(
    ServicePoint primaryRequestPickupServicePoint) {

    return new ServicePoint()
      .id(primaryRequestPickupServicePoint.getId())
      .name("DCB_" + primaryRequestPickupServicePoint.getName())
      .code(primaryRequestPickupServicePoint.getCode())
      .discoveryDisplayName(primaryRequestPickupServicePoint.getDiscoveryDisplayName())
      .pickupLocation(primaryRequestPickupServicePoint.getPickupLocation());
  }

}
