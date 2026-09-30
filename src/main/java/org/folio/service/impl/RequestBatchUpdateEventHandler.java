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

    // MCBFF-211 diagnostics: raw event as received, plus which tenant emitted it (the Kafka
    // topic is tenant-scoped, but this handler always executes under the Central tenant
    // context - see KafkaEventListener.handleEvent). If instanceId/requestLevel here doesn't
    // match what we'd expect for a plain item-level Recall (e.g. requestLevel=TITLE even
    // though the Recall itself is ITEM level), that confirms the batch-level (not per-request)
    // requestLevel tagging done in mod-circulation-storage is bleeding into this handler.
    log.info("MCBFF-211 handle:: event tenant: {}, instanceId: {}, itemId: {}, " +
        "requestLevel: {}, requestIds: {}", event.getTenantIdHeaderValue(),
      requestsBatchUpdate.getInstanceId(), requestsBatchUpdate.getItemId(),
      requestsBatchUpdate.getRequestLevel(), requestsBatchUpdate.getRequestIds());

    if (isUnifiedQueue(requestsBatchUpdate)) {
      updatePositionsForUnifiedQueue(requestsBatchUpdate.getInstanceId());
    } else {
      updatePositionsForItemLevelQueue(requestsBatchUpdate.getItemId());
    }

    log.info("handle:: requests batch update event processed: {}", event::getId);
  }

  private static boolean isUnifiedQueue(RequestsBatchUpdate requestsBatchUpdate) {
    return TITLE.getValue().equals(requestsBatchUpdate.getRequestLevel().getValue());
  }

  private void updatePositionsForUnifiedQueue(String instanceId) {
    updateQueuePositions(requestService.getRequestsQueueByInstanceId(instanceId), true);
  }

  private void updatePositionsForItemLevelQueue(String itemId) {
    updateQueuePositions(requestService.getRequestsQueueByItemId(itemId), false);
  }

  private void updateQueuePositions(List<Request> queue, boolean isUnifiedQueue) {
    log.debug("updateQueuePositions:: parameters queue: {}, isUnifiedQueue: {}", queue, isUnifiedQueue);

    // MCBFF-211 diagnostics: full snapshot of the Central-tenant queue as fetched for this
    // instance/item, BEFORE any ECS-phase filtering. This is the queue mod-tlr considers
    // "authoritative" for computing secondary-request positions. Any request that shows up
    // here with ecsRequestPhase=null (e.g. a plain item-level Recall) is a real member of
    // this queue for display/local-resequencing purposes, but will be silently dropped from
    // sortedRequestIds below.
    queue.forEach(r -> log.info(
      "MCBFF-211 updateQueuePositions:: queue member -> id: {}, itemId: {}, instanceId: {}, " +
        "requestLevel: {}, requestType: {}, ecsRequestPhase: {}, position: {}, status: {}",
      r.getId(), r.getItemId(), r.getInstanceId(), r.getRequestLevel(), r.getRequestType(),
      r.getEcsRequestPhase(), r.getPosition(), r.getStatus()));

    List<UUID> sortedRequestIds = queue.stream()
      .filter(request -> PRIMARY == request.getEcsRequestPhase() ||
        INTERMEDIATE == request.getEcsRequestPhase())
      .filter(request -> request.getPosition() != null)
      .sorted(Comparator.comparing(Request::getPosition))
      .map(request -> UUID.fromString(request.getId()))
      .toList();
    log.debug("updateQueuePositions:: sortedRequestIds: {}", sortedRequestIds);

    // MCBFF-211 diagnostics: requests present in the full queue above but EXCLUDED here
    // (no PRIMARY/INTERMEDIATE ecsRequestPhase, or null position) - these are invisible to
    // the cross-tenant position-sync computation below, even though they still occupy a
    // position slot in the real queue.
    List<String> excludedFromSync = queue.stream()
      .filter(request -> !(PRIMARY == request.getEcsRequestPhase()
        || INTERMEDIATE == request.getEcsRequestPhase()) || request.getPosition() == null)
      .map(r -> "id=" + r.getId() + ",level=" + r.getRequestLevel() + ",type="
        + r.getRequestType() + ",phase=" + r.getEcsRequestPhase() + ",position=" + r.getPosition())
      .toList();
    log.info("MCBFF-211 updateQueuePositions:: requests EXCLUDED from ECS position sync " +
      "(no EcsTlrEntity linkage possible): {}", excludedFromSync);
    log.info("MCBFF-211 updateQueuePositions:: sortedRequestIds (PRIMARY/INTERMEDIATE only, " +
      "in position order): {}", sortedRequestIds);

    // Primary and intermediate request within the same ECS TLR share the same ID, so
    // we can search by either one
    List<EcsTlrEntity> ecsTlrs = ecsTlrRepository.findByPrimaryRequestIdIn(sortedRequestIds);
    if (ecsTlrs == null || ecsTlrs.isEmpty()) {
      log.warn("updateQueuePositions:: no corresponding ECS TLRs found");
      return;
    }
    List<EcsTlrEntity> sortedEcsTlrQueue = sortEcsTlrEntities(sortedRequestIds, ecsTlrs);
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId = groupSecondaryRequestsByTenantId(
      sortedEcsTlrQueue);

    reorderSecondaryRequestsQueue(groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue, isUnifiedQueue);
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

    // MCBFF-211 diagnostics: index-position (0-based) in this list is what
    // reorderSecondaryRequestsQueue below uses as the "correct order" for the secondary
    // request. Note that null entries (a sortedRequestIds member with no matching
    // EcsTlrEntity) still occupy a slot here, but are filtered out when computing
    // correctOrder - meaning the index-to-position mapping below is compressed/shifted
    // relative to the true Central-tenant queue whenever any PRIMARY/INTERMEDIATE request in
    // sortedRequestIds isn't resolvable to an EcsTlrEntity (should be rare) - the bigger risk
    // is upstream: items EXCLUDED from sortedRequestIds entirely (see updateQueuePositions
    // log) are never in this list, so they don't even get a "null slot".
    for (int i = 0; i < sortedEcsTlrQueue.size(); i++) {
      EcsTlrEntity e = sortedEcsTlrQueue.get(i);
      log.info("MCBFF-211 sortEcsTlrEntities:: index {} -> primaryRequestId: {}, " +
          "secondaryRequestId: {}, secondaryRequestTenantId: {}",
        i, sortedRequestIds.get(i), e == null ? null : e.getSecondaryRequestId(),
        e == null ? null : e.getSecondaryRequestTenantId());
    }

    return sortedEcsTlrQueue;
  }

  private void reorderSecondaryRequestsQueue(
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId,
    List<EcsTlrEntity> sortedEcsTlrQueue, boolean isUnifiedQueue) {

    log.debug("reorderSecondaryRequestsQueue:: parameters groupedSecondaryRequestsByTenantId: {}, " +
      "sortedEcsTlrQueue: {}", groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue);

    Map<UUID, Integer> correctOrder = IntStream.range(0, sortedEcsTlrQueue.size())
      .boxed()
      .filter(i -> sortedEcsTlrQueue.get(i) != null)
      .collect(Collectors.toMap(
        i -> sortedEcsTlrQueue.get(i).getSecondaryRequestId(),
        i -> i + 1, (existing, replacement) -> existing));

    log.debug("reorderSecondaryRequestsQueue:: correctOrder: {}", correctOrder);

    // MCBFF-211 diagnostics: this is the "correct order" (1-based rank) that will be applied
    // to each secondary request's NEW position value below. Compare this rank against the
    // secondary request's actual current position in its home (Data/Secure) tenant queue -
    // divergence here, specifically an off-by-N shift, is the hypothesized root cause of the
    // Hold/Recall position swap.
    log.info("MCBFF-211 reorderSecondaryRequestsQueue:: correctOrder (secondaryRequestId -> " +
      "rank): {}", correctOrder);

    groupedSecondaryRequestsByTenantId.forEach((tenantId, secondaryRequests) ->
      updateReorderedRequests(reorderSecondaryRequestsForTenant(
        tenantId, secondaryRequests, correctOrder), tenantId, isUnifiedQueue));
  }

  private List<Request> reorderSecondaryRequestsForTenant(String tenantId,
    List<Request> secondaryRequests, Map<UUID, Integer> correctOrder) {

    List<Integer> sortedCurrentPositions = secondaryRequests.stream()
      .map(Request::getPosition)
      .sorted()
      .toList();
    log.debug("reorderSecondaryRequestsForTenant:: sortedCurrentPositions: {}",
      sortedCurrentPositions);

    // MCBFF-211 diagnostics: secondary requests in this tenant's queue BEFORE reordering,
    // with their current position and the "correctOrder" rank that will be used to re-sort
    // them. This is the actual per-tenant secondary queue that this method is about to
    // mutate positions for.
    secondaryRequests.forEach(r -> log.info(
      "MCBFF-211 reorderSecondaryRequestsForTenant:: tenant: {} - secondary request BEFORE " +
        "-> id: {}, itemId: {}, currentPosition: {}, correctOrderRank: {}",
      tenantId, r.getId(), r.getItemId(), r.getPosition(),
      correctOrder.getOrDefault(UUID.fromString(r.getId()), 0)));

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

    // MCBFF-211 diagnostics: the final set of secondary requests that WILL be pushed to the
    // Data/Secure tenant's storage via reorder API, with their NEW position value.
    log.info("MCBFF-211 reorderSecondaryRequestsForTenant:: tenant: {} - requests to be " +
        "updated AFTER reorder: {}", tenantId,
      reorderedRequests.stream()
        .map(r -> "id=" + r.getId() + ",itemId=" + r.getItemId() + ",newPosition="
          + r.getPosition())
        .toList());

    return reorderedRequests;
  }

  private void updateReorderedRequests(List<Request> requestsWithUpdatedPositions, String tenantId,
    boolean isUnifiedQueue) {

    if (requestsWithUpdatedPositions == null || requestsWithUpdatedPositions.isEmpty()) {
      log.info("updateReorderedRequests:: no secondary requests with updated positions");
      return;
    }

    Map<Integer, Request> updatedPositionMap = requestsWithUpdatedPositions.stream()
      .collect(Collectors.toMap(Request::getPosition, Function.identity()));
    List<Request> updatedQueue;

    String id;
    if (isUnifiedQueue) {
      log.info("updateReorderedRequests:: getting requests queue by instanceId");
      id = requestsWithUpdatedPositions.get(0).getInstanceId();
      updatedQueue = new ArrayList<>(requestService.getRequestsQueueByInstanceId(id, tenantId));
    } else {
      log.info("updateReorderedRequests:: getting requests queue by itemId");
      id = requestsWithUpdatedPositions.get(0).getItemId();
      updatedQueue = new ArrayList<>(requestService.getRequestsQueueByItemId(id, tenantId));
    }

    // MCBFF-211 diagnostics: this is the FULL secondary/Data-tenant queue as it exists RIGHT
    // NOW in that tenant's own storage (i.e. its own authoritative view, independent of
    // Central), fetched fresh right before we overwrite positions on it. Compare this
    // against the "MCBFF-211 updateQueuePositions" log from the Central-tenant side above -
    // if this Data-tenant queue contains entries that never appeared in the Central-tenant
    // computation (e.g. because they have no ecsRequestPhase / no EcsTlrEntity, such as a
    // locally-placed item-level Recall living directly in this Data tenant), then this
    // reorder call is about to renumber the Data tenant's queue using an "order" that was
    // computed while blind to that Recall's real position - this is the crux of the
    // hypothesized root cause.
    updatedQueue.forEach(r -> log.info(
      "MCBFF-211 updateReorderedRequests:: tenant: {} - queue member (BEFORE reorder push) " +
        "-> id: {}, itemId: {}, requestLevel: {}, requestType: {}, ecsRequestPhase: {}, " +
        "currentPosition: {}, willBeOverwritten: {}",
      tenantId, r.getId(), r.getItemId(), r.getRequestLevel(), r.getRequestType(),
      r.getEcsRequestPhase(), r.getPosition(),
      updatedPositionMap.containsKey(r.getPosition())));

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

    // MCBFF-211 diagnostics: the exact reorder request body being POSTed to the Data
    // tenant's /queue/{instance|item}/{id}/reorder endpoint - this is what will actually
    // change positions in storage for that tenant.
    log.info("MCBFF-211 updateReorderedRequests:: tenant: {} - final ReorderQueue payload " +
      "being sent to /queue/{}/{}/reorder: {}", tenantId,
      isUnifiedQueue ? "instance" : "item", id, reorderQueue);

    List<Request> requests = isUnifiedQueue
      ? requestService.reorderRequestsQueueForInstance(id, tenantId, reorderQueue)
      : requestService.reorderRequestsQueueForItem(id, tenantId, reorderQueue);

    log.debug("updateReorderedRequests:: result: {}", requests);
    log.info("MCBFF-211 updateReorderedRequests:: tenant: {} - queue AFTER reorder: {}",
      tenantId,
      requests == null ? null : requests.stream()
        .map(r -> "id=" + r.getId() + ",itemId=" + r.getItemId() + ",position="
          + r.getPosition())
        .toList());
  }
}
