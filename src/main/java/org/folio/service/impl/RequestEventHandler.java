package org.folio.service.impl;

import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.SECONDARY;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.AWAITING_PICKUP;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.ITEM_CHECKED_OUT;
import static org.folio.domain.dto.TransactionStatus.StatusEnum.OPEN;
import static org.folio.support.KafkaEvent.EventType.UPDATED;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.Request.EcsRequestPhaseEnum;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DcbService;
import org.folio.service.KafkaEventHandler;
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
    if (requestMatchesEcsTlr(ecsTlr, updatedRequest, event.getTenantIdHeaderValue())) {
      processItemIdUpdate(ecsTlr, updatedRequest);
      updateDcbTransaction(ecsTlr, updatedRequest, event);
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

  private void processItemIdUpdate(EcsTlrEntity ecsTlr, Request updatedRequest) {
    if (updatedRequest.getEcsRequestPhase() == PRIMARY) {
      log.info("processItemIdUpdate:: updated request is a primary request, doing nothing");
      return;
    }
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

  private void updateDcbTransaction(EcsTlrEntity ecsTlr, Request updatedRequest,
    KafkaEvent<Request> event) {

    String updatedRequestTenantId = updatedRequest.getEcsRequestPhase() == PRIMARY
      ? ecsTlr.getPrimaryRequestTenantId()
      : ecsTlr.getSecondaryRequestTenantId();

    UUID updatedRequestDcbTransactionId = updatedRequest.getEcsRequestPhase() == PRIMARY
      ? ecsTlr.getPrimaryRequestDcbTransactionId()
      : ecsTlr.getSecondaryRequestDcbTransactionId();

    determineNewTransactionStatus(event)
      .ifPresent(newStatus -> updateTransactionStatus(updatedRequestDcbTransactionId, newStatus,
        updatedRequestTenantId));
  }

  private static Optional<TransactionStatus.StatusEnum> determineNewTransactionStatus(
    KafkaEvent<Request> event) {

    final Request.StatusEnum oldRequestStatus = event.getData().getOldVersion().getStatus();
    final Request.StatusEnum newRequestStatus = event.getData().getNewVersion().getStatus();
    log.info("getDcbTransactionStatus:: oldRequestStatus='{}', newRequestStatus='{}'",
      oldRequestStatus, newRequestStatus);

    if (newRequestStatus == oldRequestStatus) {
      log.info("getDcbTransactionStatus:: request status did not change");
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
      ts -> log.info("getDcbTransactionStatus:: new transaction status: {}", ts),
      () -> log.info("getDcbTransactionStatus:: irrelevant request status change"));

    return newTransactionStatus;
  }

  private void updateTransactionStatus(UUID transactionId,
    TransactionStatus.StatusEnum newTransactionStatus, String tenant) {

    try {
      var currentStatus = dcbService.getTransactionStatus(transactionId, tenant).getStatus();
      if (newTransactionStatus.getValue().equals(currentStatus.getValue())) {
        log.info("updateTransactionStatus:: transaction status did not change, doing nothing");
        return;
      }
      dcbService.updateTransactionStatus(transactionId, newTransactionStatus, tenant);
    } catch (FeignException.NotFound e) {
      log.error("updateTransactionStatus:: transaction {} not found: {}", transactionId, e.getMessage());
    }
  }

}