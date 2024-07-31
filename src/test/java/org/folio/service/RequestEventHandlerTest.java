package org.folio.service;

import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.folio.support.MockDataUtils.getEcsTlrEntity;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.api.BaseIT;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.repository.EcsTlrRepository;
import org.folio.support.KafkaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

class RequestEventHandlerTest extends BaseIT {
  private static final String REQUEST_UPDATE_EVENT_SAMPLE =
    getMockDataAsString("mockdata/kafka/secondary_request_update_event.json");

  @MockBean
  private DcbService dcbService;
  @MockBean
  RequestService requestService;
  @MockBean
  private EcsTlrRepository ecsTlrRepository;

  @Autowired
  private KafkaEventListener eventListener;

  @Test
  void handleRequestUpdateTest() {
    when(ecsTlrRepository.findBySecondaryRequestId(any())).thenReturn(Optional.of(getEcsTlrEntity()));
    doNothing().when(dcbService).createLendingTransaction(any());
    eventListener.handleRequestEvent(REQUEST_UPDATE_EVENT_SAMPLE, getMessageHeaders(
      TENANT_ID_CONSORTIUM, UUID.randomUUID().toString()));
    verify(ecsTlrRepository).findBySecondaryRequestId(any());
  }

  @Test
  public void testUpdateQueuePositionIfPrimaryRequestPositionChanged() {
    String requesterId = randomId();
    String pickupServicePointId = randomId();
    String instanceId = randomId();
    String firstTenant = "tenant1";
    String secondTenant = "tenant2";

    EcsTlr firstEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    EcsTlr secondEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    EcsTlr thirdEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, secondTenant);
    EcsTlr fourthEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, secondTenant);

    Request firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    Request secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    Request thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    Request fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 2);

    Request firstPrimaryRequest = buildPrimaryRequest(firstSecondaryRequest, 1);
    Request secondPrimaryRequest = buildPrimaryRequest(secondSecondaryRequest, 2);
    Request thirdPrimaryRequest = buildPrimaryRequest(thirdSecondaryRequest, 3);
    Request fourthPrimaryRequest = buildPrimaryRequest(fourthSecondaryRequest, 4);


    Request oldVersion = firstPrimaryRequest;
    Request newVersion = buildPrimaryRequest(firstSecondaryRequest, 4);
    buildEvent("consortium", UPDATED, oldVersion, newVersion);

    EcsTlrEntity ecsTlrEntity = EcsTlrEntity.builder()
      .id(UUID.randomUUID())
      .primaryRequestId(UUID.fromString(firstPrimaryRequest.getId()))
      .primaryRequestTenantId("consortium")
      .secondaryRequestId(UUID.fromString(secondSecondaryRequest.getId()))
      .build();
    when(ecsTlrRepository.findBySecondaryRequestId(any())).thenReturn(Optional.of(ecsTlrEntity));
    when(requestService.getRequestFromStorage(any(),any())).thenReturn(firstSecondaryRequest);
    when(requestService.getRequestsByInstanceId(any())).thenReturn(List.of(firstPrimaryRequest, secondPrimaryRequest,
      thirdPrimaryRequest, fourthPrimaryRequest));
    EcsTlrMapper ecsTlrMapper = new EcsTlrMapperImpl();
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr)));

    eventListener.handleRequestEvent(serializeEvent(buildEvent(
      "consortium", UPDATED, oldVersion, newVersion)), getMessageHeaders(
        "consortium", "consortium"));
    verify(requestService, times(3)).updateRequestInStorage(any(Request.class), anyString());
  }

  private static EcsTlr buildEcsTlr(String instanceId, String requesterId,
    String pickupServicePointId, String secondaryRequestTenantId) {

    return new EcsTlr()
      .id(randomId())
      .instanceId(instanceId)
      .requesterId(requesterId)
      .pickupServicePointId(pickupServicePointId)
      .requestLevel(EcsTlr.RequestLevelEnum.TITLE)
      .requestType(EcsTlr.RequestTypeEnum.PAGE)
      .fulfillmentPreference(EcsTlr.FulfillmentPreferenceEnum.DELIVERY)
      .patronComments("random comment")
      .requestDate(new Date())
      .requestExpirationDate(new Date())
      .primaryRequestId(randomId())
      .secondaryRequestId(randomId())
      .secondaryRequestTenantId(secondaryRequestTenantId);
  }

  private static Request buildPrimaryRequest(Request secondaryRequest, int position) {
    return new Request()
      .id(secondaryRequest.getId())
      .instanceId(secondaryRequest.getInstanceId())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(secondaryRequest.getPickupServicePointId())
      .position(position);
  }

  private static Request buildSecondaryRequest(EcsTlr ecsTlr, int position) {
    return new Request()
      .id(ecsTlr.getId())
      .requesterId(ecsTlr.getRequesterId())
      .requestLevel(Request.RequestLevelEnum.fromValue(ecsTlr.getRequestLevel().getValue()))
      .requestType(Request.RequestTypeEnum.fromValue(ecsTlr.getRequestType().getValue()))
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.SECONDARY)
      .instanceId(ecsTlr.getInstanceId())
      .itemId(ecsTlr.getItemId())
      .pickupServicePointId(ecsTlr.getPickupServicePointId())
      .requestDate(ecsTlr.getRequestDate())
      .requestExpirationDate(ecsTlr.getRequestExpirationDate())
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.fromValue(
        ecsTlr.getFulfillmentPreference().getValue()))
      .patronComments(ecsTlr.getPatronComments())
      .position(position);
  }

  private static <T> KafkaEvent<T> buildUpdateEvent(String tenant, T oldVersion, T newVersion) {
    return buildEvent(tenant, UPDATED, oldVersion, newVersion);
  }

  private static <T> KafkaEvent<T> buildEvent(String tenant, KafkaEvent.EventType type,
    T oldVersion, T newVersion) {

    KafkaEvent.EventData<T> data = KafkaEvent.EventData.<T>builder()
      .oldVersion(oldVersion)
      .newVersion(newVersion)
      .build();

    return buildEvent(tenant, type, data);
  }

  private static <T> KafkaEvent<T> buildEvent(String tenant, KafkaEvent.EventType type,
    KafkaEvent.EventData<T> data) {

    return KafkaEvent.<T>builder()
      .id(randomId())
      .type(type)
      .timestamp(new Date().getTime())
      .tenant(tenant)
      .data(data)
      .build();
  }

  @SneakyThrows
  private String serializeEvent(KafkaEvent<Request> event) {
    return new ObjectMapper().writeValueAsString(event);
  }
}
