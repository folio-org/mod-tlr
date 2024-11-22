package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchInstanceClient;
import org.folio.client.feign.SearchItemClient;
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

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class AllowedServicePointsForTitleLevelRequestService extends AllowedServicePointsServiceImpl {

  private final SearchInstanceClient searchInstanceClient;

  public AllowedServicePointsForTitleLevelRequestService(SearchItemClient searchClient,
    SearchInstanceClient searchInstanceClient, CirculationClient circulationClient,
    UserService userService, SystemUserScopedExecutionService executionService,
    RequestService requestService, EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, executionService, requestService,
      ecsTlrRepository);
    this.searchInstanceClient = searchInstanceClient;
  }

  @Override
  protected Collection<String> getLendingTenants(AllowedServicePointsRequest request) {
    SearchInstancesResponse searchInstancesResponse =
      searchInstanceClient.searchInstance(request.getInstanceId());

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
  protected AllowedServicePointsResponse getAllowedServicePointsFromTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId) {

    return executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedServicePointsByInstance(patronGroupId,
        request.getOperation().getValue(), request.getInstanceId()));
  }

}
