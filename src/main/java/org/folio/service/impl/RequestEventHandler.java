package org.folio.service.impl;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.SECONDARY;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.AWAITING_PICKUP;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.OPEN;
import static org.folio.support.KafkaEvent.EventType.UPDATED;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.Request.EcsRequestPhaseEnum;
import org.folio.domain.dto.Request.FulfillmentPreferenceEnum;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.CloningService;
import org.folio.service.DcbService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class RequestEventHandler implements KafkaEventHandler<Request> {

  private final DcbService dcbService;
  private final EcsTlrRepository ecsTlrRepository;
  private final SystemUserScopedExecutionService executionService;
  private final ServicePointService servicePointService;
  private final CloningService<ServicePoint> servicePointCloningService;
  private final RequestService requestService;

  @Override
  public void handle(KafkaEvent<Request> event) {
    log.info("handle:: processing request event: {}", event::getId);
    if (event.getType() == UPDATED) {
      handleRequestUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getType);
    }
    log.info("handle:: request event processed: {}", event::getId);
  }

  private void handleRequestUpdateEvent(KafkaEvent<Request> event) {
    log.info("handleRequestUpdateEvent:: handling request update event: {}", event::getId);
    Request updatedRequest = event.getData().getNewVersion();
    if (updatedRequest == null) {
      log.warn("handleRequestUpdateEvent:: event does not contain new version of request");
      return;
    }
    if (updatedRequest.getEcsRequestPhase() == null) {
      log.info("handleRequestUpdateEvent:: updated request is not an ECS request");
      return;
    }
    if (updatedRequest.getEcsRequestPhase() == SECONDARY && updatedRequest.getItemId() == null) {
      log.info("handleRequestUpdateEvent:: updated secondary request does not contain itemId");
      return;
    }

    String requestId = updatedRequest.getId();
    log.info("handleRequestUpdateEvent:: looking for ECS TLR for request {}", requestId);
    // we can search by either primary or secondary request ID, they are identical
    ecsTlrRepository.findBySecondaryRequestId(UUID.fromString(requestId)).ifPresentOrElse(
      ecsTlr -> handleRequestUpdateEvent(ecsTlr, event),
      () -> log.info("handleSecondaryRequestUpdate: ECS TLR for request {} not found", requestId));
  }

  private void handleRequestUpdateEvent(EcsTlrEntity ecsTlr, KafkaEvent<Request> event) {
    log.debug("handleRequestUpdateEvent:: ecsTlr={}", () -> ecsTlr);
    Request updatedRequest = event.getData().getNewVersion();

    if (!requestMatchesEcsTlr(ecsTlr, updatedRequest, event.getTenantIdHeaderValue())) {
      return;
    }
    if (updatedRequest.getEcsRequestPhase() == PRIMARY) {
      handlePrimaryRequestUpdate(ecsTlr, event);
    }
    if (updatedRequest.getEcsRequestPhase() == SECONDARY) {
      handleSecondaryRequestUpdate(ecsTlr, event);
    }
  }

  private static boolean requestMatchesEcsTlr(EcsTlrEntity ecsTlr, Request updatedRequest,
    String updatedRequestTenant) {

    final EcsRequestPhaseEnum updatedRequestPhase = updatedRequest.getEcsRequestPhase();
    final UUID updatedRequestId = UUID.fromString(updatedRequest.getId());

    if (updatedRequestPhase == PRIMARY && updatedRequestId.equals(ecsTlr.getPrimaryRequestId())
      && updatedRequestTenant.equals(ecsTlr.getPrimaryRequestTenantId())) {
      log.info("requestMatchesEcsTlr:: updated primary request matches ECS TLR");
      return true;
    } else if (updatedRequestPhase == SECONDARY && updatedRequestId.equals(ecsTlr.getSecondaryRequestId())
      && updatedRequestTenant.equals(ecsTlr.getSecondaryRequestTenantId())) {
      log.info("requestMatchesEcsTlr:: updated secondary request matches ECS TLR");
      return true;
    }
    log.warn("requestMatchesEcsTlr:: request does not match ECS TLR: updatedRequestPhase={}, " +
        "updatedRequestId={}, updatedRequestTenant={}, ecsTlr={}", updatedRequestPhase,
      updatedRequestId, updatedRequestTenant, ecsTlr);
    return false;
  }

  private void handlePrimaryRequestUpdate(EcsTlrEntity ecsTlr, KafkaEvent<Request> event) {
    propagateChangesFromPrimaryToSecondaryRequest(ecsTlr, event);
    updateDcbTransaction(ecsTlr.getPrimaryRequestDcbTransactionId(),
      ecsTlr.getPrimaryRequestTenantId(), event);
  }

  private void handleSecondaryRequestUpdate(EcsTlrEntity ecsTlr, KafkaEvent<Request> event) {
    processItemIdUpdate(ecsTlr, event.getData().getNewVersion());
    updateDcbTransaction(ecsTlr.getSecondaryRequestDcbTransactionId(),
      ecsTlr.getSecondaryRequestTenantId(), event);
  }

  private void processItemIdUpdate(EcsTlrEntity ecsTlr, Request updatedRequest) {
    if (ecsTlr.getItemId() != null) {
      log.info("processItemIdUpdate:: ECS TLR {} already has itemId {}", ecsTlr::getId, ecsTlr::getItemId);
      return;
    }
    log.info("processItemIdUpdate:: updating ECS TLR {} with itemId {}", ecsTlr::getId,
      updatedRequest::getItemId);
    ecsTlr.setItemId(UUID.fromString(updatedRequest.getItemId()));
    dcbService.createLendingTransaction(ecsTlr);
    dcbService.createBorrowingTransaction(ecsTlr, updatedRequest);
    ecsTlrRepository.save(ecsTlr);
    log.info("processItemIdUpdate: ECS TLR {} is updated", ecsTlr::getId);
  }

  private void updateDcbTransaction(UUID transactionId, String tenant, KafkaEvent<Request> event) {
    determineNewTransactionStatus(event)
      .ifPresent(newStatus -> updateTransactionStatus(transactionId, newStatus, tenant));
  }

  private static Optional<TransactionStatus.StatusEnum> determineNewTransactionStatus(
    KafkaEvent<Request> event) {

    final Request.StatusEnum oldRequestStatus = event.getData().getOldVersion().getStatus();
    final Request.StatusEnum newRequestStatus = event.getData().getNewVersion().getStatus();
    log.info("determineNewTransactionStatus:: oldRequestStatus='{}', newRequestStatus='{}'",
      oldRequestStatus, newRequestStatus);

    if (newRequestStatus == oldRequestStatus) {
      log.info("determineNewTransactionStatus:: request status did not change");
      return Optional.empty();
    }

    var newTransactionStatus = Optional.ofNullable(
      switch (newRequestStatus) {
        case OPEN_IN_TRANSIT -> OPEN;
        case OPEN_AWAITING_PICKUP -> AWAITING_PICKUP;
        case CLOSED_FILLED -> ITEM_CHECKED_OUT;
        default -> null;
      });

    newTransactionStatus.ifPresentOrElse(
      ts -> log.info("determineNewTransactionStatus:: new transaction status: {}", ts),
      () -> log.info("determineNewTransactionStatus:: irrelevant request status change"));

    return newTransactionStatus;
  }

  private void updateTransactionStatus(UUID transactionId,
    TransactionStatus.StatusEnum newTransactionStatus, String tenant) {

    try {
      var currentStatus = dcbService.getTransactionStatus(transactionId, tenant).getStatus();
      log.info("updateTransactionStatus:: current transaction status: {}", currentStatus);
      if (newTransactionStatus.getValue().equals(currentStatus.getValue())) {
        log.info("updateTransactionStatus:: transaction status did not change, doing nothing");
        return;
      }
      dcbService.updateTransactionStatus(transactionId, newTransactionStatus, tenant);
    } catch (FeignException.NotFound e) {
      log.error("updateTransactionStatus:: transaction {} not found: {}", transactionId, e.getMessage());
    }
  }

  private void propagateChangesFromPrimaryToSecondaryRequest(EcsTlrEntity ecsTlr,
    KafkaEvent<Request> event) {

    String secondaryRequestId = ecsTlr.getSecondaryRequestId().toString();
    String secondaryRequestTenantId = ecsTlr.getSecondaryRequestTenantId();
    Request primaryRequest = event.getData().getNewVersion();
    Request secondaryRequest = requestService.getRequestFromStorage(
      secondaryRequestId, secondaryRequestTenantId);

    boolean shouldUpdateSecondaryRequest = false;
    if (valueIsNotEqual(primaryRequest, secondaryRequest, Request::getRequestExpirationDate)) {
      Date requestExpirationDate = primaryRequest.getRequestExpirationDate();
      log.info("propagateChangesFromPrimaryToSecondaryRequest:: request expiration date changed: {}",
        requestExpirationDate);
      secondaryRequest.setRequestExpirationDate(requestExpirationDate);
      shouldUpdateSecondaryRequest = true;
    }
    if (valueIsNotEqual(primaryRequest, secondaryRequest, Request::getFulfillmentPreference)) {
      FulfillmentPreferenceEnum fulfillmentPreference = primaryRequest.getFulfillmentPreference();
      log.info("propagateChangesFromPrimaryToSecondaryRequest:: fulfillment preference changed: {}",
        fulfillmentPreference);
      secondaryRequest.setFulfillmentPreference(fulfillmentPreference);
      shouldUpdateSecondaryRequest = true;
    }
    if (valueIsNotEqual(primaryRequest, secondaryRequest, Request::getPickupServicePointId)) {
      String pickupServicePointId = primaryRequest.getPickupServicePointId();
      log.info("propagateChangesFromPrimaryToSecondaryRequest:: pickup service point ID changed: {}",
        pickupServicePointId);
      secondaryRequest.setPickupServicePointId(pickupServicePointId);
      shouldUpdateSecondaryRequest = true;
      clonePickupServicePoint(ecsTlr, pickupServicePointId);
    }

    if (valueIsNotEqual(primaryRequest, event.getData().getOldVersion(), Request::getPosition)) {
      log.info("propagateChangesFromPrimaryToSecondaryRequest:: position has been changed");
      updateQueuePositions(event, primaryRequest);
    }

    if (!shouldUpdateSecondaryRequest) {
      log.info("propagateChangesFromPrimaryToSecondaryRequest:: no relevant changes detected");
      return;
    }

    log.info("propagateChangesFromPrimaryToSecondaryRequest:: updating secondary request");
    requestService.updateRequestInStorage(secondaryRequest, secondaryRequestTenantId);
    log.info("propagateChangesFromPrimaryToSecondaryRequest:: secondary request updated");
  }

  private void updateQueuePositions(KafkaEvent<Request> event, Request primaryRequest) {
    log.info("updateQueuePositions:: parameters event: {}, primaryRequest: {}", event, primaryRequest);
    List<Request> unifiedQueue = requestService.getRequestsByInstanceId(primaryRequest.getInstanceId())
      .stream()
      .filter(request -> !request.getId().equals(event.getData().getOldVersion().getId()))
      .collect(Collectors.toList());

    unifiedQueue.add(primaryRequest);
    unifiedQueue.sort(Comparator.comparing(Request::getPosition));
    IntStream.range(0, unifiedQueue.size()).forEach(i -> unifiedQueue.get(i).setPosition(i + 1));

    List<UUID> sortedPrimaryRequestIds = unifiedQueue.stream()
      .filter(request -> PRIMARY == request.getEcsRequestPhase())
      .sorted(Comparator.comparing(Request::getPosition))
      .map(request -> UUID.fromString(request.getId()))
      .toList();

    List<EcsTlrEntity> sortedEcsTlrQueue = sortEcsTlrEntities(sortedPrimaryRequestIds,
      ecsTlrRepository.findByPrimaryRequestIdIn(sortedPrimaryRequestIds));
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId = groupSecondaryRequestsByTenantId(
      sortedEcsTlrQueue);

    reorderSecondaryRequestsQueue(groupedSecondaryRequestsByTenantId, sortedEcsTlrQueue);
  }

  private void reorderSecondaryRequestsQueue(
    Map<String, List<Request>> groupedSecondaryRequestsByTenantId,
    List<EcsTlrEntity> sortedEcsTlrQueue) {

    log.info("reorderSecondaryRequestsQueue:: parameters groupedSecondaryRequestsByTenantId: {}," +
      "sortedEcsTlrQueue: {}", () -> groupedSecondaryRequestsByTenantId, () -> sortedEcsTlrQueue);
    Map<UUID, Integer> secondaryRequestOrder = new HashMap<>();
    for (int i = 0; i < sortedEcsTlrQueue.size(); i++) {
      EcsTlrEntity ecsEntity = sortedEcsTlrQueue.get(i);
      if (ecsEntity.getSecondaryRequestId() != null) {
        secondaryRequestOrder.put(ecsEntity.getSecondaryRequestId(), i + 1);
      }
    }

    groupedSecondaryRequestsByTenantId.forEach((tenantId, secondaryRequests) -> {
      secondaryRequests.sort(Comparator.comparingInt(
        req -> secondaryRequestOrder.getOrDefault(UUID.fromString(req.getId()), Integer.MAX_VALUE)
      ));

      for (int i = 0; i < secondaryRequests.size(); i++) {
        Request request = secondaryRequests.get(i);
        int newPosition = i + 1;
        if (newPosition != request.getPosition()) {
          log.info("reorderSecondaryRequestsQueue:: update position for secondary request: {} , " +
            "with new position: {}, tenant: {}, old position: {}", request, newPosition, tenantId,
            request.getPosition());
          request.setPosition(newPosition);
          requestService.updateRequestInStorage(request, tenantId);
        }
      }
    });
  }

  private void clonePickupServicePoint(EcsTlrEntity ecsTlr, String pickupServicePointId) {
    if (pickupServicePointId == null) {
      log.info("clonePickupServicePoint:: pickupServicePointId is null, doing nothing");
      return;
    }
    log.info("clonePickupServicePoint:: ensuring that service point {} exists in lending tenant",
      pickupServicePointId);
    ServicePoint pickupServicePoint = executionService.executeSystemUserScoped(
      ecsTlr.getPrimaryRequestTenantId(), () -> servicePointService.find(pickupServicePointId));
    executionService.executeSystemUserScoped(ecsTlr.getSecondaryRequestTenantId(),
      () -> servicePointCloningService.clone(pickupServicePoint));
  }

  private static <T, V> boolean valueIsNotEqual(T o1, T o2, Function<T, V> valueExtractor) {
    return !Objects.equals(valueExtractor.apply(o1), valueExtractor.apply(o2));
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

    List<EcsTlrEntity> sortedEcsTlrQueue = new ArrayList<>(ecsTlrQueue);
    Map<UUID, Integer> indexMap = new HashMap<>();
    for (int i = 0; i < sortedPrimaryRequestIds.size(); i++) {
      indexMap.put(sortedPrimaryRequestIds.get(i), i);
    }

    sortedEcsTlrQueue.sort(Comparator.comparingInt(entity -> indexMap.getOrDefault(
      entity.getPrimaryRequestId(), Integer.MAX_VALUE)));

    return sortedEcsTlrQueue;
  }
}
