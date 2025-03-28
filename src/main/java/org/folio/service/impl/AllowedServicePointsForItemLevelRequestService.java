package org.folio.service.impl;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.ConsortiumSearchClient;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.SearchItemResponse;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class AllowedServicePointsForItemLevelRequestService extends AllowedServicePointsServiceImpl {

  public AllowedServicePointsForItemLevelRequestService(ConsortiumSearchClient searchClient,
    CirculationClient circulationClient, UserService userService,
    SystemUserScopedExecutionService executionService, RequestService requestService,
    EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, executionService,
      requestService, ecsTlrRepository);
  }

  @Override
  protected Collection<String> getLendingTenants(AllowedServicePointsRequest request) {
    SearchItemResponse item = consortiumSearchClient.searchItem(request.getItemId());
    if (StringUtils.isNotEmpty(item.getTenantId())) {
      request.setInstanceId(item.getInstanceId());
      return List.of(item.getTenantId());
    }
    return List.of();
  }

  @Override
  protected AllowedServicePointsResponse getAllowedServicePointsFromTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId) {

    log.info("getAllowedServicePointsFromTenant:: parameters: request: {}, " +
      "patronGroupId: {}, tenantId: {}", request, patronGroupId, tenantId);

    return executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedServicePointsByItem(patronGroupId,
        request.getOperation().getValue(), request.getItemId()));
  }

}
