package org.folio.service.impl;

import static org.folio.domain.dto.RequestOperation.REPLACE;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchClient;
import org.folio.domain.Constants;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchItem;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.AllowedServicePointsService;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public abstract class AllowedServicePointsServiceImpl implements AllowedServicePointsService {

  protected final SearchClient searchClient;
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
    String patronGroupId = userService.find(request.getRequesterId()).getPatronGroup();
    log.info("getForCreate:: patronGroupId={}", patronGroupId);

    boolean isAvailableInLendingTenants = getLendingTenants(request)
      .stream()
      .anyMatch(tenant -> isAvailableInLendingTenant(request, patronGroupId, tenant));
    var searchInstancesResponse = searchClient.searchInstance(instanceId);
    // TODO: make call in parallel
    boolean availableForRequesting = searchInstancesResponse.getInstances().stream()
      .map(SearchInstance::getItems)
      .flatMap(Collection::stream)
      .map(SearchItem::getTenantId)
      .filter(Objects::nonNull)
      .distinct()
      .anyMatch(tenantId -> checkAvailability(request, patronGroupId, tenantId));

    if (!isAvailableInLendingTenants) {
      log.info("getForCreate:: Not available for requesting, returning empty result");
      return new AllowedServicePointsResponse();
    }

    log.info("getForCreate:: Available for requesting, proxying call");
    return circulationClient.allowedServicePointsWithStubItem(patronGroupId, request.getInstanceId(),
      request.getOperation().getValue(), true);
  }

  protected abstract Collection<String> getLendingTenants(AllowedServicePointsRequest request);

  private boolean isAvailableInLendingTenant(AllowedServicePointsRequest request, String patronGroupId,
    String tenantId) {

    var allowedServicePointsResponse = getAllowedServicePointsFromLendingTenant(request,
      patronGroupId, tenantId);

    var availabilityCheckResult = Stream.of(allowedServicePointsResponse.getHold(),
      allowedServicePointsResponse.getPage(), allowedServicePointsResponse.getRecall())
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .anyMatch(Objects::nonNull);

    log.info("isAvailableInLendingTenant:: result: {}", availabilityCheckResult);
    return availabilityCheckResult;
  }

  protected abstract AllowedServicePointsResponse getAllowedServicePointsFromLendingTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId);

  private AllowedServicePointsResponse getForReplace(AllowedServicePointsRequest request) {
    EcsTlrEntity ecsTlr = findEcsTlr(request);
    final boolean requestIsLinkedToItem = ecsTlr.getItemId() != null;
    log.info("getForReplace:: request is linked to an item: {}", requestIsLinkedToItem);

    if (!requestIsLinkedToItem && isRequestingNotAllowedInLendingTenant(ecsTlr)) {
      log.info("getForReplace:: no service points are allowed in lending tenant");
      return new AllowedServicePointsResponse();
    }

    return getAllowedServicePointsFromBorrowingTenant(request);
  }

  private EcsTlrEntity findEcsTlr(AllowedServicePointsRequest request) {
    final String primaryRequestId = request.getRequestId();
    log.info("findEcsTlr:: looking for ECS TLR with primary request {}", primaryRequestId);
    EcsTlrEntity ecsTlr = ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(primaryRequestId))
      .orElseThrow(() -> new EntityNotFoundException(String.format(
        "ECS TLR for primary request %s was not found", primaryRequestId)));

    log.info("findEcsTlr:: ECS TLR found: {}", ecsTlr.getId());
    return ecsTlr;
  }

  private AllowedServicePointsResponse getAllowedServicePointsFromBorrowingTenant(
    AllowedServicePointsRequest request) {

    log.info("getForReplace:: fetching allowed service points from borrowing tenant");
    var allowedServicePoints = circulationClient.allowedServicePointsWithStubItem(
      REPLACE.getValue(), request.getRequestId(), true);

    Request.RequestTypeEnum primaryRequestType = Constants.PRIMARY_REQUEST_TYPE;
    log.info("getAllowedServicePointsFromBorrowingTenant:: primary request type: {}",
      primaryRequestType.getValue());

    return switch (primaryRequestType) {
      case PAGE -> new AllowedServicePointsResponse().page(allowedServicePoints.getPage());
      case HOLD -> new AllowedServicePointsResponse().hold(allowedServicePoints.getHold());
      case RECALL -> new AllowedServicePointsResponse().recall(allowedServicePoints.getRecall());
    };
  }

  private boolean isRequestingNotAllowedInLendingTenant(EcsTlrEntity ecsTlr) {
    log.info("isRequestingNotAllowedInLendingTenant:: checking if requesting is allowed in lending tenant");
    var allowedServicePointsInLendingTenant = executionService.executeSystemUserScoped(
      ecsTlr.getSecondaryRequestTenantId(), () -> circulationClient.allowedRoutingServicePoints(
        REPLACE.getValue(), ecsTlr.getSecondaryRequestId().toString(), true));

    Request secondaryRequest = requestService.getRequestFromStorage(
      ecsTlr.getSecondaryRequestId().toString(), ecsTlr.getSecondaryRequestTenantId());
    Request.RequestTypeEnum secondaryRequestType = secondaryRequest.getRequestType();
    log.info("isRequestingNotAllowedInLendingTenant:: secondary request type: {}",
      secondaryRequestType.getValue());

    var allowedServicePointsForRequestType = switch (secondaryRequestType) {
      case PAGE -> allowedServicePointsInLendingTenant.getPage();
      case HOLD -> allowedServicePointsInLendingTenant.getHold();
      case RECALL -> allowedServicePointsInLendingTenant.getRecall();
    };

    log.debug("isRequestingNotAllowedInLendingTenant:: allowed service points for {}: {}",
      secondaryRequestType.getValue(), allowedServicePointsForRequestType);

    return allowedServicePointsForRequestType == null || allowedServicePointsForRequestType.isEmpty();
  }

}
