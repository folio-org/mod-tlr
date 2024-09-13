package org.folio.service.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

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
@Service("titleLevelSpService")
public class TitleLevelServicePointServiceImpl extends AllowedServicePointsServiceImpl {

  public TitleLevelServicePointServiceImpl(SearchClient searchClient,
    CirculationClient circulationClient, UserService userService,
    SystemUserScopedExecutionService executionService, RequestService requestService,
    EcsTlrRepository ecsTlrRepository) {

    super(searchClient, circulationClient, userService, executionService, requestService,
      ecsTlrRepository);
  }

  @Override
  protected AllowedServicePointsResponse getForCreate(AllowedServicePointsRequest request) {
    String patronGroupId = userService.find(request.getRequesterId()).getPatronGroup();
    log.info("getForCreate:: patronGroupId={}", patronGroupId);
    String instanceId = request.getInstanceId();
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

  @Override
  protected boolean checkAvailability(AllowedServicePointsRequest request, String patronGroupId,
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

}
