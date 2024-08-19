package org.folio.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.domain.dto.Request.StatusEnum.CLOSED_CANCELLED;
import static org.folio.domain.dto.Request.StatusEnum.OPEN_IN_TRANSIT;
import static org.folio.domain.dto.Request.StatusEnum.OPEN_NOT_YET_FILLED;
import static org.folio.support.KafkaEvent.EventType.CREATED;
import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.folio.util.TestUtils.buildEvent;
import static org.folio.util.TestUtils.mockConsortiaTenants;
import static org.folio.util.TestUtils.mockUserTenants;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.awaitility.Awaitility;
import org.folio.api.BaseIT;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestInstance;
import org.folio.domain.dto.RequestItem;
import org.folio.domain.dto.RequestRequester;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.client.WireMock;

import lombok.SneakyThrows;

class KafkaEventListenerTest extends BaseIT {
  private static final String UUID_PATTERN =
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
  private static final String ECS_REQUEST_TRANSACTIONS_URL = "/ecs-request-transactions";
  private static final String POST_ECS_REQUEST_TRANSACTION_URL_PATTERN =
    ECS_REQUEST_TRANSACTIONS_URL + "/" + UUID_PATTERN;
  private static final String DCB_TRANSACTION_STATUS_URL_PATTERN = "/transactions/%s/status";
  private static final String DCB_TRANSACTIONS_URL_PATTERN =
    format(DCB_TRANSACTION_STATUS_URL_PATTERN, UUID_PATTERN);
  private static final String USER_GROUPS_URL_PATTERN = "/groups";
  private static final String REQUEST_STORAGE_URL_PATTERN = ".*/request-storage/requests/%s";
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String CONSUMER_GROUP_ID = "folio-mod-tlr-group";

  private static final UUID INSTANCE_ID = randomUUID();
  private static final UUID HOLDINGS_ID = randomUUID();
  private static final UUID ITEM_ID = randomUUID();
  private static final UUID REQUESTER_ID = randomUUID();
  private static final UUID PICKUP_SERVICE_POINT_ID = randomUUID();
  private static final UUID ECS_TLR_ID = randomUUID();
  private static final UUID PRIMARY_REQUEST_ID = ECS_TLR_ID;
  private static final UUID SECONDARY_REQUEST_ID = ECS_TLR_ID;
  private static final UUID PRIMARY_REQUEST_DCB_TRANSACTION_ID = randomUUID();
  private static final UUID SECONDARY_REQUEST_DCB_TRANSACTION_ID = randomUUID();
  private static final Date REQUEST_EXPIRATION_DATE = new Date();
  private static final String PRIMARY_REQUEST_TENANT_ID = TENANT_ID_CONSORTIUM;
  private static final String SECONDARY_REQUEST_TENANT_ID = TENANT_ID_COLLEGE;
  private static final String CENTRAL_TENANT_ID = TENANT_ID_CONSORTIUM;
  private static final UUID CONSORTIUM_ID = randomUUID();

  @Autowired
  private EcsTlrRepository ecsTlrRepository;
  @Autowired
  private SystemUserScopedExecutionService executionService;

