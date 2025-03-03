package org.folio.service;

import static org.folio.domain.dto.Request.RequestLevelEnum.ITEM;
import static org.folio.domain.dto.Request.RequestLevelEnum.TITLE;
import static org.folio.support.KafkaEvent.EventType.CREATED;
import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.folio.util.TestUtils.buildEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.folio.api.BaseIT;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.ReorderQueueReorderedQueueInner;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestsBatchUpdate;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.repository.EcsTlrRepository;
import org.folio.support.KafkaEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
  String requesterId = randomId();
  String pickupServicePointId = randomId();
  String instanceId = randomId();
  String itemId = randomId();
  String firstTenant = "tenant1";
  String secondTenant = "tenant2";

  @ParameterizedTest
  @EnumSource(value = Request.EcsRequestPhaseEnum.class, names = {"PRIMARY", "INTERMEDIATE"})
  void shouldReorderTwoSecondaryRequestsWhenPrimaryOrIntermediateRequestsReordered(
    Request.EcsRequestPhaseEnum ecsPhase) {

    var firstEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);
    var fourthEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 2);

    var secondPrimaryRequest = buildRequest(secondEcsTlr, secondSecondaryRequest, 2, TITLE, ecsPhase);
    var thirdPrimaryRequest = buildRequest(thirdEcsTlr, thirdSecondaryRequest, 3, TITLE, ecsPhase);
    var fourthPrimaryRequest = buildRequest(fourthEcsTlr, fourthSecondaryRequest, 4, TITLE, ecsPhase);
    var reorderedFirstPrimaryRequest = buildRequest(firstEcsTlr, firstSecondaryRequest, 4, TITLE, ecsPhase);

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
    when(requestService.getRequestsQueueByInstanceId(any())).thenReturn(requestsQueue);
    when(requestService.getRequestsQueueByInstanceId(any(), eq(firstTenant))).thenReturn(
      List.of(firstSecondaryRequest, secondSecondaryRequest));
    when(requestService.getRequestsQueueByInstanceId(any(), eq(secondTenant))).thenReturn(
      List.of(thirdSecondaryRequest, fourthSecondaryRequest));
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr)));

    List<Request> secRequestsWithUpdatedPositions = List.of(
      new Request()
        .id(firstSecondaryRequest.getId())
        .position(2),
      new Request()
        .id(secondSecondaryRequest.getId())
        .position(1));
    ReorderQueue reorderQueue = createReorderQueue(secRequestsWithUpdatedPositions);
    when(requestService.reorderRequestsQueueForInstance(instanceId, firstTenant, reorderQueue))
      .thenReturn(secRequestsWithUpdatedPositions);

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, CREATED,
      null, new RequestsBatchUpdate()
        .instanceId(instanceId)
        .requestLevel(RequestsBatchUpdate.RequestLevelEnum.TITLE))),
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(requestService, times(1)).reorderRequestsQueueForInstance(
      instanceId, firstTenant, reorderQueue);
    verify(requestService, times(0)).reorderRequestsQueueForInstance(
      eq(instanceId), eq(secondTenant), any());
  }

  @ParameterizedTest
  @EnumSource(value = Request.EcsRequestPhaseEnum.class, names = {"PRIMARY", "INTERMEDIATE"})
  void shouldReorderThreeSecondaryRequestsWhenPrimaryOrIntermediateRequestsReordered(
    Request.EcsRequestPhaseEnum ecsPhase) {

    var firstEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);
    var fourthEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var fifthEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 3);
    var fifthSecondaryRequest = buildSecondaryRequest(fifthEcsTlr, 2);

    var firstPrimaryRequest = buildRequest(firstEcsTlr, firstSecondaryRequest, 1, TITLE, ecsPhase);
    var thirdPrimaryRequest = buildRequest(thirdEcsTlr, thirdSecondaryRequest, 3, TITLE, ecsPhase);
    var fourthPrimaryRequest = buildRequest(fourthEcsTlr, fourthSecondaryRequest, 4, TITLE, ecsPhase);
    var fifthPrimaryRequest = buildRequest(fifthEcsTlr, fifthSecondaryRequest, 5, TITLE, ecsPhase);
    var reorderedSecondPrimaryRequest = buildRequest(secondEcsTlr,
      secondSecondaryRequest, 5, TITLE, ecsPhase);

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
    when(requestService.getRequestsQueueByInstanceId(any()))
      .thenReturn(List.of(firstPrimaryRequest, reorderedSecondPrimaryRequest, thirdPrimaryRequest,
        fourthPrimaryRequest, fifthPrimaryRequest));
    when(requestService.getRequestsQueueByInstanceId(any(), eq(firstTenant))).thenReturn(
      List.of(firstSecondaryRequest, secondSecondaryRequest, fourthSecondaryRequest));
    when(requestService.getRequestsQueueByInstanceId(any(), eq(secondTenant))).thenReturn(
      List.of(thirdSecondaryRequest, fifthSecondaryRequest));

    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr),
      ecsTlrMapper.mapDtoToEntity(fifthEcsTlr)));
    List<Request> secRequestsWithUpdatedPositions = List.of(
      new Request()
        .id(firstSecondaryRequest.getId())
        .position(1),
      new Request()
        .id(secondSecondaryRequest.getId())
        .position(3),
      new Request()
        .id(fourthSecondaryRequest.getId())
        .position(2));
    ReorderQueue reorderQueue = createReorderQueue(secRequestsWithUpdatedPositions);
    when(requestService.reorderRequestsQueueForInstance(instanceId, firstTenant, reorderQueue))
      .thenReturn(secRequestsWithUpdatedPositions);

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, UPDATED,
      null, new RequestsBatchUpdate()
        .instanceId(instanceId)
        .requestLevel(RequestsBatchUpdate.RequestLevelEnum.TITLE))),
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(requestService, times(1)).reorderRequestsQueueForInstance(
      instanceId, firstTenant, reorderQueue);
    verify(requestService, times(0)).reorderRequestsQueueForInstance(
      eq(instanceId), eq(secondTenant), any());
  }

  @ParameterizedTest
  @EnumSource(value = Request.EcsRequestPhaseEnum.class, names = {"PRIMARY", "INTERMEDIATE"})
  void shouldNotReorderSecondaryRequestsWhenPrimaryOrIntermediateRequestsOrderIsUnchanged(
    Request.EcsRequestPhaseEnum ecsPhase) {

    var firstEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);
    var fourthEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 2);

    var firstPrimaryRequest = buildRequest(firstEcsTlr, firstSecondaryRequest, 1, TITLE, ecsPhase);
    var thirdPrimaryRequest = buildRequest(thirdEcsTlr, thirdSecondaryRequest, 3, TITLE, ecsPhase);
    var fourthPrimaryRequest = buildRequest(fourthEcsTlr, fourthSecondaryRequest, 4, TITLE, ecsPhase);
    var reorderedSecondPrimaryRequest = buildRequest(secondEcsTlr, secondSecondaryRequest, 4, TITLE, ecsPhase);

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
    var requestsQueue = Stream.of(firstPrimaryRequest, reorderedSecondPrimaryRequest, thirdPrimaryRequest,
        fourthPrimaryRequest)
      .sorted(Comparator.comparing(Request::getPosition))
      .toList();
    when(requestService.getRequestsQueueByInstanceId(any())).thenReturn(requestsQueue);
    when(requestService.getRequestsQueueByInstanceId(any(), eq(firstTenant))).thenReturn(
      List.of(firstSecondaryRequest, secondSecondaryRequest));
    when(requestService.getRequestsQueueByInstanceId(any(), eq(secondTenant))).thenReturn(
      List.of(thirdSecondaryRequest, fourthSecondaryRequest));
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr)));

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, CREATED,
      null, new RequestsBatchUpdate()
          .instanceId(instanceId)
          .requestLevel(RequestsBatchUpdate.RequestLevelEnum.TITLE))),
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(requestService, times(0)).reorderRequestsQueueForInstance(
      eq(instanceId), eq(firstTenant), any());
    verify(requestService, times(0)).reorderRequestsQueueForInstance(
      eq(instanceId), eq(secondTenant), any());
  }

  @ParameterizedTest
  @MethodSource("provideLists")
  void shouldNotReorderSecondaryRequestsWhenPrimaryRequestsAreNullOrEmpty(
    List<EcsTlrEntity> primaryRequests) {

    var firstEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);
    var fourthEcsTlr = buildEcsTlrTitleLevel(instanceId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 2);

    var firstPrimaryRequest = buildPrimaryRequest(firstEcsTlr, firstSecondaryRequest, 1, TITLE);
    var thirdPrimaryRequest = buildPrimaryRequest(thirdEcsTlr, thirdSecondaryRequest, 3, TITLE);
    var fourthPrimaryRequest = buildPrimaryRequest(fourthEcsTlr, fourthSecondaryRequest, 4, TITLE);
    var reorderedSecondPrimaryRequest = buildPrimaryRequest(secondEcsTlr, secondSecondaryRequest, 4, TITLE);

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
    var requestsQueue = Stream.of(firstPrimaryRequest, reorderedSecondPrimaryRequest, thirdPrimaryRequest,
        fourthPrimaryRequest)
      .sorted(Comparator.comparing(Request::getPosition))
      .toList();
    when(requestService.getRequestsQueueByInstanceId(any())).thenReturn(requestsQueue);
    when(requestService.getRequestsQueueByInstanceId(any(), eq(firstTenant))).thenReturn(
      List.of(firstSecondaryRequest, secondSecondaryRequest));
    when(requestService.getRequestsQueueByInstanceId(any(), eq(secondTenant))).thenReturn(
      List.of(thirdSecondaryRequest, fourthSecondaryRequest));
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(primaryRequests);

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, CREATED,
      null, new RequestsBatchUpdate()
        .instanceId(instanceId)
        .requestLevel(RequestsBatchUpdate.RequestLevelEnum.TITLE))),
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(requestService, times(0)).reorderRequestsQueueForInstance(
      eq(instanceId), eq(firstTenant), any());
    verify(requestService, times(0)).reorderRequestsQueueForInstance(
      eq(instanceId), eq(secondTenant), any());
  }

  @ParameterizedTest
  @EnumSource(value = Request.EcsRequestPhaseEnum.class, names = {"PRIMARY", "INTERMEDIATE"})
  void shouldReorderTwoSecondaryRequestsWhenPrimaryOrIntermediateItemLevelRequestsReordered(
    Request.EcsRequestPhaseEnum ecsPhase) {

    var firstEcsTlr = buildEcsTlrItemLevel(itemId, requesterId, pickupServicePointId, firstTenant);
    var secondEcsTlr = buildEcsTlrItemLevel(itemId, requesterId, pickupServicePointId, firstTenant);
    var thirdEcsTlr = buildEcsTlrItemLevel(itemId, requesterId, pickupServicePointId, secondTenant);
    var fourthEcsTlr = buildEcsTlrItemLevel(itemId, requesterId, pickupServicePointId, secondTenant);

    var firstSecondaryRequest = buildSecondaryRequest(firstEcsTlr, 1);
    var secondSecondaryRequest = buildSecondaryRequest(secondEcsTlr, 2);
    var thirdSecondaryRequest = buildSecondaryRequest(thirdEcsTlr, 1);
    var fourthSecondaryRequest = buildSecondaryRequest(fourthEcsTlr, 2);

    var secondPrimaryRequest = buildRequest(secondEcsTlr, secondSecondaryRequest, 2, ITEM, ecsPhase);
    var thirdPrimaryRequest = buildRequest(thirdEcsTlr, thirdSecondaryRequest, 3, ITEM, ecsPhase);
    var fourthPrimaryRequest = buildRequest(fourthEcsTlr, fourthSecondaryRequest, 4, ITEM, ecsPhase);
    var reorderedFirstPrimaryRequest = buildRequest(firstEcsTlr, firstSecondaryRequest, 4, ITEM, ecsPhase);

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
    when(requestService.getRequestsQueueByItemId(any())).thenReturn(requestsQueue);
    when(requestService.getRequestsQueueByItemId(any(), eq(firstTenant))).thenReturn(
      List.of(firstSecondaryRequest, secondSecondaryRequest));
    when(requestService.getRequestsQueueByItemId(any(), eq(secondTenant))).thenReturn(
      List.of(thirdSecondaryRequest, fourthSecondaryRequest));
    when(ecsTlrRepository.findByPrimaryRequestIdIn(any())).thenReturn(List.of(
      ecsTlrMapper.mapDtoToEntity(firstEcsTlr), ecsTlrMapper.mapDtoToEntity(secondEcsTlr),
      ecsTlrMapper.mapDtoToEntity(thirdEcsTlr), ecsTlrMapper.mapDtoToEntity(fourthEcsTlr)));

    List<Request> secRequestsWithUpdatedPositions = List.of(
      new Request()
        .id(firstSecondaryRequest.getId())
        .position(2),
      new Request()
        .id(secondSecondaryRequest.getId())
        .position(1));
    ReorderQueue reorderQueue = createReorderQueue(secRequestsWithUpdatedPositions);
    when(requestService.reorderRequestsQueueForItem(itemId, firstTenant, reorderQueue))
      .thenReturn(secRequestsWithUpdatedPositions);

    eventListener.handleRequestBatchUpdateEvent(serializeEvent(buildEvent(CENTRAL_TENANT_ID, CREATED,
        null, new RequestsBatchUpdate()
          .instanceId(instanceId)
          .itemId(itemId)
          .requestLevel(RequestsBatchUpdate.RequestLevelEnum.ITEM))),
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(requestService, times(1)).reorderRequestsQueueForItem(
      itemId, firstTenant, reorderQueue);
    verify(requestService, times(0)).reorderRequestsQueueForItem(
      eq(itemId), eq(secondTenant), any());
  }

  private static Stream<Arguments> provideLists() {
    return Stream.of(
      Arguments.of(Collections.emptyList()),
      Arguments.of((List<EcsTlrEntity>) null)
    );
  }

  private static EcsTlr buildEcsTlrTitleLevel(String instanceId, String requesterId,
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

  private static EcsTlr buildEcsTlrItemLevel(String itemId, String requesterId,
    String pickupServicePointId, String secondaryRequestTenantId) {

    return new EcsTlr()
      .id(randomId())
      .itemId(itemId)
      .requesterId(requesterId)
      .pickupServicePointId(pickupServicePointId)
      .requestLevel(EcsTlr.RequestLevelEnum.ITEM)
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

  private static Request buildPrimaryRequest(EcsTlr ecsTlr, Request secondaryRequest, int position,
    Request.RequestLevelEnum requestLevel) {

    return buildRequest(ecsTlr, secondaryRequest, position, requestLevel,
      Request.EcsRequestPhaseEnum.PRIMARY);
  }

  private static Request buildRequest(EcsTlr ecsTlr, Request secondaryRequest, int position,
    Request.RequestLevelEnum requestLevel, Request.EcsRequestPhaseEnum ecsPhase) {

    return new Request()
      .id(ecsTlr.getPrimaryRequestId())
      .instanceId(secondaryRequest.getInstanceId())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestExpirationDate(secondaryRequest.getRequestExpirationDate())
      .requestLevel(requestLevel)
      .requestType(Request.RequestTypeEnum.HOLD)
      .ecsRequestPhase(ecsPhase)
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

  private ReorderQueue createReorderQueue(List<Request> requests) {
    ReorderQueue reorderQueue = new ReorderQueue();
    requests.forEach(request -> reorderQueue.addReorderedQueueItem(new ReorderQueueReorderedQueueInner(
      request.getId(), request.getPosition())));

    return reorderQueue;
  }
}
