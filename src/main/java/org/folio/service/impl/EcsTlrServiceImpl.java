package org.folio.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.exception.TenantPickingException;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DcbService;
import org.folio.service.EcsTlrService;
import org.folio.service.RequestService;
import org.folio.service.TenantService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcsTlrServiceImpl implements EcsTlrService {

  private final EcsTlrRepository ecsTlrRepository;
  private final EcsTlrMapper requestsMapper;
  private final TenantService tenantService;
  private final RequestService requestService;
  private final DcbService dcbService;

  @Override
  public Optional<EcsTlr> get(UUID id) {
    log.debug("get:: parameters id: {}", id);

    return ecsTlrRepository.findById(id)
      .map(requestsMapper::mapEntityToDto);
  }

  @Override
  public EcsTlr create(EcsTlr ecsTlr) {
    log.info("create:: creating ECS TLR {} for instance {} and requester {}", ecsTlr.getId(),
      ecsTlr.getInstanceId(), ecsTlr.getRequesterId());

    String borrowingTenantId = getBorrowingTenant(ecsTlr);
    Collection<String> lendingTenantIds = getLendingTenants(ecsTlr);
    RequestWrapper secondaryRequest = requestService.createSecondaryRequest(
      buildSecondaryRequest(ecsTlr), borrowingTenantId, lendingTenantIds);
    RequestWrapper primaryRequest = requestService.createPrimaryRequest(
      buildPrimaryRequest(secondaryRequest.request()), borrowingTenantId);
    updateEcsTlr(ecsTlr, primaryRequest, secondaryRequest);

    return save(ecsTlr);
  }

  @Override
  public boolean update(UUID requestId, EcsTlr ecsTlr) {
    log.debug("update:: parameters requestId: {}, ecsTlr: {}", () -> requestId, () -> ecsTlr);

    if (ecsTlrRepository.existsById(requestId)) {
      ecsTlrRepository.save(requestsMapper.mapDtoToEntity(ecsTlr));
      return true;
    }
    return false;
  }

  @Override
  public boolean delete(UUID requestId) {
    log.debug("delete:: parameters requestId: {}", () -> requestId);

    if (ecsTlrRepository.existsById(requestId)) {
      ecsTlrRepository.deleteById(requestId);
      return true;
    }
    return false;
  }

  private String getBorrowingTenant(EcsTlr ecsTlr) {
    log.info("getBorrowingTenant:: getting borrowing tenant");
    final String borrowingTenantId = tenantService.getBorrowingTenant(ecsTlr)
      .orElseThrow(() -> new TenantPickingException("Failed to get borrowing tenant"));
    log.info("getBorrowingTenant:: borrowing tenant: {}", borrowingTenantId);

    return borrowingTenantId;
  }

  private Collection<String> getLendingTenants(EcsTlr ecsTlr) {
    final String instanceId = ecsTlr.getInstanceId();
    log.info("getLendingTenants:: looking for lending tenants for instance {}", instanceId);
    List<String> tenantIds = tenantService.getLendingTenants(ecsTlr);
    if (tenantIds.isEmpty()) {
      log.error("getLendingTenants:: failed to find lending tenants for instance: {}", instanceId);
      throw new TenantPickingException("Failed to find lending tenants for instance " + instanceId);
    }

    log.info("getLendingTenants:: lending tenants found: {}", tenantIds);
    return tenantIds;
  }

  private EcsTlr save(EcsTlr ecsTlr) {
    log.info("save:: saving ECS TLR {}", ecsTlr.getId());
    EcsTlrEntity updatedEcsTlr = ecsTlrRepository.save(requestsMapper.mapDtoToEntity(ecsTlr));
    log.info("save:: saved ECS TLR {}", ecsTlr.getId());
    log.debug("save:: ECS TLR: {}", () -> ecsTlr);

    return requestsMapper.mapEntityToDto(updatedEcsTlr);
  }

  private static Request buildPrimaryRequest(Request secondaryRequest) {
    return new Request()
      .id(secondaryRequest.getId())
      .instanceId(secondaryRequest.getInstanceId())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF)
      .pickupServicePointId(secondaryRequest.getPickupServicePointId());
  }

  private Request buildSecondaryRequest(EcsTlr ecsTlr) {
    return requestsMapper.mapDtoToRequest(ecsTlr)
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.SECONDARY);
  }

  private static void updateEcsTlr(EcsTlr ecsTlr, RequestWrapper primaryRequest,
    RequestWrapper secondaryRequest) {

    log.info("updateEcsTlr:: updating ECS TLR in memory");
    ecsTlr.primaryRequestTenantId(primaryRequest.tenantId())
      .primaryRequestId(primaryRequest.request().getId())
      .secondaryRequestTenantId(secondaryRequest.tenantId())
      .secondaryRequestId(secondaryRequest.request().getId())
      .itemId(secondaryRequest.request().getItemId());

    log.info("updateEcsTlr:: ECS TLR updated in memory");
    log.debug("updateEcsTlr:: ECS TLR: {}", () -> ecsTlr);
  }

  @Override
  public void handleSecondaryRequestUpdate(UUID secondaryRequestId, UUID itemId) {
    log.debug("handleSecondaryRequestUpdate:: parameters secondaryRequestId: {}, itemId: {}",
      secondaryRequestId, itemId);
    log.info("handleSecondaryRequestUpdate:: looking for ECS TLR for secondary request {}",
      secondaryRequestId);
    ecsTlrRepository.findBySecondaryRequestId(secondaryRequestId).ifPresentOrElse(
      ecsTlr -> handleSecondaryRequestUpdate(ecsTlr, itemId),
      () -> log.info("handleSecondaryRequestUpdate: ECS TLR with secondary request {} not found",
        secondaryRequestId));
  }

  private void handleSecondaryRequestUpdate(EcsTlrEntity ecsTlr, UUID itemId) {
    log.debug("handleSecondaryRequestUpdate:: parameters ecsTlr: {}, itemId: {}",
      () -> ecsTlr, () -> itemId);
    final UUID ecsTlrId = ecsTlr.getId();
    final UUID ecsTlrItemId = ecsTlr.getItemId();
    if (ecsTlrItemId != null) {
      log.info("handleSecondaryRequestUpdate:: ECS TLR {} already has itemId: {}", ecsTlrId, ecsTlrItemId);
      return;
    }
    dcbService.createTransactions(ecsTlr);
    log.info("handleSecondaryRequestUpdate:: updating ECS TLR {}, new itemId is {}", ecsTlrId, itemId);
    ecsTlr.setItemId(itemId);
    ecsTlrRepository.save(ecsTlr);
    log.info("handleSecondaryRequestUpdate: ECS TLR {} is updated", ecsTlrId);
  }
}