  @BeforeEach
  void beforeEach() {
    ecsTlrRepository.deleteAll();
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN_NOT_YET_FILLED, OPEN_IN_TRANSIT, CREATED, OPEN",
    "OPEN_IN_TRANSIT, OPEN_AWAITING_PICKUP, OPEN, AWAITING_PICKUP",
    "OPEN_AWAITING_PICKUP, CLOSED_FILLED, AWAITING_PICKUP, ITEM_CHECKED_OUT",
  })
  void shouldCreateAndUpdateDcbTransactionsUponSecondaryRequestUpdateWhenEcsTlrHasNoItemId(
    Request.StatusEnum oldRequestStatus, Request.StatusEnum newRequestStatus,
    TransactionStatusResponse.StatusEnum oldTransactionStatus,
    TransactionStatusResponse.StatusEnum expectedNewTransactionStatus) {

    mockDcb(oldTransactionStatus, expectedNewTransactionStatus);

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithoutItemId());
    assertNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildSecondaryRequestUpdateEvent(oldRequestStatus, newRequestStatus);
    publishEventAndWait(SECONDARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    EcsTlrEntity updatedEcsTlr = getEcsTlr(initialEcsTlr.getId());
    assertEquals(ITEM_ID, updatedEcsTlr.getItemId());

    UUID secondaryRequestTransactionId = updatedEcsTlr.getSecondaryRequestDcbTransactionId();
    verifyThatDcbTransactionsWereCreated(updatedEcsTlr);
    verifyThatDcbTransactionStatusWasRetrieved(secondaryRequestTransactionId,
      SECONDARY_REQUEST_TENANT_ID);
    verifyThatDcbTransactionWasUpdated(secondaryRequestTransactionId,
      SECONDARY_REQUEST_TENANT_ID, expectedNewTransactionStatus);
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN_NOT_YET_FILLED, OPEN_IN_TRANSIT, CREATED, OPEN",
    "OPEN_IN_TRANSIT, OPEN_AWAITING_PICKUP, OPEN, AWAITING_PICKUP",
    "OPEN_AWAITING_PICKUP, CLOSED_FILLED, AWAITING_PICKUP, ITEM_CHECKED_OUT"
  })
  void shouldUpdateLendingDcbTransactionUponSecondaryRequestUpdateWhenEcsTlrAlreadyHasItemId(
    Request.StatusEnum oldRequestStatus, Request.StatusEnum newRequestStatus,
    TransactionStatusResponse.StatusEnum oldTransactionStatus,
    TransactionStatusResponse.StatusEnum expectedNewTransactionStatus) {

    mockDcb(oldTransactionStatus, expectedNewTransactionStatus);

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithItemId());
    assertNotNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildSecondaryRequestUpdateEvent(oldRequestStatus, newRequestStatus);
    publishEventAndWait(SECONDARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    EcsTlrEntity updatedEcsTlr = getEcsTlr(initialEcsTlr.getId());
    UUID transactionId = updatedEcsTlr.getSecondaryRequestDcbTransactionId();
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasRetrieved(transactionId, SECONDARY_REQUEST_TENANT_ID);
    verifyThatDcbTransactionWasUpdated(transactionId,
      SECONDARY_REQUEST_TENANT_ID, expectedNewTransactionStatus);
  }

  @Test
  void shouldCancelLendingDcbTransactionUponPrimaryRequestCancel() {

    mockDcb(TransactionStatusResponse.StatusEnum.CREATED, TransactionStatusResponse.StatusEnum.CANCELLED);
    Request secondaryRequest = buildSecondaryRequest(OPEN_NOT_YET_FILLED);

    wireMockServer.stubFor(WireMock.get(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), HttpStatus.SC_OK)));
    wireMockServer.stubFor(WireMock.put(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(noContent()));

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithItemId());
    assertNotNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildPrimaryRequestUpdateEvent(OPEN_NOT_YET_FILLED, CLOSED_CANCELLED);
    publishEventAndWait(PRIMARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    EcsTlrEntity updatedEcsTlr = getEcsTlr(initialEcsTlr.getId());
    UUID transactionId = updatedEcsTlr.getSecondaryRequestDcbTransactionId();
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasRetrieved(transactionId, SECONDARY_REQUEST_TENANT_ID);
    verifyThatDcbTransactionWasUpdated(transactionId,
      SECONDARY_REQUEST_TENANT_ID, TransactionStatusResponse.StatusEnum.CANCELLED);
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN_NOT_YET_FILLED, OPEN_IN_TRANSIT, CREATED, OPEN",
    "OPEN_IN_TRANSIT, OPEN_AWAITING_PICKUP, OPEN, AWAITING_PICKUP",
    "OPEN_AWAITING_PICKUP, CLOSED_FILLED, AWAITING_PICKUP, ITEM_CHECKED_OUT",
  })
  void primaryRequestUpdate(
    Request.StatusEnum oldRequestStatus, Request.StatusEnum newRequestStatus,
    TransactionStatusResponse.StatusEnum oldTransactionStatus,
    TransactionStatusResponse.StatusEnum expectedNewTransactionStatus) {

    mockDcb(oldTransactionStatus, expectedNewTransactionStatus);

    Request secondaryRequest = buildSecondaryRequest(OPEN_NOT_YET_FILLED)
      // custom values in order to trigger change propagation from primary to secondary request
      .requestExpirationDate(Date.from(ZonedDateTime.now().minusDays(1).toInstant()))
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.DELIVERY)
      .pickupServicePointId(randomId());

    wireMockServer.stubFor(WireMock.get(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), HttpStatus.SC_OK)));
    wireMockServer.stubFor(WireMock.put(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(noContent()));

    ServicePoint primaryPickupServicePoint = buildPrimaryRequestPickupServicePoint(
      PICKUP_SERVICE_POINT_ID.toString());
    ServicePoint secondaryPickupServicePoint = buildSecondaryRequestPickupServicePoint(
      primaryPickupServicePoint);

    wireMockServer.stubFor(WireMock.get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(PRIMARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(primaryPickupServicePoint), HttpStatus.SC_OK)));
    wireMockServer.stubFor(WireMock.get(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(notFound())); // to trigger service point cloning
    wireMockServer.stubFor(post(urlMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryPickupServicePoint), HttpStatus.SC_CREATED)));

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithItemId());
    assertNotNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildPrimaryRequestUpdateEvent(oldRequestStatus, newRequestStatus);
    publishEventAndWait(PRIMARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    EcsTlrEntity updatedEcsTlr = getEcsTlr(initialEcsTlr.getId());
    UUID transactionId = updatedEcsTlr.getPrimaryRequestDcbTransactionId();
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasRetrieved(transactionId, PRIMARY_REQUEST_TENANT_ID);
    verifyThatDcbTransactionWasUpdated(transactionId, PRIMARY_REQUEST_TENANT_ID,
      expectedNewTransactionStatus);

    wireMockServer.verify(getRequestedFor(
      urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID)));

    Request updatedPrimaryRequest = event.getData().getNewVersion();
    wireMockServer.verify(putRequestedFor(
      urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .withRequestBody(equalToJson(asJsonString(secondaryRequest
        .requestExpirationDate(updatedPrimaryRequest.getRequestExpirationDate())
        .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
        .pickupServicePointId(PICKUP_SERVICE_POINT_ID.toString())
      ))));

    wireMockServer.verify(getRequestedFor(urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(PRIMARY_REQUEST_TENANT_ID)));

    wireMockServer.verify(postRequestedFor(urlMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .withRequestBody(equalToJson(asJsonString(secondaryPickupServicePoint))));
  }

  @Test
  void shouldNotUpdateSecondaryRequestUponPrimaryRequestUpdateWhenNoRelevantChangesWereDetected() {
    mockDcb(TransactionStatusResponse.StatusEnum.CREATED, TransactionStatusResponse.StatusEnum.OPEN);
    Request secondaryRequest = buildSecondaryRequest(OPEN_NOT_YET_FILLED);
    wireMockServer.stubFor(WireMock.get(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), HttpStatus.SC_OK)));

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithItemId());
    assertNotNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildPrimaryRequestUpdateEvent(OPEN_NOT_YET_FILLED, OPEN_IN_TRANSIT);
    publishEventAndWait(PRIMARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    EcsTlrEntity updatedEcsTlr = getEcsTlr(initialEcsTlr.getId());
    UUID transactionId = updatedEcsTlr.getPrimaryRequestDcbTransactionId();
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasRetrieved(transactionId, PRIMARY_REQUEST_TENANT_ID);
    verifyThatDcbTransactionWasUpdated(transactionId, PRIMARY_REQUEST_TENANT_ID,
      TransactionStatusResponse.StatusEnum.OPEN);

    wireMockServer.verify(getRequestedFor(
      urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID)));

    wireMockServer.verify(0, putRequestedFor(
      urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID)));

    wireMockServer.verify(0, getRequestedFor(
      urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID))
      .withHeader(HEADER_TENANT, equalTo(PRIMARY_REQUEST_TENANT_ID)));

    wireMockServer.verify(0, postRequestedFor(urlMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID)));
  }

  @Test
  void shouldNotTryToClonePickupServicePointWhenPrimaryRequestFulfillmentPreferenceIsChangedToDelivery() {
    mockDcb(TransactionStatusResponse.StatusEnum.OPEN, TransactionStatusResponse.StatusEnum.OPEN);

    Request secondaryRequest = buildSecondaryRequest(OPEN_NOT_YET_FILLED)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(randomId());

    wireMockServer.stubFor(WireMock.get(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), HttpStatus.SC_OK)));
    wireMockServer.stubFor(WireMock.put(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(noContent()));

    createEcsTlr(buildEcsTlrWithItemId());

    KafkaEvent<Request> event = buildPrimaryRequestUpdateEvent(OPEN_NOT_YET_FILLED, OPEN_IN_TRANSIT);
    event.getData().getNewVersion()
      .pickupServicePointId(null)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.DELIVERY);

    publishEventAndWait(PRIMARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    wireMockServer.verify(getRequestedFor(
      urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID)));

    wireMockServer.verify(putRequestedFor(
      urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .withRequestBody(equalToJson(asJsonString(secondaryRequest
        .fulfillmentPreference(Request.FulfillmentPreferenceEnum.DELIVERY)
        .pickupServicePointId(null)
      ))));

    // no service point fetching for either tenant
    wireMockServer.verify(0, getRequestedFor(
      urlMatching(SERVICE_POINTS_URL + "/" + PICKUP_SERVICE_POINT_ID)));

    wireMockServer.verify(0, postRequestedFor(urlMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID)));
  }

