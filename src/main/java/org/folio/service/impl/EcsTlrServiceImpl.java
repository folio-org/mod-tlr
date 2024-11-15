package org.folio.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.exception.RequestCreatingException;
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
  public EcsTlr create(EcsTlr ecsTlrDto) {
    log.info("create:: creating ECS TLR {} instance {}, item {}, requester {}", ecsTlrDto.getId(),
      ecsTlrDto.getInstanceId(), ecsTlrDto.getItemId(), ecsTlrDto.getRequesterId());

    final EcsTlrEntity ecsTlr = requestsMapper.mapDtoToEntity(ecsTlrDto);
    String borrowingTenantId = getBorrowingTenant(ecsTlr);
    Collection<String> lendingTenantIds = getLendingTenants(ecsTlr);
    RequestWrapper secondaryRequest = requestService.createSecondaryRequest(
      buildSecondaryRequest(ecsTlr), borrowingTenantId, lendingTenantIds);

    log.info("create:: Creating circulation item for ECS TLR (ILR) {}", ecsTlrDto.getId());
    CirculationItem circulationItem = requestService.createCirculationItem(ecsTlr,
      secondaryRequest.request(), borrowingTenantId, secondaryRequest.tenantId());

    RequestWrapper primaryRequest = requestService.createPrimaryRequest(
      buildPrimaryRequest(secondaryRequest.request()), borrowingTenantId);

    requestService.updateCirculationItemOnRequestCreation(circulationItem,
      secondaryRequest.request());

    updateEcsTlr(ecsTlr, primaryRequest, secondaryRequest);
    createDcbTransactions(ecsTlr, secondaryRequest.request());

    return requestsMapper.mapEntityToDto(save(ecsTlr));
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

  private String getBorrowingTenant(EcsTlrEntity ecsTlr) {
    log.info("getBorrowingTenant:: getting borrowing tenant");
    final String borrowingTenantId = tenantService.getBorrowingTenant(ecsTlr)
      .orElseThrow(() -> new TenantPickingException("Failed to get borrowing tenant"));
    log.info("getBorrowingTenant:: borrowing tenant: {}", borrowingTenantId);

    return borrowingTenantId;
  }

  private Collection<String> getLendingTenants(EcsTlrEntity ecsTlr) {
    final String instanceId = ecsTlr.getInstanceId().toString();
    log.info("getLendingTenants:: looking for lending tenants for instance {}", instanceId);
    List<String> tenantIds = tenantService.getLendingTenants(ecsTlr);
    if (tenantIds.isEmpty()) {
      log.error("getLendingTenants:: failed to find lending tenants for instance: {}", instanceId);
      throw new TenantPickingException("Failed to find lending tenants for instance " + instanceId);
    }

    log.info("getLendingTenants:: lending tenants found: {}", tenantIds);
    return tenantIds;
  }

  private EcsTlrEntity save(EcsTlrEntity ecsTlr) {
    log.info("save:: saving ECS TLR {}", ecsTlr.getId());
    EcsTlrEntity savedEcsTlr = ecsTlrRepository.save(ecsTlr);
    log.info("save:: saved ECS TLR {}", ecsTlr.getId());
    log.debug("save:: ECS TLR: {}", () -> ecsTlr);

    return savedEcsTlr;
  }

  private static Request buildPrimaryRequest(Request secondaryRequest) {
    return new Request()
      .id(secondaryRequest.getId())
      .instanceId(secondaryRequest.getInstanceId())
      .itemId(secondaryRequest.getItemId())
      .holdingsRecordId(secondaryRequest.getHoldingsRecordId())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestLevel(secondaryRequest.getRequestLevel())
      .requestType(secondaryRequest.getRequestType())
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.PRIMARY)
      .fulfillmentPreference(secondaryRequest.getFulfillmentPreference())
      .pickupServicePointId(secondaryRequest.getPickupServicePointId());
  }

  private Request buildSecondaryRequest(EcsTlrEntity ecsTlr) {
    return requestsMapper.mapEntityToRequest(ecsTlr)
      .ecsRequestPhase(Request.EcsRequestPhaseEnum.SECONDARY);
  }

  private static void updateEcsTlr(EcsTlrEntity ecsTlr, RequestWrapper primaryRequest,
    RequestWrapper secondaryRequest) {

    log.info("updateEcsTlr:: updating ECS TLR in memory");
    ecsTlr.setPrimaryRequestTenantId(primaryRequest.tenantId());
    ecsTlr.setSecondaryRequestTenantId(secondaryRequest.tenantId());
    ecsTlr.setPrimaryRequestId(UUID.fromString(primaryRequest.request().getId()));
    ecsTlr.setSecondaryRequestId(UUID.fromString(secondaryRequest.request().getId()));

    Optional.of(secondaryRequest.request())
      .map(Request::getItemId)
      .map(UUID::fromString)
      .ifPresent(ecsTlr::setItemId);

    log.info("updateEcsTlr:: ECS TLR updated in memory");
    log.debug("updateEcsTlr:: ECS TLR: {}", () -> ecsTlr);
  }

  private void createDcbTransactions(EcsTlrEntity ecsTlr, Request secondaryRequest) {
    if (secondaryRequest.getItemId() == null) {
      log.info("createDcbTransactions:: secondary request has no item ID");
      return;
    }
    dcbService.createBorrowingTransaction(ecsTlr, secondaryRequest);
    dcbService.createLendingTransaction(ecsTlr);
  }

}
