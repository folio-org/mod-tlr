package org.folio.service;

import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.folio.util.TestUtils.buildEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.folio.api.BaseIT;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestsBatchUpdate;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.repository.EcsTlrRepository;
import org.folio.support.KafkaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

class RequestBatchUpdateEventHandlerTest extends BaseIT {

  private static final String CENTRAL_TENANT_ID = "consortium";
  @MockBean
  RequestService requestService;
  @MockBean
  private EcsTlrRepository ecsTlrRepository;
  @Autowired
  private KafkaEventListener eventListener;

  @Test
  void shouldUpdateSecondaryRequestPositionsWhenPrimaryRequestsPositionsChanged() {
    var requesterId = randomId();
    var pickupServicePointId = randomId();
    var instanceId = randomId();
    var firstTenant = "tenant1";
    var secondTenant = "tenant2";

    var firstEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, secondTenant);
    var fourthEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 2);

    var secondPrimaryRequest = buildPrimaryRequest(secondEcsTlr, secondSecondaryRequest, 2);
    var thirdPrimaryRequest = buildPrimaryRequest(thirdEcsTlr, thirdSecondaryRequest, 3);
    var fourthPrimaryRequest = buildPrimaryRequest(fourthEcsTlr, fourthSecondaryRequest, 4);
    var reorderedFirstPrimaryRequest = buildPrimaryRequest(firstEcsTlr, firstSecondaryRequest, 4);

    var ecsTlrMapper = new EcsTlrMapperImpl();
    when(ecsTlrRepository.findBySecondaryRequestId(any()))
      .thenReturn(Optional.of(ecsTlrMapper.mapDtoToEntity(firstEcsTlr)));
    when(requestService.getRequestFromStorage(firstEcsTlr.getSecondaryRequestId(),
      firstEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(firstSecondaryRequest);
    when(requestService.getRequestFromStorage(secondEcsTlr.getSecondaryRequestId(),
      secondEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(secondSecondaryRequest);
    when(requestService.getRequestFromStorage(thirdEcsTlr.getSecondaryRequestId(),
      thirdEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(thirdSecondaryRequest);
    when(requestService.getRequestFromStorage(fourthEcsTlr.getSecondaryRequestId(),
      fourthEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(fourthSecondaryRequest);
    var requestsQueue = Stream.of(reorderedFirstPrimaryRequest, secondPrimaryRequest, thirdPrimaryRequest,
        fourthPrimaryRequest)
      .sorted(Comparator.comparing(Request::getPosition))
      .toList();

    when(requestService.getRequestsByInstanceId(any())).thenReturn(requestsQueue);
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr)));

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, UPDATED,
      null, new RequestsBatchUpdate().instanceId(instanceId))), getMessageHeaders(
        CENTRAL_TENANT_ID, CENTRAL_TENANT_ID));
    verify(requestService, times(1)).updateRequestInStorage(firstSecondaryRequest, firstTenant);
    verify(requestService, times(1)).updateRequestInStorage(secondSecondaryRequest, firstTenant);
    verify(requestService, times(0)).updateRequestInStorage(thirdSecondaryRequest, secondTenant);
    verify(requestService, times(0)).updateRequestInStorage(fourthSecondaryRequest, secondTenant);
  }

  @Test
  void shouldReorderSecondaryRequestsFollowingChangesInPrimaryRequestOrder() {
    var requesterId = randomId();
    var pickupServicePointId = randomId();
    var instanceId = randomId();
    var firstTenant = "tenant1";
    var secondTenant = "tenant2";

    var firstEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, firstTenant);
    var fourthEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, secondTenant);
    var fifthEcsTlr = buildEcsTlr(instanceId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 3);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 1);
    var fifthSecondaryRequest = buildSecondaryRequest(fifthEcsTlr, 2);

    var firstPrimaryRequest = buildPrimaryRequest(firstEcsTlr, firstSecondaryRequest, 1);
    var thirdPrimaryRequest = buildPrimaryRequest(thirdEcsTlr, thirdSecondaryRequest, 3);
    var fourthPrimaryRequest = buildPrimaryRequest(fourthEcsTlr, fourthSecondaryRequest, 4);
    var fifthPrimaryRequest = buildPrimaryRequest(fifthEcsTlr, fifthSecondaryRequest, 5);
    var reorderedSecondPrimaryRequest = buildPrimaryRequest(secondEcsTlr,
      secondSecondaryRequest, 5);

    var ecsTlrMapper = new EcsTlrMapperImpl();
    when(ecsTlrRepository.findBySecondaryRequestId(any()))
      .thenReturn(Optional.of(ecsTlrMapper.mapDtoToEntity(secondEcsTlr)));
    when(requestService.getRequestFromStorage(firstEcsTlr.getSecondaryRequestId(),
      firstEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(firstSecondaryRequest);
    when(requestService.getRequestFromStorage(secondEcsTlr.getSecondaryRequestId(),
      secondEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(secondSecondaryRequest);
    when(requestService.getRequestFromStorage(thirdEcsTlr.getSecondaryRequestId(),
      thirdEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(thirdSecondaryRequest);
    when(requestService.getRequestFromStorage(fourthEcsTlr.getSecondaryRequestId(),
      fourthEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(fourthSecondaryRequest);
    when(requestService.getRequestFromStorage(fifthEcsTlr.getSecondaryRequestId(),
      fifthEcsTlr.getSecondaryRequestTenantId()))
      .thenReturn(fifthSecondaryRequest);
    when(requestService.getRequestsByInstanceId(any()))
      .thenReturn(List.of(firstPrimaryRequest, reorderedSecondPrimaryRequest, thirdPrimaryRequest,
        fourthPrimaryRequest, fifthPrimaryRequest));
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr),
      ecsTlrMapper.mapDtoToEntity(fifthEcsTlr)));

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, UPDATED,
      null, new RequestsBatchUpdate().instanceId(instanceId))), getMessageHeaders(
        CENTRAL_TENANT_ID, CENTRAL_TENANT_ID));
    verify(requestService, times(0)).updateRequestInStorage(firstSecondaryRequest, firstTenant);
    verify(requestService, times(1)).updateRequestInStorage(secondSecondaryRequest, firstTenant);
    verify(requestService, times(1)).updateRequestInStorage(thirdSecondaryRequest, firstTenant);
    verify(requestService, times(0)).updateRequestInStorage(fourthSecondaryRequest, secondTenant);
    verify(requestService, times(0)).updateRequestInStorage(fifthSecondaryRequest, secondTenant);
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
      .secondaryRequestTenantId(secondaryRequestTenantId)
      .primaryRequestTenantId(CENTRAL_TENANT_ID);
  }

  private static Request buildPrimaryRequest(EcsTlr ecsTlr, Request secondaryRequest, int position) {
    return new Request()
      .id(ecsTlr.getPrimaryRequestId())
      .instanceId(secondaryRequest.getInstanceId())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestExpirationDate(secondaryRequest.getRequestExpirationDate())
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(secondaryRequest.getPickupServicePointId())
      .position(position);
  }

  private static Request buildSecondaryRequest(EcsTlr ecsTlr, int position) {
    return new Request()
      .id(ecsTlr.getSecondaryRequestId())
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

  @SneakyThrows
  private String serializeEvent(KafkaEvent<RequestsBatchUpdate> event) {
    return new ObjectMapper().writeValueAsString(event);
  }
}