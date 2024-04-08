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
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

class EcsTlrApiTest extends BaseIT {
  private static final String TLR_URL = "/tlr/ecs-tlr";
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final String INSTANCE_ID = randomId();
  private static final String INSTANCE_REQUESTS_URL = "/circulation/requests/instances";
  private static final String REQUESTS_URL = "/circulation/requests";
  private static final String USERS_URL = "/users";
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String SEARCH_INSTANCES_URL =
    "/search/instances\\?query=id==" + INSTANCE_ID + "&expandAll=true";

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
  }

  @Test
  void getByIdNotFound() {
    doGet(TLR_URL + "/" + UUID.randomUUID())
      .expectStatus().isEqualTo(NOT_FOUND);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "true, true",
    "true, false",
    "false, true",
    "false, false"
  })
  void ecsTlrIsCreated(boolean secondaryRequestRequesterExists,
    boolean secondaryRequestPickupServicePointExists) {

    String availableItemId = randomId();
    String requesterId = randomId();
    String pickupServicePointId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID, requesterId, pickupServicePointId);
    String ecsTlrJson = asJsonString(ecsTlr);

    // 1. Create mock responses from other modules

    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new Instance().id(INSTANCE_ID)
          .tenantId(TENANT_ID_CONSORTIUM)
          .items(List.of(
            buildItem(randomId(), TENANT_ID_UNIVERSITY, "Checked out"),
            buildItem(randomId(), TENANT_ID_UNIVERSITY, "In transit"),
            buildItem(availableItemId, TENANT_ID_COLLEGE, "Available")))
      ));

    Request mockSecondaryRequestResponse = new Request()
      .id(randomId())
      .requesterId(requesterId)
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.PAGE)
      .instanceId(INSTANCE_ID)
      .itemId(availableItemId)
      .pickupServicePointId(pickupServicePointId);

    Request mockPrimaryRequestResponse = new Request()
      .id(mockSecondaryRequestResponse.getId())
      .instanceId(INSTANCE_ID)
      .requesterId(requesterId)
      .requestDate(mockSecondaryRequestResponse.getRequestDate())
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(mockSecondaryRequestResponse.getPickupServicePointId());

    User primaryRequestRequester = buildPrimaryRequestRequester(requesterId);
    User secondaryRequestRequester = buildSecondaryRequestRequester(primaryRequestRequester);
    ServicePoint primaryRequestPickupServicePoint =
      buildPrimaryRequestPickupServicePoint(pickupServicePointId);
    ServicePoint secondaryRequestPickupServicePoint =
      buildSecondaryRequestPickupServicePoint(primaryRequestPickupServicePoint);

    // 2. Create stubs for other modules
    // 2.1 Mock search endpoint

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    // 2.2 Mock user endpoints

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(primaryRequestRequester, HttpStatus.SC_OK)));

    ResponseDefinitionBuilder mockGetSecondaryRequesterResponse = secondaryRequestRequesterExists
      ? jsonResponse(secondaryRequestRequester, HttpStatus.SC_OK)
      : notFound();

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
      .willReturn(mockGetSecondaryRequesterResponse));

    wireMockServer.stubFor(post(urlMatching(USERS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(secondaryRequestRequester, HttpStatus.SC_CREATED)));

    // 2.3 Mock service point endpoints

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(primaryRequestPickupServicePoint), HttpStatus.SC_OK)));

    var mockGetSecondaryRequestPickupServicePointResponse = secondaryRequestPickupServicePointExists
      ? jsonResponse(asJsonString(secondaryRequestPickupServicePoint), HttpStatus.SC_OK)
      : notFound();

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
      .willReturn(mockGetSecondaryRequestPickupServicePointResponse));

    wireMockServer.stubFor(post(urlMatching(SERVICE_POINTS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(secondaryRequestPickupServicePoint), HttpStatus.SC_CREATED)));

    // 2.4 Mock request endpoints

    wireMockServer.stubFor(post(urlMatching(INSTANCE_REQUESTS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(mockSecondaryRequestResponse), HttpStatus.SC_CREATED)));

    wireMockServer.stubFor(post(urlMatching(REQUESTS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(mockPrimaryRequestResponse), HttpStatus.SC_CREATED)));

    // 3. Create ECS TLR

    EcsTlr expectedPostEcsTlrResponse = fromJsonString(ecsTlrJson, EcsTlr.class)
      .primaryRequestId(mockPrimaryRequestResponse.getId())
      .primaryRequestTenantId(TENANT_ID_CONSORTIUM)
      .secondaryRequestId(mockSecondaryRequestResponse.getId())
      .secondaryRequestTenantId(TENANT_ID_COLLEGE)
      .itemId(availableItemId);

    assertEquals(TENANT_ID_CONSORTIUM, getCurrentTenantId());
    doPostWithTenant(TLR_URL, ecsTlr, TENANT_ID_CONSORTIUM)
      .expectStatus().isCreated()
      .expectBody().json(asJsonString(expectedPostEcsTlrResponse), true);
    assertEquals(TENANT_ID_CONSORTIUM, getCurrentTenantId());

    // 4. Verify calls to other modules
    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE)));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE)));

    wireMockServer.verify(postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE)) // because this tenant has available item
      .withRequestBody(equalToJson(ecsTlrJson)));

    wireMockServer.verify(postRequestedFor(urlMatching(REQUESTS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(mockPrimaryRequestResponse))));

    if (secondaryRequestRequesterExists) {
      wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(USERS_URL)));
    } else {
      wireMockServer.verify(postRequestedFor(urlMatching(USERS_URL))
        .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(secondaryRequestRequester))));
    }

    if (secondaryRequestPickupServicePointExists) {
      wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(SERVICE_POINTS_URL)));
    } else {
      wireMockServer.verify(postRequestedFor(urlMatching(SERVICE_POINTS_URL))
        .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(secondaryRequestPickupServicePoint))));
    }
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToExtractBorrowingTenantIdFromToken() {
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID, randomId(), randomId());
    doPostWithToken(TLR_URL, ecsTlr, "not_a_token")
      .expectStatus().isEqualTo(500);

    wireMockServer.verify(exactly(0), getRequestedFor(urlMatching(SEARCH_INSTANCES_URL)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToPickLendingTenant() {
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID, randomId(), randomId());
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(0)
      .instances(List.of());

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR);

    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(USERS_URL)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToFindRequesterInBorrowingTenant() {
    String requesterId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID, requesterId, randomId());
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new Instance().id(INSTANCE_ID)
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
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(REQUESTS_URL)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToFindPickupServicePointInBorrowingTenant() {
    String requesterId = randomId();
    String pickupServicePointId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID, requesterId, pickupServicePointId);
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new Instance().id(INSTANCE_ID)
          .tenantId(TENANT_ID_CONSORTIUM)
          .items(List.of(buildItem(randomId(), TENANT_ID_UNIVERSITY, "Available")))
      ));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(USERS_URL + "/" + requesterId))
      .willReturn(jsonResponse(buildPrimaryRequestRequester(requesterId), HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .willReturn(notFound()));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR);

    wireMockServer.verify(getRequestedFor(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(USERS_URL + "/" + requesterId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + pickupServicePointId))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(INSTANCE_REQUESTS_URL)));
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(REQUESTS_URL)));
  }

  private static EcsTlr buildEcsTlr(String instanceId, String requesterId,
    String pickupServicePointId) {

    return new EcsTlr()
      .id(randomId())
      .instanceId(instanceId)
      .requesterId(requesterId)
      .pickupServicePointId(pickupServicePointId)
      .requestLevel(EcsTlr.RequestLevelEnum.TITLE)
      .requestType(EcsTlr.RequestTypeEnum.HOLD)
      .fulfillmentPreference(EcsTlr.FulfillmentPreferenceEnum.DELIVERY)
      .patronComments("random comment")
      .requestDate(new Date())
      .requestExpirationDate(new Date());
  }

  private static Item buildItem(String id, String tenantId, String status) {
    return new Item()
      .id(id)
      .tenantId(tenantId)
      .status(new ItemStatus().name(status));
  }

  private static User buildPrimaryRequestRequester(String userId) {
    return new User()
      .id(userId)
      .username("test_user")
      .patronGroup(randomId())
      .type("patron")
      .active(true)
      .personal(new UserPersonal()
        .firstName("First")
        .middleName("Middle")
        .lastName("Last"));
  }

  private static User buildSecondaryRequestRequester(User primaryRequestRequester) {
    User secondaryRequestRequester = new User()
      .id(primaryRequestRequester.getId())
      .username(primaryRequestRequester.getUsername())
      .patronGroup(primaryRequestRequester.getPatronGroup())
      .type(UserType.SHADOW.getValue())
      .active(true);

    UserPersonal personal = primaryRequestRequester.getPersonal();
    if (personal != null) {
      secondaryRequestRequester.setPersonal(new UserPersonal()
        .firstName(personal.getFirstName())
        .lastName(personal.getLastName())
      );
    }

    return secondaryRequestRequester;
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