@Test
  void shouldNotUpdateDcbTransactionUponRequestUpdateWhenTransactionStatusWouldNotChange() {
    mockDcb(TransactionStatusResponse.StatusEnum.OPEN, TransactionStatusResponse.StatusEnum.OPEN);
    EcsTlrEntity ecsTlr = createEcsTlr(buildEcsTlrWithItemId());
    publishEventAndWait(SECONDARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME,
      buildSecondaryRequestUpdateEvent());

    EcsTlrEntity updatedEcsTlr = getEcsTlr(ecsTlr.getId());
    UUID transactionId = updatedEcsTlr.getSecondaryRequestDcbTransactionId();
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasRetrieved(transactionId, SECONDARY_REQUEST_TENANT_ID);
    verifyThatNoDcbTransactionsWereUpdated();
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN_NOT_YET_FILLED, OPEN_NOT_YET_FILLED"
  })
  void shouldNotCreateOrUpdateLendingDcbTransactionUponIrrelevantSecondaryRequestStatusChange(
    Request.StatusEnum oldRequestStatus, Request.StatusEnum newRequestStatus) {

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithItemId());
    assertNotNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildSecondaryRequestUpdateEvent(oldRequestStatus, newRequestStatus);
    publishEventAndWait(SECONDARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasNotRetrieved();
    verifyThatNoDcbTransactionsWereUpdated();
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN_NOT_YET_FILLED, OPEN_NOT_YET_FILLED"
  })
  void shouldNotUpdateBorrowingDcbTransactionUponIrrelevantPrimaryRequestStatusChange(
    Request.StatusEnum oldRequestStatus, Request.StatusEnum newRequestStatus) {

    Request secondaryRequest = buildSecondaryRequest(OPEN_NOT_YET_FILLED);
    wireMockServer.stubFor(WireMock.get(urlMatching(format(REQUEST_STORAGE_URL_PATTERN, SECONDARY_REQUEST_ID)))
      .withHeader(HEADER_TENANT, equalTo(SECONDARY_REQUEST_TENANT_ID))
      .willReturn(jsonResponse(asJsonString(secondaryRequest), HttpStatus.SC_OK)));

    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithItemId());
    assertNotNull(initialEcsTlr.getItemId());

    KafkaEvent<Request> event = buildPrimaryRequestUpdateEvent(oldRequestStatus, newRequestStatus);
    publishEventAndWait(PRIMARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasNotRetrieved();
    verifyThatNoDcbTransactionsWereUpdated();
  }

  @Test
  void shouldNotTryToUpdateTransactionStatusUponRequestUpdateWhenTransactionIsNotFound() {
    EcsTlrEntity ecsTlr = createEcsTlr(buildEcsTlrWithItemId());

    wireMockServer.stubFor(WireMock.get(urlMatching(DCB_TRANSACTIONS_URL_PATTERN))
      .willReturn(notFound()));

    publishEventAndWait(SECONDARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME,
      buildSecondaryRequestUpdateEvent());

    UUID transactionId = ecsTlr.getSecondaryRequestDcbTransactionId();
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasRetrieved(transactionId, SECONDARY_REQUEST_TENANT_ID);
    verifyThatNoDcbTransactionsWereUpdated();
  }

  @Test
  void requestEventOfUnsupportedTypeIsIgnored() {
    checkThatEventIsIgnored(
      buildEvent(SECONDARY_REQUEST_TENANT_ID, KafkaEvent.EventType.CREATED,
        buildSecondaryRequest(OPEN_NOT_YET_FILLED),
        buildSecondaryRequest(OPEN_IN_TRANSIT)
      ));
  }

  @Test
  void requestUpdateEventFromUnknownTenantIsIgnored() {
    checkThatEventIsIgnored(
      buildUpdateEvent("unknown",
        buildSecondaryRequest(OPEN_NOT_YET_FILLED),
        buildSecondaryRequest(OPEN_IN_TRANSIT)
      ));
  }

  @Test
  void requestUpdateEventWithoutNewVersionOfRequestIsIgnored() {
    checkThatEventIsIgnored(
      buildUpdateEvent(SECONDARY_REQUEST_TENANT_ID, buildSecondaryRequest(OPEN_NOT_YET_FILLED), null));
  }

  @Test
  void requestUpdateEventForRequestWithoutItemIdIsIgnored() {
    checkThatEventIsIgnored(
      buildUpdateEvent(SECONDARY_REQUEST_TENANT_ID,
        buildSecondaryRequest(OPEN_NOT_YET_FILLED).itemId(null),
        buildSecondaryRequest(CLOSED_CANCELLED).itemId(null)
      ));
  }

  @Test
  void requestUpdateEventForRequestWithoutEcsRequestPhaseIsIgnored() {
    checkThatEventIsIgnored(
      buildUpdateEvent(PRIMARY_REQUEST_TENANT_ID,
        buildPrimaryRequest(OPEN_NOT_YET_FILLED).ecsRequestPhase(null),
        buildPrimaryRequest(CLOSED_CANCELLED).ecsRequestPhase(null)
      ));
  }

  @Test
  void requestUpdateEventForUnknownRequestIsIgnored() {
    String randomId = randomId();
    checkThatEventIsIgnored(
      buildUpdateEvent(SECONDARY_REQUEST_TENANT_ID,
        buildSecondaryRequest(OPEN_NOT_YET_FILLED).id(randomId),
        buildSecondaryRequest(OPEN_IN_TRANSIT).id(randomId)
      ));
  }

  @Test
  void shouldCloneNewPatronGroupFromCentralTenantToNonCentralTenants() {
    wireMockServer.stubFor(post(urlMatching(USER_GROUPS_URL_PATTERN))
      .willReturn(jsonResponse("", HttpStatus.SC_CREATED)));

    mockUserTenants(wireMockServer, CENTRAL_TENANT_ID, CONSORTIUM_ID);
    mockConsortiaTenants(wireMockServer, CONSORTIUM_ID);

    KafkaEvent<UserGroup> event = buildUserGroupCreateEvent("new-user-group");

    publishEventAndWait(CENTRAL_TENANT_ID, USER_GROUP_KAFKA_TOPIC_NAME, event);

    var newUserGroup = event.getData().getNewVersion();

    wireMockServer.verify(1, postRequestedFor(urlMatching(USER_GROUPS_URL_PATTERN))
      .withRequestBody(equalToJson(asJsonString(newUserGroup)))
      .withHeader(XOkapiHeaders.TENANT, equalTo("university")));
    wireMockServer.verify(1, postRequestedFor(urlMatching(USER_GROUPS_URL_PATTERN))
      .withRequestBody(equalToJson(asJsonString(newUserGroup)))
      .withHeader(XOkapiHeaders.TENANT, equalTo("college")));
    wireMockServer.verify(0, postRequestedFor(urlMatching(USER_GROUPS_URL_PATTERN))
      .withHeader(XOkapiHeaders.TENANT, equalTo("consortium")));
  }

  @Test
  void shouldUpdatePatronGroupInNonCentralTenantsWhenUpdatedInCentralTenant() {
    var userGroupId = randomUUID();
    var userGroupUpdateUrlPattern = format("%s/%s", USER_GROUPS_URL_PATTERN, userGroupId);
    wireMockServer.stubFor(put(urlMatching(userGroupUpdateUrlPattern))
      .willReturn(jsonResponse("", HttpStatus.SC_NO_CONTENT)));

    mockUserTenants(wireMockServer, TENANT_ID_CONSORTIUM, CONSORTIUM_ID);
    mockConsortiaTenants(wireMockServer, CONSORTIUM_ID);

    KafkaEvent<UserGroup> event = buildUserGroupUpdateEvent(userGroupId, "old-user-group",
      "new-user-group");

    publishEventAndWait(CENTRAL_TENANT_ID, USER_GROUP_KAFKA_TOPIC_NAME, event);

    var updatedUserGroup = event.getData().getNewVersion();

    wireMockServer.verify(1, putRequestedFor(urlMatching(userGroupUpdateUrlPattern))
      .withRequestBody(equalToJson(asJsonString(updatedUserGroup)))
      .withHeader(XOkapiHeaders.TENANT, equalTo("university")));
    wireMockServer.verify(1, putRequestedFor(urlMatching(userGroupUpdateUrlPattern))
      .withRequestBody(equalToJson(asJsonString(updatedUserGroup)))
      .withHeader(XOkapiHeaders.TENANT, equalTo("college")));
    wireMockServer.verify(0, putRequestedFor(urlMatching(userGroupUpdateUrlPattern))
      .withHeader(XOkapiHeaders.TENANT, equalTo("consortium")));
  }

  @Test
  void shouldIgnoreUserGroupEventsReceivedFromNonCentralTenants() {
    wireMockServer.stubFor(post(urlMatching(USER_GROUPS_URL_PATTERN))
      .willReturn(jsonResponse("", HttpStatus.SC_CREATED)));

    var userGroupId = randomUUID();
    var userGroupUpdateUrlPattern = format("%s/%s", USER_GROUPS_URL_PATTERN, userGroupId);
    wireMockServer.stubFor(put(urlMatching(userGroupUpdateUrlPattern))
      .willReturn(jsonResponse("", HttpStatus.SC_CREATED)));

    mockUserTenants(wireMockServer, CENTRAL_TENANT_ID, CONSORTIUM_ID);
    mockConsortiaTenants(wireMockServer, CONSORTIUM_ID);

    KafkaEvent<UserGroup> createEvent = buildUserGroupCreateEvent(TENANT_ID_COLLEGE, "new-user-group-1");
    publishEventAndWait(TENANT_ID_COLLEGE, USER_GROUP_KAFKA_TOPIC_NAME, createEvent);

    KafkaEvent<UserGroup> updateEvent = buildUserGroupUpdateEvent(TENANT_ID_UNIVERSITY, userGroupId, "old-user-group-2",
      "new-user-group-2");
    publishEventAndWait(TENANT_ID_UNIVERSITY, USER_GROUP_KAFKA_TOPIC_NAME, updateEvent);

    wireMockServer.verify(0, putRequestedFor(urlMatching(USER_GROUPS_URL_PATTERN)));
    wireMockServer.verify(0, putRequestedFor(urlMatching(userGroupUpdateUrlPattern)));
  }

  void checkThatEventIsIgnored(KafkaEvent<Request> event) {
    EcsTlrEntity initialEcsTlr = createEcsTlr(buildEcsTlrWithoutItemId());
    publishEventAndWait(PRIMARY_REQUEST_TENANT_ID, REQUEST_KAFKA_TOPIC_NAME, event);

    EcsTlrEntity ecsTlr = getEcsTlr(initialEcsTlr.getId());
    assertNull(ecsTlr.getItemId());
    assertNull(ecsTlr.getPrimaryRequestDcbTransactionId());
    assertNull(ecsTlr.getSecondaryRequestDcbTransactionId());
    verifyThatNoDcbTransactionsWereCreated();
    verifyThatDcbTransactionStatusWasNotRetrieved();
    verifyThatNoDcbTransactionsWereUpdated();
  }

  private static void verifyThatDcbTransactionsWereCreated(EcsTlrEntity ecsTlr) {
    UUID primaryRequestDcbTransactionId = ecsTlr.getPrimaryRequestDcbTransactionId();
    UUID secondaryRequestDcbTransactionId = ecsTlr.getSecondaryRequestDcbTransactionId();
    assertNotNull(primaryRequestDcbTransactionId);
    assertNotNull(secondaryRequestDcbTransactionId);

    DcbTransaction expectedBorrowerTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.BORROWER)
      .item(new DcbItem()
        .id(ecsTlr.getItemId().toString())
        .barcode("test")
        .title("Test title"))
      .requestId(ecsTlr.getPrimaryRequestId().toString());

    DcbTransaction expectedLenderTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.LENDER)
      .requestId(ecsTlr.getSecondaryRequestId().toString());

    wireMockServer.verify(postRequestedFor(urlMatching(
      ECS_REQUEST_TRANSACTIONS_URL + "/" + primaryRequestDcbTransactionId))
      .withHeader(HEADER_TENANT, equalTo(ecsTlr.getPrimaryRequestTenantId()))
      .withRequestBody(equalToJson(asJsonString(expectedBorrowerTransaction))));

    wireMockServer.verify(postRequestedFor(urlMatching(
      ECS_REQUEST_TRANSACTIONS_URL + "/" + secondaryRequestDcbTransactionId))
      .withHeader(HEADER_TENANT, equalTo(ecsTlr.getSecondaryRequestTenantId()))
      .withRequestBody(equalToJson(asJsonString(expectedLenderTransaction))));
  }

  private static void verifyThatNoDcbTransactionsWereCreated() {
    wireMockServer.verify(0, postRequestedFor(
      urlMatching(ECS_REQUEST_TRANSACTIONS_URL + "/" + UUID_PATTERN)));
  }

  private static void verifyThatDcbTransactionWasUpdated(UUID transactionId, String tenant,
    TransactionStatusResponse.StatusEnum newStatus) {

    wireMockServer.verify(putRequestedFor(
      urlMatching(format(DCB_TRANSACTION_STATUS_URL_PATTERN, transactionId)))
      .withHeader(HEADER_TENANT, equalTo(tenant))
      .withRequestBody(equalToJson(asJsonString(
        new TransactionStatus().status(TransactionStatus.StatusEnum.valueOf(newStatus.name()))))));
  }

  private static void verifyThatNoDcbTransactionsWereUpdated() {
    wireMockServer.verify(0, putRequestedFor(urlMatching(DCB_TRANSACTIONS_URL_PATTERN)));
  }

  private static void verifyThatDcbTransactionStatusWasRetrieved(UUID transactionId, String tenant) {
    wireMockServer.verify(getRequestedFor(
      urlMatching(format(DCB_TRANSACTION_STATUS_URL_PATTERN, transactionId)))
      .withHeader(HEADER_TENANT, equalTo(tenant)));
  }

  private static void verifyThatDcbTransactionStatusWasNotRetrieved() {
    wireMockServer.verify(0, getRequestedFor(urlMatching(DCB_TRANSACTIONS_URL_PATTERN)));
  }

  @SneakyThrows
  private <T> void publishEvent(String tenant, String topic, KafkaEvent<T> event) {
    publishEvent(tenant, topic, asJsonString(event));
  }

  @SneakyThrows
  private void publishEvent(String tenant, String topic, String payload) {
    kafkaTemplate.send(new ProducerRecord<>(topic, 0, randomId(), payload,
        List.of(
          new RecordHeader(XOkapiHeaders.TENANT, tenant.getBytes()),
          new RecordHeader("folio.tenantId", randomId().getBytes())
        )))
      .get(10, SECONDS);
  }

  @SneakyThrows
  private static int getOffset(String topic, String consumerGroup) {
    return kafkaAdminClient.listConsumerGroupOffsets(consumerGroup)
      .partitionsToOffsetAndMetadata()
      .thenApply(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::offset)
        .map(Long::intValue)
        .orElse(0))
      .get(10, TimeUnit.SECONDS);
  }

  private <T> void publishEventAndWait(String tenant, String topic, KafkaEvent<T> event) {
    publishEventAndWait(tenant, topic, asJsonString(event));
  }

  private void publishEventAndWait(String tenant, String topic, String payload) {
    final int initialOffset = getOffset(topic, CONSUMER_GROUP_ID);
    publishEvent(tenant, topic, payload);
    waitForOffset(topic, CONSUMER_GROUP_ID, initialOffset + 1);
  }

  private void waitForOffset(String topic, String consumerGroupId, int expectedOffset) {
    Awaitility.await()
      .atMost(60, TimeUnit.SECONDS)
      .until(() -> getOffset(topic, consumerGroupId), offset -> offset.equals(expectedOffset));
  }

  private static KafkaEvent<Request> buildPrimaryRequestUpdateEvent(Request.StatusEnum oldStatus,
    Request.StatusEnum newStatus) {

    return buildUpdateEvent(PRIMARY_REQUEST_TENANT_ID,
      buildPrimaryRequest(oldStatus),
      buildPrimaryRequest(newStatus));
  }

  private static KafkaEvent<Request> buildSecondaryRequestUpdateEvent(Request.StatusEnum oldStatus,
    Request.StatusEnum newStatus) {

    return buildUpdateEvent(SECONDARY_REQUEST_TENANT_ID,
      buildSecondaryRequest(oldStatus),
      buildSecondaryRequest(newStatus));
  }

  private static KafkaEvent<Request> buildSecondaryRequestUpdateEvent() {
    return buildSecondaryRequestUpdateEvent(OPEN_NOT_YET_FILLED, OPEN_IN_TRANSIT);
  }

  private static KafkaEvent<UserGroup> buildUserGroupCreateEvent(String name) {
    return buildUserGroupCreateEvent(CENTRAL_TENANT_ID, name);
  }

  private static KafkaEvent<UserGroup> buildUserGroupCreateEvent(String tenantId, String name) {
    return buildCreateEvent(tenantId, buildUserGroup(name));
  }

  private static KafkaEvent<UserGroup> buildUserGroupUpdateEvent(UUID id, String oldName,
    String newName) {

    return buildUserGroupUpdateEvent(CENTRAL_TENANT_ID, id, oldName, newName);
  }

  private static KafkaEvent<UserGroup> buildUserGroupUpdateEvent(String tenantId, UUID id,
    String oldName, String newName) {

    return buildUpdateEvent(tenantId,
      buildUserGroup(id, oldName),
      buildUserGroup(id, newName));
  }

  private static <T> KafkaEvent<T> buildCreateEvent(String tenant, T newVersion) {
    return buildEvent(tenant, CREATED, null, newVersion);
  }

  private static <T> KafkaEvent<T> buildUpdateEvent(String tenant, T oldVersion, T newVersion) {
    return buildEvent(tenant, UPDATED, oldVersion, newVersion);
  }

  private static Request buildPrimaryRequest(Request.StatusEnum status) {
    return buildRequest(PRIMARY_REQUEST_ID, Request.EcsRequestPhaseEnum.PRIMARY, status);
  }

  private static Request buildSecondaryRequest(Request.StatusEnum status) {
    return buildRequest(SECONDARY_REQUEST_ID, Request.EcsRequestPhaseEnum.SECONDARY, status);
  }

  private static Request buildRequest(UUID id, Request.EcsRequestPhaseEnum ecsPhase,
    Request.StatusEnum status) {

    return new Request()
      .id(id.toString())
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .ecsRequestPhase(ecsPhase)
      .requestDate(new Date())
      .requesterId(REQUESTER_ID.toString())
      .instanceId(INSTANCE_ID.toString())
      .holdingsRecordId(HOLDINGS_ID.toString())
      .itemId(ITEM_ID.toString())
      .status(status)
      .position(1)
      .instance(new RequestInstance().title("Test title"))
      .item(new RequestItem().barcode("test"))
      .requester(new RequestRequester()
        .firstName("First")
        .lastName("Last")
        .barcode("test"))
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(PICKUP_SERVICE_POINT_ID.toString())
      .requestExpirationDate(REQUEST_EXPIRATION_DATE);
  }

  private static UserGroup buildUserGroup(String name) {
    return buildUserGroup(randomUUID(), name);
  }

  private static UserGroup buildUserGroup(UUID id, String name) {
    return new UserGroup()
      .id(id.toString())
      .group(name)
      .desc("description")
      .expirationOffsetInDays(0)
      .source("source");
  }

  private static EcsTlrEntity buildEcsTlrWithItemId() {
    return EcsTlrEntity.builder()
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .primaryRequestTenantId(PRIMARY_REQUEST_TENANT_ID)
      .primaryRequestDcbTransactionId(PRIMARY_REQUEST_DCB_TRANSACTION_ID)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .secondaryRequestTenantId(SECONDARY_REQUEST_TENANT_ID)
      .secondaryRequestDcbTransactionId(SECONDARY_REQUEST_DCB_TRANSACTION_ID)
      .itemId(ITEM_ID)
      .pickupServicePointId(PICKUP_SERVICE_POINT_ID)
      .build();
  }

  private static EcsTlrEntity buildEcsTlrWithoutItemId() {
    return EcsTlrEntity.builder()
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .primaryRequestTenantId(PRIMARY_REQUEST_TENANT_ID)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .secondaryRequestTenantId(SECONDARY_REQUEST_TENANT_ID)
      .pickupServicePointId(PICKUP_SERVICE_POINT_ID)
      .requestExpirationDate(REQUEST_EXPIRATION_DATE)
      .build();
  }

  private static void mockDcb(TransactionStatusResponse.StatusEnum initialTransactionStatus,
    TransactionStatusResponse.StatusEnum newTransactionStatus) {

    // mock DCB transaction POST response
    TransactionStatusResponse mockPostEcsDcbTransactionResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CREATED);
    wireMockServer.stubFor(post(urlMatching(POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .willReturn(jsonResponse(mockPostEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    // mock DCB transaction GET response
    TransactionStatusResponse mockGetTransactionStatusResponse = new TransactionStatusResponse()
      .status(initialTransactionStatus)
      .role(TransactionStatusResponse.RoleEnum.LENDER);
    wireMockServer.stubFor(WireMock.get(urlMatching(DCB_TRANSACTIONS_URL_PATTERN))
      .willReturn(jsonResponse(mockGetTransactionStatusResponse, HttpStatus.SC_OK)));

    // mock DCB transaction PUT response
    TransactionStatusResponse mockPutEcsDcbTransactionResponse = new TransactionStatusResponse()
      .status(newTransactionStatus);
    wireMockServer.stubFor(WireMock.put(urlMatching(DCB_TRANSACTIONS_URL_PATTERN))
      .willReturn(jsonResponse(mockPutEcsDcbTransactionResponse, HttpStatus.SC_OK)));
  }

  private EcsTlrEntity createEcsTlr(EcsTlrEntity ecsTlr) {
    return executionService.executeSystemUserScoped(CENTRAL_TENANT_ID,
      () -> ecsTlrRepository.save(ecsTlr));
  }

  private EcsTlrEntity getEcsTlr(UUID id) {
    return executionService.executeSystemUserScoped(CENTRAL_TENANT_ID,
      () -> ecsTlrRepository.findById(id)).orElseThrow();
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
