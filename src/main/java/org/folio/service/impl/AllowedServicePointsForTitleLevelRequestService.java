package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.client.CirculationClient;
import org.folio.client.SearchInstanceClient;
import org.folio.client.ConsortiumSearchClient;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.SearchHolding;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.scope.FolioExecutionContextService;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class AllowedServicePointsForTitleLevelRequestService extends AllowedServicePointsServiceImpl {

  private final SearchInstanceClient searchInstanceClient;

  public AllowedServicePointsForTitleLevelRequestService(ConsortiumSearchClient searchClient,
    SearchInstanceClient searchInstanceClient, CirculationClient circulationClient,
    UserService userService, FolioExecutionContextService contextService,
    FolioExecutionContext folioContext,
    RequestService requestService, EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, contextService,
      folioContext, requestService, ecsTlrRepository);
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
      .map(SearchInstance::getHoldings)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .map(SearchHolding::getTenantId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  @Override
  protected AllowedServicePointsResponse getAllowedServicePointsFromTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId) {

    return contextService.execute(tenantId, folioContext,
      () -> circulationClient.allowedServicePointsByInstance(patronGroupId,
        request.getOperation().getValue(), request.getInstanceId()));
  }

}
