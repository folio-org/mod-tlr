package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.AllowedServicePointsInner;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.RequestOperation;
import org.folio.service.AllowedServicePointsService;
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
  private final SystemUserScopedExecutionService executionService;

  @Override
  public AllowedServicePointsResponse getAllowedServicePoints(RequestOperation operation,
    String requesterId, String instanceId) {

    log.debug("getAllowedServicePoints:: params: operation={}, requesterId={}, instanceId={}",
      operation, requesterId, instanceId);

    var searchInstancesResponse = searchClient.searchInstance(instanceId);
    var availableForRequesting = searchInstancesResponse.getInstances().stream()
      .map(Instance::getItems)
      .flatMap(Collection::stream)
      .map(Item::getTenantId)
      .filter(Objects::nonNull)
      .distinct()
      .anyMatch(tenantId -> checkAvailability(tenantId, operation, requesterId, instanceId));

    if (availableForRequesting) {
      log.info("getAllowedServicePoints:: Available for requesting, proxying call");
      return circulationClient.allowedServicePoints(requesterId, instanceId,
        operation.toString().toLowerCase(), true);
    } else {
      log.info("getAllowedServicePoints:: Not available for requesting, returning empty result");
      return new AllowedServicePointsResponse();
    }
  }

  private boolean checkAvailability(String tenantId, RequestOperation operation,
    String requesterId, String instanceId) {

    log.debug("checkAvailability:: params: tenantId={}, operation={}, requesterId={}, instanceId={}",
      tenantId, operation, requesterId, instanceId);

    var allowedServicePointsResponse = executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedServicePoints(requesterId, instanceId,
        operation.toString().toLowerCase(), true));

    var availabilityCheckResult = Stream.of(allowedServicePointsResponse.getHold(),
        allowedServicePointsResponse.getPage(), allowedServicePointsResponse.getRecall())
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .anyMatch(AllowedServicePointsInner::getEcsRequestRouting);

    log.info("checkAvailability:: result: {}", availabilityCheckResult);

    return availabilityCheckResult;
  }

}
