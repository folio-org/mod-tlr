package org.folio.service.impl;

import static java.util.Optional.of;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.INTERMEDIATE;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.type.ErrorCode.ECS_REQUEST_CANNOT_BE_PLACED_FOR_INACTIVE_PATRON;
import static org.folio.domain.type.ErrorCode.PATRON_HAS_OPEN_ECS_TLR_FOR_THE_SAME_TITLE;
import static org.folio.exception.ExceptionFactory.validationError;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Request.EcsRequestPhaseEnum;
import org.folio.domain.dto.TlrSettings;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.exception.TenantPickingException;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.ConsortiumService;
import org.folio.service.DcbService;
import org.folio.service.EcsTlrService;
import org.folio.service.RequestService;
import org.folio.service.TenantService;
import org.folio.service.TlrSettingsService;
import org.folio.service.UserService;
import org.folio.service.UserTenantsService;
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
  private final UserTenantsService userTenantsService;
  private final UserService userService;
  private final TlrSettingsService tlrSettingsService;
  private final ConsortiumService consortiumService;

  @Override
  public Optional<EcsTlr> get(UUID id) {
    log.debug("get:: parameters id: {}", id);

    return ecsTlrRepository.findById(id)
      .map(requestsMapper::mapEntityToDto);
  }

  @Override
  public EcsTlr create(EcsTlr ecsTlrDto) {
    log.info("create:: creating ECS TLR for instance {}, item {}, requester {}",
      ecsTlrDto.getInstanceId(), ecsTlrDto.getItemId(), ecsTlrDto.getRequesterId());

    ecsTlrDto.setId(null); // remove client-provided id, it will be generated when entity is persisted
    final EcsTlrEntity ecsTlr = requestsMapper.mapDtoToEntity(ecsTlrDto);
    ecsTlr.setPrimaryRequestStatus(Request.StatusEnum.OPEN_NOT_YET_FILLED.getValue());
    String primaryRequestTenantId = getPrimaryRequestTenant(ecsTlr);

    validateRequester(ecsTlrDto, primaryRequestTenantId);
    validateIfNoOpenEcsTlrForTheSameTitleExist(ecsTlr);

    Collection<String> secondaryRequestsTenantIds = getSecondaryRequestTenants(ecsTlr).stream()
      .filter(tenantId -> !tenantId.equals(primaryRequestTenantId))
      .collect(Collectors.toList());
    log.info("create:: Creating secondary request for ECS TLR (ILR), instance {}, item {}, requester {}",
      ecsTlrDto.getInstanceId(), ecsTlrDto.getItemId(), ecsTlrDto.getRequesterId());
    RequestWrapper secondaryRequestWrapper = requestService.createSecondaryRequest(
      buildSecondaryRequest(ecsTlr), primaryRequestTenantId, secondaryRequestsTenantIds);
    Request secondaryRequest = secondaryRequestWrapper.request();
    String secondaryRequestTenantId = secondaryRequestWrapper.tenantId();

    log.info("create:: Creating primary request for ECS TLR (ILR), instance {}, item {}, requester {}",
      ecsTlrDto.getInstanceId(), ecsTlrDto.getItemId(), ecsTlrDto.getRequesterId());
    RequestWrapper primaryRequestWrapper = requestService.createPrimaryRequest(
      buildPrimaryRequest(secondaryRequest), primaryRequestTenantId, secondaryRequestTenantId);

    updateEcsTlr(ecsTlr, primaryRequestWrapper, secondaryRequestWrapper);

    var centralTenantId = consortiumService.getCentralTenantId();
    if (!primaryRequestTenantId.equals(centralTenantId)) {
      log.info("create:: Primary request tenant is not central, creating intermediate request");
      RequestWrapper intermediateRequest = requestService.createIntermediateRequest(
        buildIntermediateRequest(secondaryRequest), primaryRequestTenantId, centralTenantId,
        secondaryRequestTenantId);
      updateEcsTlrWithIntermediateRequest(ecsTlr, intermediateRequest);
    }

    dcbService.createTransactions(ecsTlr, secondaryRequest);

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

  private void validateRequester(EcsTlr ecsTlrDto, String primaryRequestTenantId) {
    log.info("validateRequester:: validating requester {} in the primary request tenant {}",
      ecsTlrDto::getRequesterId, () -> primaryRequestTenantId);

    if (userService.isInactiveInTenant(ecsTlrDto.getRequesterId(), primaryRequestTenantId)) {
      String message = "ECS request cannot be placed for inactive requester %s"
        .formatted(ecsTlrDto.getRequesterId());
      log.warn("create:: {}", message);
      throw validationError(message, ECS_REQUEST_CANNOT_BE_PLACED_FOR_INACTIVE_PATRON,
        Map.of(
          "requesterId", ecsTlrDto.getRequesterId(),
          "tenantId", primaryRequestTenantId
        ));
    }
  }

  private void validateIfNoOpenEcsTlrForTheSameTitleExist(EcsTlrEntity ecsTlrEntity) {
    log.info("validateIfNoOpenEcsTlrForTheSameTitleExist:: " +
        "level: {}, instanceId: {}, requesterId: {}", ecsTlrEntity::getRequestLevel,
      ecsTlrEntity::getInstanceId, ecsTlrEntity::getRequesterId);

    if (ecsTlrEntity.getRequestLevel() == null
      || !ecsTlrEntity.getRequestLevel().equals(EcsTlr.RequestLevelEnum.TITLE.getValue())
      || ecsTlrEntity.getInstanceId() == null || ecsTlrEntity.getRequesterId() == null) {

      log.info("validateIfNoOpenEcsTlrForTheSameTitleExist:: Skipping validation");
      return;
    }

    var openRequests = ecsTlrRepository.findOpenRequests(ecsTlrEntity.getRequesterId(),
      ecsTlrEntity.getInstanceId());

    if (openRequests == null) {
      log.info("validateIfNoOpenEcsTlrForTheSameTitleExist:: Failed to find open requests");
      return;
    }

    if (!openRequests.isEmpty()) {
      String message = "Patron %s has an open ECS TLR for the same title %s"
        .formatted(ecsTlrEntity.getRequesterId(), ecsTlrEntity.getInstanceId());
      log.warn("validateIfNoOpenEcsTlrForTheSameTitleExist:: {}", message);
      throw validationError(message, PATRON_HAS_OPEN_ECS_TLR_FOR_THE_SAME_TITLE,
        Map.of("requesterId", ecsTlrEntity.getRequesterId().toString(),
          "instanceId", ecsTlrEntity.getInstanceId().toString()));
    }
  }

  private String getPrimaryRequestTenant(EcsTlrEntity ecsTlr) {
    log.info("getPrimaryRequestTenant:: getting primary request tenant");
    final String primaryRequestTenantId = tenantService.getPrimaryRequestTenantId(ecsTlr);
    log.info("getPrimaryRequestTenant:: primary request tenant: {}", primaryRequestTenantId);

    if (primaryRequestTenantId == null) {
      throw new TenantPickingException("Failed to get primary request tenant");
    }

    return primaryRequestTenantId;
  }

  private Collection<String> getSecondaryRequestTenants(EcsTlrEntity ecsTlr) {
    final String instanceId = ecsTlr.getInstanceId().toString();
    log.info("getSecondaryRequestTenants:: looking for secondary request tenants for instance {}", instanceId);
    List<String> tenantIds = tenantService.getSecondaryRequestTenants(ecsTlr);
    if (tenantIds.isEmpty()) {
      log.error("getSecondaryRequestTenants:: failed to find lending tenants for instance: {}", instanceId);
      throw new TenantPickingException("Failed to find secondary request tenants for instance " + instanceId);
    }
    log.info("getSecondaryRequestTenants:: secondary request tenants found: {}", tenantIds);

    List<String> excludedTenants = getExcludedTenants();
    log.info("getSecondaryRequestTenants:: excluded tenants: {}", excludedTenants);

    List<String> eligibleTenants = filterExcludedTenants(tenantIds, excludedTenants);
    if (eligibleTenants.isEmpty()) {
      log.warn("getSecondaryRequestTenants:: No eligible tenants found");
      throw new TenantPickingException("No eligible tenants found");
    }
    log.info("getSecondaryRequestTenants:: eligible tenants: {}", eligibleTenants);
    return eligibleTenants;
  }

  private List<String> getExcludedTenants() {
    return tlrSettingsService.getTlrSettings()
      .map(TlrSettings::getExcludeFromEcsRequestLendingTenantSearch)
      .map(list -> list.stream()
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .toList())
      .orElseGet(Collections::emptyList);
  }

  private List<String> filterExcludedTenants(List<String> tenantIds, List<String> excludedTenants) {
    return tenantIds.stream()
      .filter(Objects::nonNull)
      .filter(tenantId -> excludedTenants.stream()
        .noneMatch(excluded -> excluded.equalsIgnoreCase(tenantId)))
      .toList();
  }

  private EcsTlrEntity save(EcsTlrEntity ecsTlr) {
    log.info("save:: saving ECS TLR {}", ecsTlr.getId());
    EcsTlrEntity savedEcsTlr = ecsTlrRepository.save(ecsTlr);
    log.info("save:: saved ECS TLR {}", ecsTlr.getId());
    log.debug("save:: ECS TLR: {}", () -> ecsTlr);

    return savedEcsTlr;
  }

  private static Request buildPrimaryRequest(Request secondaryRequest) {
    return buildRequest(secondaryRequest, PRIMARY);
  }

  private static Request buildIntermediateRequest(Request secondaryRequest) {
    return buildRequest(secondaryRequest, INTERMEDIATE);
  }

  private static Request buildRequest(Request secondaryRequest, EcsRequestPhaseEnum ecsRequestPhase) {
    return new Request()
      .id(secondaryRequest.getId())
      .instanceId(secondaryRequest.getInstanceId())
      .itemId(secondaryRequest.getItemId())
      .holdingsRecordId(secondaryRequest.getHoldingsRecordId())
      .requesterId(secondaryRequest.getRequesterId())
      .requestDate(secondaryRequest.getRequestDate())
      .requestLevel(secondaryRequest.getRequestLevel())
      .requestType(secondaryRequest.getRequestType())
      .ecsRequestPhase(ecsRequestPhase)
      .fulfillmentPreference(secondaryRequest.getFulfillmentPreference())
      .pickupServicePointId(secondaryRequest.getPickupServicePointId())
      .patronComments(secondaryRequest.getPatronComments());
  }

  private Request buildSecondaryRequest(EcsTlrEntity ecsTlr) {
    return requestsMapper.mapEntityToRequest(ecsTlr)
      .ecsRequestPhase(EcsRequestPhaseEnum.SECONDARY);
  }

  private static void updateEcsTlr(EcsTlrEntity ecsTlr, RequestWrapper primaryRequest,
    RequestWrapper secondaryRequest) {

    log.info("updateEcsTlr:: updating ECS TLR in memory");
    ecsTlr.setPrimaryRequestTenantId(primaryRequest.tenantId());
    ecsTlr.setSecondaryRequestTenantId(secondaryRequest.tenantId());
    ecsTlr.setPrimaryRequestId(UUID.fromString(primaryRequest.request().getId()));
    ecsTlr.setSecondaryRequestId(UUID.fromString(secondaryRequest.request().getId()));

    of(secondaryRequest.request())
      .map(Request::getItemId)
      .map(UUID::fromString)
      .ifPresent(ecsTlr::setItemId);

    log.info("updateEcsTlr:: ECS TLR updated in memory");
    log.debug("updateEcsTlr:: ECS TLR: {}", () -> ecsTlr);
  }

  private static void updateEcsTlrWithIntermediateRequest(EcsTlrEntity ecsTlr,
    RequestWrapper intermediateRequest) {

    log.info("updateEcsTlrWithIntermediateRequest:: updating ECS TLR in memory");
    ecsTlr.setIntermediateRequestTenantId(intermediateRequest.tenantId());
    ecsTlr.setIntermediateRequestId(UUID.fromString(intermediateRequest.request().getId()));

    log.info("updateEcsTlrWithIntermediateRequest:: ECS TLR updated in memory");
    log.debug("updateEcsTlrWithIntermediateRequest:: ECS TLR: {}", () -> ecsTlr);
  }

}
