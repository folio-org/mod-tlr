package org.folio.service.impl;

import static java.lang.String.format;

import java.util.Collection;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.User;
import org.folio.exception.RequestCreatingException;
import org.folio.service.CloningService;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestServiceImpl implements RequestService {

  private final SystemUserScopedExecutionService executionService;
  private final CirculationClient circulationClient;
  private final UserService userService;
  private final ServicePointService servicePointService;
  private final CloningService<User> userCloningService;
  private final CloningService<ServicePoint> servicePointCloningService;

  @Override
  public RequestWrapper createPrimaryRequest(Request request, String borrowingTenantId) {
    final String requestId = request.getId();
    log.info("createPrimaryRequest:: creating primary request {} in borrowing tenant ({})",
      requestId, borrowingTenantId);
    Request primaryRequest = executionService.executeSystemUserScoped(borrowingTenantId,
      () -> circulationClient.createRequest(request));
    log.info("createPrimaryRequest:: primary request {} created in borrowing tenant ({})",
      requestId, borrowingTenantId);
    log.debug("createPrimaryRequest:: primary request: {}", () -> primaryRequest);

    return new RequestWrapper(primaryRequest, borrowingTenantId);
  }

  @Override
  public RequestWrapper createSecondaryRequest(Request request, String borrowingTenantId,
    Collection<String> lendingTenantIds) {

    final String requestId = request.getId();
    final String requesterId = request.getRequesterId();
    final String pickupServicePointId = request.getPickupServicePointId();

    log.info("createSecondaryRequest:: creating secondary request {} in one of potential " +
      "lending tenants: {}", requestId, lendingTenantIds);

    User primaryRequestRequester = executionService.executeSystemUserScoped(borrowingTenantId,
      () -> userService.find(requesterId));
    ServicePoint primaryRequestPickupServicePoint = executionService.executeSystemUserScoped(
      borrowingTenantId, () -> servicePointService.find(pickupServicePointId));

    for (String lendingTenantId : lendingTenantIds) {
      try {
        return executionService.executeSystemUserScoped(lendingTenantId, () -> {
          log.info("createSecondaryRequest:: creating requester {} in lending tenant ({})",
            requesterId, lendingTenantId);
          cloneRequester(primaryRequestRequester);

          log.info("createSecondaryRequest:: creating pickup service point {} in lending tenant ({})",
            pickupServicePointId, lendingTenantId);
          servicePointCloningService.clone(primaryRequestPickupServicePoint);

          log.info("createSecondaryRequest:: creating secondary request {} in lending tenant ({})",
            requestId, lendingTenantId);
          Request secondaryRequest = circulationClient.createInstanceRequest(request);
          log.info("createSecondaryRequest:: secondary request {} created in lending tenant ({})",
            requestId, lendingTenantId);
          log.debug("createSecondaryRequest:: secondary request: {}", () -> secondaryRequest);

          return new RequestWrapper(secondaryRequest, lendingTenantId);
        });
      } catch (Exception e) {
        log.error("createSecondaryRequest:: failed to create secondary request in lending tenant ({}): {}",
          lendingTenantId, e.getMessage());
        log.debug("createSecondaryRequest:: ", e);
      }
    }

    String errorMessage = format(
      "Failed to create secondary request for instance %s in all potential lending tenants: %s",
      request.getInstanceId(), lendingTenantIds);
    log.error("createSecondaryRequest:: {}", errorMessage);
    throw new RequestCreatingException(errorMessage);
  }

  private void cloneRequester(User primaryRequestRequester) {
    User requesterClone = userCloningService.clone(primaryRequestRequester);
    String patronGroup = primaryRequestRequester.getPatronGroup();

    if (patronGroup != null && !patronGroup.equals(requesterClone.getPatronGroup())) {
      log.info("cloneRequester:: updating requester's ({}) patron group in lending tenant to {}",
        requesterClone.getId(), patronGroup);
      requesterClone.setPatronGroup(patronGroup);
      userService.update(requesterClone);
    }
  }

}
