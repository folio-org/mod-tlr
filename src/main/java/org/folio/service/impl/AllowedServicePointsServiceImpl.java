package org.folio.service.impl;

import static org.folio.domain.dto.RequestOperation.REPLACE;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.ConsortiumSearchClient;
import org.folio.domain.dto.AllowedServicePointsInner;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.AllowedServicePointsService;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public abstract class AllowedServicePointsServiceImpl implements AllowedServicePointsService {

  protected final ConsortiumSearchClient consortiumSearchClient;
  protected final CirculationClient circulationClient;
  private final UserService userService;
  protected final SystemUserScopedExecutionService executionService;
  private final RequestService requestService;
  private final EcsTlrRepository ecsTlrRepository;

  public AllowedServicePointsResponse getAllowedServicePoints(AllowedServicePointsRequest request) {
    log.info("getAllowedServicePoints:: {}", request);
    return switch (request.getOperation()) {
      case CREATE -> getForCreate(request);
      case REPLACE -> getForReplace(request);
    };
  }

  private AllowedServicePointsResponse getForCreate(AllowedServicePointsRequest request) {
    String patronGroupId = request.getPatronGroupId();
    String requesterId = request.getRequesterId();
    if (StringUtils.isBlank(patronGroupId)) {
      patronGroupId = userService.find(requesterId).getPatronGroup();
    }
    log.info("getForCreate:: patronGroupId={}", patronGroupId);

    Map<String, AllowedServicePointsInner> page = new HashMap<>();
    Map<String, AllowedServicePointsInner> hold = new HashMap<>();
    Map<String, AllowedServicePointsInner> recall = new HashMap<>();
    for (String tenantId : getLendingTenants(request)) {
      var servicePoints = getAllowedServicePointsFromTenant(request, patronGroupId, tenantId);
      combineAndFilterDuplicates(page, servicePoints.getPage());
      combineAndFilterDuplicates(hold, servicePoints.getHold());
      combineAndFilterDuplicates(recall, servicePoints.getRecall());
    }

    return new AllowedServicePointsResponse()
      .page(Set.copyOf(page.values()))
      .hold(Set.copyOf(hold.values()))
      .recall(Set.copyOf(recall.values()));
  }

  private void combineAndFilterDuplicates(
    Map<String, AllowedServicePointsInner> servicePoints, Set<AllowedServicePointsInner> toAdd) {

    if (CollectionUtils.isEmpty(toAdd)) {
      return;
    }
    toAdd.stream()
      .filter(Objects::nonNull)
      .forEach(allowedSp -> servicePoints.put(allowedSp.getId(), allowedSp));
  }

  protected abstract Collection<String> getLendingTenants(AllowedServicePointsRequest request);

  protected abstract AllowedServicePointsResponse getAllowedServicePointsFromTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId);

  private AllowedServicePointsResponse getForReplace(AllowedServicePointsRequest request) {
    EcsTlrEntity ecsTlr = findEcsTlr(request);

    var allowedServicePoints = executionService.executeSystemUserScoped(
      ecsTlr.getSecondaryRequestTenantId(), () -> circulationClient.allowedServicePoints(
        REPLACE.getValue(), ecsTlr.getSecondaryRequestId().toString()));

    Request secondaryRequest = requestService.getRequestFromStorage(
      ecsTlr.getSecondaryRequestId().toString(), ecsTlr.getSecondaryRequestTenantId());
    Request.RequestTypeEnum secondaryRequestType = secondaryRequest.getRequestType();
    log.info("getForReplace:: secondary request type: {}", secondaryRequestType.getValue());

    return switch (secondaryRequestType) {
      case PAGE -> new AllowedServicePointsResponse().page(allowedServicePoints.getPage());
      case HOLD -> new AllowedServicePointsResponse().hold(allowedServicePoints.getHold());
      case RECALL -> new AllowedServicePointsResponse().recall(allowedServicePoints.getRecall());
    };
  }

  private EcsTlrEntity findEcsTlr(AllowedServicePointsRequest request) {
    String primaryRequestId = request.getRequestId();
    log.info("findEcsTlr:: looking for ECS TLR with primary request {}", primaryRequestId);
    EcsTlrEntity ecsTlr = ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(primaryRequestId))
      .orElseThrow(() -> new EntityNotFoundException(String.format(
        "ECS TLR for primary request %s was not found", primaryRequestId)));

    log.info("findEcsTlr:: ECS TLR found: {}", ecsTlr.getId());
    return ecsTlr;
  }

}
