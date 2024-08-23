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
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Request;
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
public class AllowedServicePointsServiceImpl implements AllowedServicePointsService {

  private final SearchClient searchClient;
  private final CirculationClient circulationClient;
  private final UserService userService;
  private final SystemUserScopedExecutionService executionService;
  private final RequestService requestService;
  private final EcsTlrRepository ecsTlrRepository;

  public AllowedServicePointsResponse getAllowedServicePoints(AllowedServicePointsRequest request) {
    log.info("getAllowedServicePoints:: {}", request);
    return switch (request.getOperation()) {
      case CREATE -> getForCreate(request);
      case REPLACE -> getForReplace(request);
    };
  }

  public AllowedServicePointsResponse getForCreate(AllowedServicePointsRequest request) {
    String instanceId = request.getInstanceId();
    String patronGroupId = userService.find(request.getRequesterId()).getPatronGroup();
    log.info("getForCreate:: patronGroupId={}", patronGroupId);

    var searchInstancesResponse = searchClient.searchInstance(instanceId);
    // TODO: make call in parallel
    boolean availableForRequesting = searchInstancesResponse.getInstances().stream()
      .map(Instance::getItems)
      .flatMap(Collection::stream)
      .map(Item::getTenantId)
      .filter(Objects::nonNull)
      .distinct()
      .anyMatch(tenantId -> checkAvailability(request, patronGroupId, tenantId));

    if (availableForRequesting) {
      log.info("getForCreate:: Available for requesting, proxying call");
      return circulationClient.allowedServicePointsWithStubItem(patronGroupId, instanceId,
        request.getOperation().getValue(), true);
    } else {
      log.info("getForCreate:: Not available for requesting, returning empty result");
      return new AllowedServicePointsResponse();
    }
  }

  private boolean checkAvailability(AllowedServicePointsRequest request, String patronGroupId,
    String tenantId) {

    var allowedServicePointsResponse = executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedRoutingServicePoints(patronGroupId, request.getInstanceId(),
        request.getOperation().getValue(), true));

    var availabilityCheckResult = Stream.of(allowedServicePointsResponse.getHold(),
      allowedServicePointsResponse.getPage(), allowedServicePointsResponse.getRecall())
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .anyMatch(Objects::nonNull);

    log.info("checkAvailability:: result: {}", availabilityCheckResult);

    return availabilityCheckResult;
  }

  private AllowedServicePointsResponse getForReplace(AllowedServicePointsRequest request) {
    EcsTlrEntity ecsTlr = findEcsTlr(request);
    final boolean requestIsNotLinkedToItem = ecsTlr.getItemId() == null;
    log.info("getForReplace:: request is linked to an item: {}", !requestIsNotLinkedToItem);

    if (requestIsNotLinkedToItem && isRequestingNotAllowedInLendingTenant(ecsTlr)) {
      log.info("getForReplace:: no service points are allowed in lending tenant");
      return new AllowedServicePointsResponse();
    }

    return getAllowedServicePointsFromBorrowingTenant(request);
  }

  private EcsTlrEntity findEcsTlr(AllowedServicePointsRequest request) {
    final String primaryRequestId = request.getRequestId();
    log.info("findEcsTlr:: looking for ECS TLR with primary request ID {}", primaryRequestId);
    EcsTlrEntity ecsTlr = ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(primaryRequestId))
      .orElseThrow(() -> new EntityNotFoundException(String.format(
        "ECS TLR for primary request with ID %s was not found", primaryRequestId)));

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
      case PAGE -> allowedServicePoints.hold(null).recall(null);
      case HOLD -> allowedServicePoints.page(null).recall(null);
      case RECALL -> allowedServicePoints.page(null).hold(null);
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

    return allowedServicePointsForRequestType == null || allowedServicePointsForRequestType.isEmpty();
  }

}
