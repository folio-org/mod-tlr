package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

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
public class ItemLevelServicePointServiceImpl extends AllowedServicePointsServiceImpl {

  public ItemLevelServicePointServiceImpl(SearchClient searchClient,
    CirculationClient circulationClient, UserService userService,
    SystemUserScopedExecutionService executionService, RequestService requestService,
    EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, executionService,
      requestService, ecsTlrRepository);
  }

  @Override
  protected AllowedServicePointsResponse getForCreate(AllowedServicePointsRequest request) {
    String instanceId = "";
    String patronGroupId = userService.find(request.getRequesterId()).getPatronGroup();
    log.info("getForCreate:: patronGroupId={}", patronGroupId);
    boolean availableForRequesting = false;
    var searchItemResponse = searchClient.searchItem(request.getItemId());
    if (StringUtils.isNotEmpty(searchItemResponse.getTenantId())) {
      instanceId = searchItemResponse.getInstanceId();
      availableForRequesting = checkAvailability(request, patronGroupId,
        searchItemResponse.getTenantId());
    }

    if (availableForRequesting) {
      log.info("getForCreate:: Available for requesting, proxying call");
      return circulationClient.allowedServicePointsWithStubItem(patronGroupId, instanceId,
        request.getOperation().getValue(), true);
    } else {
      log.info("getForCreate:: Not available for requesting, returning empty result");
      return new AllowedServicePointsResponse();
    }
  }

  @Override
  protected boolean checkAvailability(AllowedServicePointsRequest request, String patronGroupId,
    String tenantId) {

    var allowedServicePointsResponse = executionService.executeSystemUserScoped(tenantId,
      () -> circulationClient.allowedRoutingServicePoints(patronGroupId, request.getItemId(),
        true, request.getOperation().getValue()));

    var availabilityCheckResult = Stream.of(allowedServicePointsResponse.getHold(),
        allowedServicePointsResponse.getPage(), allowedServicePointsResponse.getRecall())
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .anyMatch(Objects::nonNull);

    log.info("checkAvailability:: result: {}", availabilityCheckResult);
    return availabilityCheckResult;
  }

}
