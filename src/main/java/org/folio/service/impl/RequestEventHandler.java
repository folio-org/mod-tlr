package org.folio.service.impl;

import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.SECONDARY;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.AWAITING_PICKUP;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.CANCELLED;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.OPEN;
import static org.folio.support.KafkaEvent.EventType.UPDATED;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

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
    updateTransactionStatuses(event, ecsTlr);
  }

  private void handleSecondaryRequestUpdate(EcsTlrEntity ecsTlr, KafkaEvent<Request> event) {
    processItemIdUpdate(ecsTlr, event.getData().getNewVersion());
    updateTransactionStatuses(event, ecsTlr);
  }

  private void processItemIdUpdate(EcsTlrEntity ecsTlr, Request updatedRequest) {
    if (ecsTlr.getItemId() != null) {
      log.info("processItemIdUpdate:: ECS TLR {} already has itemId {}", ecsTlr::getId, ecsTlr::getItemId);
      return;
    }
    log.info("processItemIdUpdate:: updating ECS TLR {} with itemId {}", ecsTlr::getId,
      updatedRequest::getItemId);
    ecsTlr.setItemId(UUID.fromString(updatedRequest.getItemId()));
    // TODO: change this if Page request works
    dcbService.createTransactions(ecsTlr, updatedRequest);
    ecsTlrRepository.save(ecsTlr);
    log.info("processItemIdUpdate: ECS TLR {} is updated", ecsTlr::getId);
  }

  private static Optional<TransactionStatus.StatusEnum> determineNewTransactionStatus(
    KafkaEvent<Request> event) {

    final Request.StatusEnum oldRequestStatus = event.getData().getOldVersion().getStatus();
    final Request.StatusEnum newRequestStatus = event.getData().getNewVersion().getStatus();
    log.info("determineNewTransactionStatus:: oldRequestStatus='{}', newRequestStatus='{}'",
      oldRequestStatus, newRequestStatus);

    if (newRequestStatus == oldRequestStatus) {
      log.info("determineNewTransactionStatus:: request status did not change, doing nothing");
      return Optional.empty();
    }

    var newTransactionStatus = Optional.ofNullable(
      switch (newRequestStatus) {
        case OPEN_IN_TRANSIT -> OPEN;
        case OPEN_AWAITING_PICKUP -> AWAITING_PICKUP;
        case CLOSED_FILLED -> ITEM_CHECKED_OUT;
        case CLOSED_CANCELLED -> CANCELLED;
        default -> null;
      });

    newTransactionStatus.ifPresentOrElse(
      ts -> log.info("determineNewTransactionStatus:: new transaction status: {}", ts),
      () -> log.info("determineNewTransactionStatus:: irrelevant request status change"));

    return newTransactionStatus;
  }

  private void updateTransactionStatuses(KafkaEvent<Request> event, EcsTlrEntity ecsTlr) {
    determineNewTransactionStatus(event)
      .ifPresent(newStatus -> dcbService.updateTransactionStatuses(newStatus, ecsTlr));
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

    if (!shouldUpdateSecondaryRequest) {
      log.info("propagateChangesFromPrimaryToSecondaryRequest:: no relevant changes detected");
      return;
    }

    log.info("propagateChangesFromPrimaryToSecondaryRequest:: updating secondary request");
    requestService.updateRequestInStorage(secondaryRequest, secondaryRequestTenantId);
    log.info("propagateChangesFromPrimaryToSecondaryRequest:: secondary request updated");
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
}
