package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;

import lombok.extern.log4j.Log4j2;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class AllowedServicePointsForTitleLevelRequestService extends AllowedServicePointsServiceImpl {

  public AllowedServicePointsForTitleLevelRequestService(SearchClient searchClient,
    CirculationClient circulationClient, UserService userService,
    SystemUserScopedExecutionService executionService, RequestService requestService,
    EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, executionService, requestService,
      ecsTlrRepository);
  }

  @Override
  protected boolean checkAvailabilityForLevelRequest(AllowedServicePointsRequest request,
    String patronGroupId) {

    var searchInstancesResponse = searchClient.searchInstance(request.getInstanceId());
    return searchInstancesResponse.getInstances().stream()
      .map(Instance::getItems)
      .flatMap(Collection::stream)
      .map(Item::getTenantId)
      .filter(Objects::nonNull)
      .distinct()
      .anyMatch(tenantId -> checkAvailability(request, patronGroupId, tenantId));
  }

  @Override
  protected AllowedServicePointsResponse getAllowedServicePointsResponseFromTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId) {
    return executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedRoutingServicePoints(patronGroupId, request.getInstanceId(),
        request.getOperation().getValue(), true));
  }

}
