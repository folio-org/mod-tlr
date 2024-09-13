package org.folio.service.impl;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.StringUtils;
import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class AllowedServicePointsForItemLevelRequestService extends AllowedServicePointsServiceImpl {

  public AllowedServicePointsForItemLevelRequestService(SearchClient searchClient,
    CirculationClient circulationClient, UserService userService,
    SystemUserScopedExecutionService executionService, RequestService requestService,
    EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, executionService,
      requestService, ecsTlrRepository);
  }

  @Override
  protected boolean checkAvailabilityForLevelRequest(
    AllowedServicePointsRequest request, String patronGroupId) {
    var searchItemResponse = searchClient.searchItem(request.getItemId());
    if (StringUtils.isNotEmpty(searchItemResponse.getTenantId())) {
      request.setInstanceId(searchItemResponse.getInstanceId());
      return checkAvailability(request, patronGroupId,
        searchItemResponse.getTenantId());
    }
    return false;
  }

  @Override
  protected AllowedServicePointsResponse getAllowedServicePointsResponseFromTenant(
    AllowedServicePointsRequest request, String patronGroupId, String tenantId) {
    return executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedRoutingServicePoints(patronGroupId, request.getItemId(),
        true, request.getOperation().getValue()));
  }

}
