package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.RequestOperation;
import org.folio.service.AllowedServicePointsService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

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

  @Override
  public AllowedServicePointsResponse getAllowedServicePoints(RequestOperation operation,
    String requesterId, String instanceId) {

    log.debug("getAllowedServicePoints:: params: operation={}, requesterId={}, instanceId={}",
      operation, requesterId, instanceId);

    String patronGroupId = userService.find(requesterId).getPatronGroup();

    var searchInstancesResponse = searchClient.searchInstance(instanceId);
    // TODO: make call in parallel
    boolean availableForRequesting = searchInstancesResponse.getInstances().stream()
      .map(Instance::getItems)
      .flatMap(Collection::stream)
      .map(Item::getTenantId)
      .filter(Objects::nonNull)
      .distinct()
      .anyMatch(tenantId -> checkAvailability(tenantId, operation, patronGroupId, instanceId));

    if (availableForRequesting) {
      log.info("getAllowedServicePoints:: Available for requesting, proxying call");
      return circulationClient.allowedServicePointsWithStubItem(patronGroupId, instanceId,
        operation.toString().toLowerCase(), true);
    } else {
      log.info("getAllowedServicePoints:: Not available for requesting, returning empty result");
      return new AllowedServicePointsResponse();
    }
  }

  private boolean checkAvailability(String tenantId, RequestOperation operation,
    String patronGroupId, String instanceId) {

    log.debug("checkAvailability:: params: tenantId={}, operation={}, patronGroupId={}, instanceId={}",
      tenantId, operation, patronGroupId, instanceId);

    var allowedServicePointsResponse = executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedRoutingServicePoints(patronGroupId, instanceId,
        operation.toString().toLowerCase(), true));

    var availabilityCheckResult = Stream.of(allowedServicePointsResponse.getHold(),
      allowedServicePointsResponse.getPage(), allowedServicePointsResponse.getRecall())
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .anyMatch(Objects::nonNull);

    log.info("checkAvailability:: result: {}", availabilityCheckResult);

    return availabilityCheckResult;
  }

}
