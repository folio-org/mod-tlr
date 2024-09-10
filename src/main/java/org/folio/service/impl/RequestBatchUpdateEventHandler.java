package org.folio.service.impl;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import org.folio.support.KafkaEvent;
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
    updateQueuePositions(event.getData().getNewVersion().getInstanceId());

    log.info("handle:: requests batch update event processed: {}", event::getId);
  }

  private void updateQueuePositions(String instanceId) {
    log.info("updateQueuePositions:: parameters instanceId: {}", instanceId);

    var unifiedQueue = requestService.getRequestsQueueByInstanceId(instanceId);
    log.debug("updateQueuePositions:: unifiedQueue: {}", unifiedQueue);

    List<UUID> sortedPrimaryRequestIds = unifiedQueue.stream()
      .filter(request -> PRIMARY == request.getEcsRequestPhase())
      .filter(request -> request.getPosition() != null)
      .sorted(Comparator.comparing(Request::getPosition))
      .map(request -> UUID.fromString(request.getId()))
      .toList();
    log.info("updateQueuePositions:: sortedPrimaryRequestIds: {}", sortedPrimaryRequestIds);

    List<EcsTlrEntity> ecsTlrByPrimaryRequests = ecsTlrRepository.findByPrimaryRequestIdIn(
      sortedPrimaryRequestIds);
    if (ecsTlrByPrimaryRequests == null || ecsTlrByPrimaryRequests.isEmpty()) {
      log.warn("updateQueuePositions:: no corresponding ECS TLR found");
      return;
    }
    List<EcsTlrEntity> sortedEcsTlrQueue = sortEcsTlrEntities(sortedPrimaryRequestIds,
      ecsTlrByPrimaryRequests);
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId = groupSecondaryRequestsByTenantId(
      sortedEcsTlrQueue);

    reorderSecondaryRequestsQueue(groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue);
  }

  private Map<String, List<Request>> groupSecondaryRequestsByTenantId(
    List<EcsTlrEntity> sortedEcsTlrQueue) {

    return sortedEcsTlrQueue.stream()
      .filter(entity -> entity.getSecondaryRequestTenantId() != null &&
        entity.getSecondaryRequestId() != null)
      .collect(groupingBy(EcsTlrEntity::getSecondaryRequestTenantId,
        mapping(entity -> requestService.getRequestFromStorage(
            entity.getSecondaryRequestId().toString(), entity.getSecondaryRequestTenantId()),
          Collectors.toList())
      ));
  }

  private List<EcsTlrEntity> sortEcsTlrEntities(List<UUID> sortedPrimaryRequestIds,
    List<EcsTlrEntity> ecsTlrQueue) {

    log.info("sortEcsTlrEntities:: parameters sortedPrimaryRequestIds: {}, ecsTlrQueue: {}",
      sortedPrimaryRequestIds, ecsTlrQueue);
    Map<UUID, EcsTlrEntity> ecsTlrByPrimaryRequestId = ecsTlrQueue.stream()
      .collect(toMap(EcsTlrEntity::getPrimaryRequestId, Function.identity()));
    List<EcsTlrEntity> sortedEcsTlrQueue = sortedPrimaryRequestIds
      .stream()
      .map(ecsTlrByPrimaryRequestId::get)
      .toList();
    log.info("sortEcsTlrEntities:: result: {}", sortedEcsTlrQueue);

    return sortedEcsTlrQueue;
  }

  private void reorderSecondaryRequestsQueue(
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId,
    List<EcsTlrEntity> sortedEcsTlrQueue) {

    log.info("reorderSecondaryRequestsQueue:: parameters groupedSecondaryRequestsByTenantId: {}," +
      "sortedEcsTlrQueue: {}", groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue);

    Map<UUID, Integer> correctOrder = IntStream.range(0, sortedEcsTlrQueue.size())
      .boxed()
      .collect(Collectors.toMap(
        i -> sortedEcsTlrQueue.get(i).getSecondaryRequestId(),
        i -> i + 1));
    log.debug("reorderSecondaryRequestsQueue:: correctOrder: {}", correctOrder);

    groupedSecondaryRequestsByTenantId.forEach((tenantId, secondaryRequests) ->
      updateReorderedRequests(reorderSecondaryRequestsForTenant(tenantId, secondaryRequests,
        correctOrder), tenantId));
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

  private void updateReorderedRequests(List<Request> requestsWithUpdatedPositions,
    String tenantId) {

    if (requestsWithUpdatedPositions == null || requestsWithUpdatedPositions.isEmpty()) {
      log.info("updateReorderedRequests:: no secondary requests with updated positions");
      return;
    }

    Map<Integer, Request> updatedPositionMap = requestsWithUpdatedPositions.stream()
      .collect(Collectors.toMap(Request::getPosition, request -> request));
    String instanceId = requestsWithUpdatedPositions.get(0).getInstanceId();
    List<Request> updatedQueue = new ArrayList<>(requestService.getRequestsQueueByInstanceId(
      instanceId, tenantId));

    for (int i = 0; i < updatedQueue.size(); i++) {
      Request currentRequest = updatedQueue.get(i);
      if (updatedPositionMap.containsKey(currentRequest.getPosition())) {
        updatedQueue.set(i, updatedPositionMap.get(currentRequest.getPosition()));
      }
    }
    ReorderQueue reorderQueue = new ReorderQueue();
    updatedQueue.forEach(request -> reorderQueue.addReorderedQueueItem(new ReorderQueueReorderedQueueInner()
        .id(request.getId())
        .newPosition(request.getPosition())));
    log.info("updateReorderedRequests:: reorderQueue: {}", reorderQueue);

    List<Request> requests = requestService.reorderRequestsQueueForInstance(instanceId, tenantId,
      reorderQueue);
    log.debug("updateReorderedRequests:: result: {}", requests);
  }
}
