package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItem;
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
  protected Collection<String> getLendingTenants(AllowedServicePointsRequest request) {
    SearchInstancesResponse searchInstancesResponse = searchClient.searchInstance(request.getInstanceId());

    return searchInstancesResponse
      .getInstances()
      .stream()
      .filter(Objects::nonNull)
      .map(SearchInstance::getItems)
      .flatMap(Collection::stream)
      .map(SearchItem::getTenantId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  @Override
  protected AllowedServicePointsResponse getAllowedServicePointsFromLendingTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId) {

    return executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedRoutingServicePoints(patronGroupId, request.getInstanceId(),
        request.getOperation().getValue(), true));
  }

}
