package org.folio.service.impl;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.INTERMEDIATE;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.dto.Request.RequestLevelEnum.TITLE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.ReorderQueueReorderedQueueInner;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestsBatchUpdate;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.KafkaEventHandler;
import org.folio.service.RequestService;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class RequestBatchUpdateEventHandler implements KafkaEventHandler<RequestsBatchUpdate> {

  private final RequestService requestService;
  private final EcsTlrRepository ecsTlrRepository;

  @Override
  public void handle(KafkaEvent<RequestsBatchUpdate> event) {
    log.info("handle:: processing requests batch update event: {}", event::getId);
    RequestsBatchUpdate requestsBatchUpdate = event.getNewVersion();

    if (TITLE.getValue().equals(requestsBatchUpdate.getRequestLevel().getValue())) {
      updateQueuePositionsForTitleLevel(requestsBatchUpdate.getInstanceId());
    } else {
      updateQueuePositionsForItemLevel(requestsBatchUpdate.getItemId());
    }

    log.info("handle:: requests batch update event processed: {}", event::getId);
  }

  private void updateQueuePositionsForTitleLevel(String instanceId) {
    updateQueuePositions(requestService.getRequestsQueueByInstanceId(instanceId), true);
  }

  private void updateQueuePositionsForItemLevel(String itemId) {
    updateQueuePositions(requestService.getRequestsQueueByItemId(itemId), false);
  }

  private void updateQueuePositions(List<Request> unifiedQueue, boolean isTlrRequestQueue) {
    log.debug("updateQueuePositions:: parameters unifiedQueue: {}", unifiedQueue);
    List<UUID> sortedRequestIds = unifiedQueue.stream()
      .filter(request -> PRIMARY == request.getEcsRequestPhase() ||
        INTERMEDIATE == request.getEcsRequestPhase())
      .filter(request -> request.getPosition() != null)
      .sorted(Comparator.comparing(Request::getPosition))
      .map(request -> UUID.fromString(request.getId()))
      .toList();
    log.debug("updateQueuePositions:: sortedRequestIds: {}", sortedRequestIds);

    // Primary and intermediate request within the same ECS TLR share the same ID, so
    // we can search by either one
    List<EcsTlrEntity> ecsTlrs = ecsTlrRepository.findByPrimaryRequestIdIn(
      sortedRequestIds);
    if (ecsTlrs == null || ecsTlrs.isEmpty()) {
      log.warn("updateQueuePositions:: no corresponding ECS TLR found");
      return;
    }
    List<EcsTlrEntity> sortedEcsTlrQueue = sortEcsTlrEntities(sortedRequestIds,
      ecsTlrs);
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId = groupSecondaryRequestsByTenantId(
      sortedEcsTlrQueue);

    reorderSecondaryRequestsQueue(groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue, isTlrRequestQueue);
  }

  private Map<String, List<Request>> groupSecondaryRequestsByTenantId(
    List<EcsTlrEntity> sortedEcsTlrQueue) {

    return sortedEcsTlrQueue.stream()
      .filter(Objects::nonNull)
      .filter(entity -> entity.getSecondaryRequestTenantId() != null &&
        entity.getSecondaryRequestId() != null)
      .collect(groupingBy(EcsTlrEntity::getSecondaryRequestTenantId,
        mapping(entity -> requestService.getRequestFromStorage(
            entity.getSecondaryRequestId().toString(), entity.getSecondaryRequestTenantId()),
          Collectors.toList())
      ));
  }

  private List<EcsTlrEntity> sortEcsTlrEntities(List<UUID> sortedRequestIds,
    List<EcsTlrEntity> ecsTlrQueue) {

    log.debug("sortEcsTlrEntities:: parameters sortedRequestIds: {}, ecsTlrQueue: {}",
      sortedRequestIds, ecsTlrQueue);
    Map<UUID, EcsTlrEntity> ecsTlrByPrimaryRequestId = ecsTlrQueue.stream()
      .collect(toMap(EcsTlrEntity::getPrimaryRequestId, Function.identity()));
    List<EcsTlrEntity> sortedEcsTlrQueue = sortedRequestIds
      .stream()
      .map(ecsTlrByPrimaryRequestId::get)
      .toList();
    log.debug("sortEcsTlrEntities:: result: {}", sortedEcsTlrQueue);

    return sortedEcsTlrQueue;
  }

  private void reorderSecondaryRequestsQueue(
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId,
    List<EcsTlrEntity> sortedEcsTlrQueue, boolean isTlrRequestQueue) {

    log.debug("reorderSecondaryRequestsQueue:: parameters groupedSecondaryRequestsByTenantId: {}, " +
      "sortedEcsTlrQueue: {}", groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue);

    Map<UUID, Integer> correctOrder = IntStream.range(0, sortedEcsTlrQueue.size())
      .boxed()
      .filter(i -> sortedEcsTlrQueue.get(i) != null)
      .collect(Collectors.toMap(
        i -> sortedEcsTlrQueue.get(i).getSecondaryRequestId(),
        i -> i + 1, (existing, replacement) -> existing));

    log.debug("reorderSecondaryRequestsQueue:: correctOrder: {}", correctOrder);

    groupedSecondaryRequestsByTenantId.forEach((tenantId, secondaryRequests) ->
      updateReorderedRequests(reorderSecondaryRequestsForTenant(
        tenantId, secondaryRequests, correctOrder), tenantId, isTlrRequestQueue));
  }

  private List<Request> reorderSecondaryRequestsForTenant(String tenantId,
    List<Request> secondaryRequests, Map<UUID, Integer> correctOrder) {

    List<Integer> sortedCurrentPositions = secondaryRequests.stream()
      .map(Request::getPosition)
      .sorted()
      .toList();
    log.debug("reorderSecondaryRequestsForTenant:: sortedCurrentPositions: {}",
      sortedCurrentPositions);

    secondaryRequests.sort(Comparator.comparingInt(r -> correctOrder.getOrDefault(
      UUID.fromString(r.getId()), 0)));

    List<Request> reorderedRequests = new ArrayList<>();
    IntStream.range(0, secondaryRequests.size()).forEach(i -> {
      Request request = secondaryRequests.get(i);
      int updatedPosition = sortedCurrentPositions.get(i);

      if (request.getPosition() != updatedPosition) {
        log.info("reorderSecondaryRequestsForTenant:: swap positions: {} <-> {}, for tenant: {}",
          request.getPosition(), updatedPosition, tenantId);
        request.setPosition(updatedPosition);
        reorderedRequests.add(request);
        log.debug("reorderSecondaryRequestsForTenant:: request {} updated", request);
      }
    });
    return reorderedRequests;
  }

  private void updateReorderedRequests(List<Request> requestsWithUpdatedPositions, String tenantId,
    boolean isTlrRequestQueue) {

    if (requestsWithUpdatedPositions == null || requestsWithUpdatedPositions.isEmpty()) {
      log.info("updateReorderedRequests:: no secondary requests with updated positions");
      return;
    }

    Map<Integer, Request> updatedPositionMap = requestsWithUpdatedPositions.stream()
      .collect(Collectors.toMap(Request::getPosition, Function.identity()));
    List<Request> updatedQueue;

    String id;
    if (isTlrRequestQueue) {
      log.info("updateReorderedRequests:: getting requests queue by instanceId");
      id = requestsWithUpdatedPositions.get(0).getInstanceId();
      updatedQueue = new ArrayList<>(requestService.getRequestsQueueByInstanceId(id, tenantId));
    } else {
      log.info("updateReorderedRequests:: getting requests queue by itemId");
      id = requestsWithUpdatedPositions.get(0).getItemId();
      updatedQueue = new ArrayList<>(requestService.getRequestsQueueByItemId(id, tenantId));
    }

    for (int i = 0; i < updatedQueue.size(); i++) {
      Request currentRequest = updatedQueue.get(i);
      if (updatedPositionMap.containsKey(currentRequest.getPosition())) {
        updatedQueue.set(i, updatedPositionMap.get(currentRequest.getPosition()));
      }
    }
    ReorderQueue reorderQueue = new ReorderQueue();
    updatedQueue.forEach(request -> reorderQueue.addReorderedQueueItem(
      new ReorderQueueReorderedQueueInner()
        .id(request.getId())
        .newPosition(request.getPosition())));
    log.debug("updateReorderedRequests:: reorderQueue: {}", reorderQueue);

    List<Request> requests = isTlrRequestQueue
      ? requestService.reorderRequestsQueueForInstance(id, tenantId, reorderQueue)
      : requestService.reorderRequestsQueueForItem(id, tenantId, reorderQueue);

    log.debug("updateReorderedRequests:: result: {}", requests);
  }
}
