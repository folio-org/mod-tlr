package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.service.TenantService;
import org.folio.exception.TenantPickingException;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.EcsTlrService;
import org.folio.service.RequestService;
import org.folio.service.UserService;
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
  private final UserService userService;
  private final RequestService requestService;

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

    final String borrowingTenantId = pickBorrowingTenant(ecsTlr);
    final String lendingTenantId = pickLendingTenant(ecsTlr);

    userService.createShadowUser(ecsTlr, borrowingTenantId, lendingTenantId);
    Request secondaryRequest = requestService.createSecondaryRequest(
      requestsMapper.mapDtoToRequest(ecsTlr), lendingTenantId);
    Request primaryRequest = requestService.createPrimaryRequest(
      buildPrimaryRequest(secondaryRequest), borrowingTenantId);

    ecsTlr.primaryRequestTenantId(borrowingTenantId)
      .primaryRequestId(primaryRequest.getId())
      .secondaryRequestTenantId(lendingTenantId)
      .secondaryRequestId(secondaryRequest.getId())
      .itemId(secondaryRequest.getItemId());

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

  private String pickBorrowingTenant(EcsTlr ecsTlr) {
    log.info("pickBorrowingTenant:: picking borrowing tenant");
    final String borrowingTenantId = tenantService.pickBorrowingTenant(ecsTlr).orElseThrow(
      () -> new TenantPickingException("Failed to pick borrowing tenant"));
    log.info("pickBorrowingTenant:: borrowing tenant picked: {}", borrowingTenantId);

    return borrowingTenantId;
  }

  private String pickLendingTenant(EcsTlr ecsTlr) {
    log.info("pickLendingTenant:: picking lending tenant");
    final String lendingTenantId = tenantService.pickLendingTenant(ecsTlr).orElseThrow(
      () -> new TenantPickingException("Failed to pick lending tenant"));
    log.info("pickLendingTenant:: lending tenant picked: {}", lendingTenantId);

    return lendingTenantId;
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
      .fulfillmentPreference(Request.FulfillmentPreferenceEnum.HOLD_SHELF);
  }

}
