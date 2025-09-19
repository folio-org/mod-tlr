package org.folio.service.impl;

import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.SECONDARY;
import static org.folio.domain.dto.Request.RequestTypeEnum.HOLD;
import static org.folio.domain.dto.Request.StatusEnum.CLOSED_CANCELLED;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.AWAITING_PICKUP;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.CANCELLED;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.OPEN;
import static org.folio.support.Constants.INTERIM_SERVICE_POINT_ID;
import static org.folio.support.kafka.EventType.UPDATE;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Request.EcsRequestPhaseEnum;
import org.folio.domain.dto.Request.FulfillmentPreferenceEnum;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.CloningService;
import org.folio.service.DcbService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class RequestEventHandler implements KafkaEventHandler<Request> {
  // the id used by DCB when canceling a request
  private static final String DCB_CANCELLATION_REASON_ID = "50ed35b2-1397-4e83-a76b-642adf91ca2a";

  private final DcbService dcbService;
  private final EcsTlrRepository ecsTlrRepository;
  private final SystemUserScopedExecutionService executionService;
  private final ServicePointService servicePointService;
  private final CloningService<ServicePoint> servicePointCloningService;
  private final RequestService requestService;

  @Override
  public void handle(KafkaEvent<Request> event) {
    log.info("handle:: processing request event: {}", event::getId);
    if (event.getGenericType() == UPDATE) {
      handleRequestUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getGenericType);
    }
    log.info("handle:: request event processed: {}", event::getId);
  }

  private void handleRequestUpdateEvent(KafkaEvent<Request> event) {
    log.info("handleRequestUpdateEvent:: handling request update event: {}", event::getId);
    Request updatedRequest = event.getNewVersion();
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
    Request updatedRequest = event.getNewVersion();

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

    EcsRequestPhaseEnum updatedRequestPhase = updatedRequest.getEcsRequestPhase();
    UUID updatedRequestId = UUID.fromString(updatedRequest.getId());

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
    processPrimaryRequestStatusIdUpdate(ecsTlr, event.getNewVersion());
    propagatePrimaryRequestChanges(ecsTlr, event);
    updateTransactionStatuses(event, ecsTlr);
  }

  private void handleSecondaryRequestUpdate(EcsTlrEntity ecsTlr, KafkaEvent<Request> event) {
    processItemIdUpdate(ecsTlr, event.getNewVersion());
    updateTransactionStatuses(event, ecsTlr);
  }

  private void processPrimaryRequestStatusIdUpdate(EcsTlrEntity ecsTlr, Request updatedRequest) {
    if (updatedRequest.getStatus() == null) {
      log.info("processPrimaryRequestStatusIdUpdate:: updated primary request status is null, " +
        "doing nothing");
      return;
    }

    if (updatedRequest.getStatus().getValue().equals(ecsTlr.getPrimaryRequestStatus())) {
      log.info("processPrimaryRequestStatusIdUpdate:: primary request status hasn't changed, " +
        "doing nothing");
      return;
    }

    log.info("processPrimaryRequestStatusIdUpdate:: updating ECS request {} " +
        "primary request status from {} to {}", ecsTlr::getId, ecsTlr::getPrimaryRequestStatus,
      () -> updatedRequest.getStatus().getValue());

    ecsTlr.setPrimaryRequestStatus(updatedRequest.getStatus().getValue());
    ecsTlrRepository.save(ecsTlr);

    log.info("processPrimaryRequestStatusIdUpdate:: primary request status for ECS request {} " +
      "updated", ecsTlr::getId);
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

  private void updateTransactionStatuses(KafkaEvent<Request> event, EcsTlrEntity ecsTlr) {

    var oldRequestStatus = event.getOldVersion().getStatus();
    var newRequestStatus = event.getNewVersion().getStatus();
    log.info("updateTransactionStatuses:: oldRequestStatus='{}', newRequestStatus='{}'",
      oldRequestStatus, newRequestStatus);

    if (newRequestStatus == oldRequestStatus) {
      log.info("updateTransactionStatuses:: request status did not change, doing nothing");
      return;
    }

    var newTransactionStatus = switch (newRequestStatus) {
      case OPEN_IN_TRANSIT -> OPEN;
      case OPEN_AWAITING_PICKUP, OPEN_AWAITING_DELIVERY -> AWAITING_PICKUP;
      case CLOSED_FILLED -> ITEM_CHECKED_OUT;
      case CLOSED_CANCELLED -> CANCELLED;
      default -> null;
    };
    if (newTransactionStatus == null) {
      log.info("updateTransactionStatuses:: irrelevant request status change");
      return;
    }

    log.info("updateTransactionStatuses:: new transaction status: {}", newTransactionStatus);
    dcbService.updateTransactionStatuses(newTransactionStatus, ecsTlr);
  }

  private void propagatePrimaryRequestChanges(EcsTlrEntity ecsTlr, KafkaEvent<Request> event) {
    log.info("propagatePrimaryRequestChanges:: propagating primary request changes");

   Optional.ofNullable(ecsTlr.getSecondaryRequestId())
     .map(UUID::toString)
     .ifPresent(secondaryRequestId -> propagatePrimaryRequestChanges(ecsTlr, event,
       secondaryRequestId, ecsTlr.getSecondaryRequestTenantId()));

    Optional.ofNullable(ecsTlr.getIntermediateRequestId())
      .map(UUID::toString)
      .ifPresent(intermediateRequestId -> propagatePrimaryRequestChanges(ecsTlr, event,
        intermediateRequestId, ecsTlr.getIntermediateRequestTenantId()));
  }

  private void propagatePrimaryRequestChanges(EcsTlrEntity ecsTlr, KafkaEvent<Request> event,
    String targetRequestId, String targetRequestTenantId) {

    log.info("propagatePrimaryRequestChanges:: propagating changes to request {} in tenant {}",
      targetRequestId, targetRequestTenantId);

    Request primaryRequest = event.getNewVersion();
    Request targetRequest = requestService.getRequestFromStorage(targetRequestId, targetRequestTenantId);

    boolean shouldUpdateTargetRequest = false;
    if (valueIsNotEqual(primaryRequest, targetRequest, Request::getRequestExpirationDate)) {
      Date requestExpirationDate = primaryRequest.getRequestExpirationDate();
      log.info("propagatePrimaryRequestChanges:: request expiration date changed: {}",
        requestExpirationDate);
      targetRequest.setRequestExpirationDate(requestExpirationDate);
      shouldUpdateTargetRequest = true;
    }
    if (!INTERIM_SERVICE_POINT_ID.equals(targetRequest.getPickupServicePointId())) {
      log.info("propagatePrimaryRequestChanges:: request {} service point is not interim",
        targetRequestId);
      if (valueIsNotEqual(primaryRequest, targetRequest, Request::getFulfillmentPreference)) {
        FulfillmentPreferenceEnum fulfillmentPreference = primaryRequest.getFulfillmentPreference();
        log.info("propagatePrimaryRequestChanges:: fulfillment preference changed: {}",
          fulfillmentPreference);
        targetRequest.setFulfillmentPreference(fulfillmentPreference);
        shouldUpdateTargetRequest = true;
      }
      if (valueIsNotEqual(primaryRequest, targetRequest, Request::getPickupServicePointId)) {
        String pickupServicePointId = primaryRequest.getPickupServicePointId();
        log.info("propagatePrimaryRequestChanges:: pickup service point ID changed: {}",
          pickupServicePointId);
        targetRequest.setPickupServicePointId(pickupServicePointId);
        shouldUpdateTargetRequest = true;
        clonePickupServicePoint(ecsTlr.getPrimaryRequestTenantId(), targetRequestTenantId,
          pickupServicePointId);
      }
    }

    if (needToCancelHoldTlr(primaryRequest, targetRequest)) {
      log.info("propagatePrimaryRequestChanges:: canceling the request: {}", targetRequestId);
      targetRequest.setStatus(CLOSED_CANCELLED);
      targetRequest.setCancelledDate(new Date());
      targetRequest.setCancellationAdditionalInformation("Request cancelled by DCB");
      targetRequest.setCancelledByUserId(event.getUserIdHeaderValue());
      targetRequest.setCancellationReasonId(DCB_CANCELLATION_REASON_ID);
      shouldUpdateTargetRequest = true;
    }

    if (!shouldUpdateTargetRequest) {
      log.info("propagatePrimaryRequestChanges:: no relevant changes detected");
      return;
    }

    log.info("propagatePrimaryRequestChanges:: updating request {}", targetRequestId);
    requestService.updateRequestInStorage(targetRequest, targetRequestTenantId);
    log.info("propagatePrimaryRequestChanges:: request {} updated", targetRequestId);
  }

  private void clonePickupServicePoint(String primaryRequestTenantId, String targetRequestTenantId,
    String pickupServicePointId) {

    if (pickupServicePointId == null) {
      log.info("clonePickupServicePoint:: pickupServicePointId is null, doing nothing");
      return;
    }
    log.info("clonePickupServicePoint:: ensuring that service point {} exists in tenant {}",
      pickupServicePointId, targetRequestTenantId);
    ServicePoint pickupServicePoint = executionService.executeSystemUserScoped(
      primaryRequestTenantId, () -> servicePointService.find(pickupServicePointId));
    executionService.executeSystemUserScoped(targetRequestTenantId,
      () -> servicePointCloningService.clone(pickupServicePoint));
  }

  private boolean needToCancelHoldTlr(Request primaryRequest, Request targetRequest) {
    log.info("needToCancelHoldTlr:: primary request level: {}, type: {}, itemId: {}",
      primaryRequest.getRequestLevel(), primaryRequest.getRequestType(), primaryRequest.getItemId());
    return primaryRequest.getRequestLevel() == Request.RequestLevelEnum.TITLE
      && primaryRequest.getRequestType() == HOLD
      && StringUtils.isEmpty(primaryRequest.getItemId())
      && primaryRequest.getStatus() == CLOSED_CANCELLED
      && targetRequest.getStatus() != CLOSED_CANCELLED;
  }

  private static <T, V> boolean valueIsNotEqual(T o1, T o2, Function<T, V> valueExtractor) {
    return !Objects.equals(valueExtractor.apply(o1), valueExtractor.apply(o2));
  }
}
