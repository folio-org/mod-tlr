package org.folio.service.impl;

import static java.lang.String.format;

import java.util.Collection;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.User;
import org.folio.exception.RequestCreatingException;
import org.folio.service.RequestService;
import org.folio.service.TenantScopedExecutionService;
import org.folio.service.UserService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestServiceImpl implements RequestService {
  private final TenantScopedExecutionService tenantScopedExecutionService;
  private final CirculationClient circulationClient;
  private final UserService userService;

  @Override
  public RequestWrapper createPrimaryRequest(Request request, String borrowingTenantId) {
    final String requestId = request.getId();
    log.info("createPrimaryRequest:: creating primary request {} in borrowing tenant {}",
      requestId, borrowingTenantId);
    Request primaryRequest = tenantScopedExecutionService.execute(borrowingTenantId,
      () -> circulationClient.createRequest(request));
    log.info("createPrimaryRequest:: primary request {} created in borrowing tenant {}",
      requestId, borrowingTenantId);
    log.debug("createPrimaryRequest:: primary request: {}", () -> primaryRequest);

    return new RequestWrapper(primaryRequest, borrowingTenantId);
  }

  @Override
  public RequestWrapper createSecondaryRequest(Request request, String borrowingTenantId,
    Collection<String> lendingTenantIds) {

    log.info("createSecondaryRequest:: attempting to create secondary request in one of potential " +
        "lending tenants: {}", lendingTenantIds);
    final String requesterId = request.getRequesterId();

    log.info("createSecondaryRequest:: looking for requester {} in borrowing tenant ({})",
      requesterId, borrowingTenantId);
    User realRequester = userService.findUser(requesterId, borrowingTenantId);

    for (String lendingTenantId : lendingTenantIds) {
      try {
        log.info("createSecondaryRequest:: attempting to create shadow requester {} in lending tenant {}",
          requesterId, lendingTenantId);
        userService.createShadowUser(realRequester, lendingTenantId);
        return createSecondaryRequest(request, lendingTenantId);
      } catch (Exception e) {
        log.error("createSecondaryRequest:: failed to create secondary request in lending tenant {}: {}",
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

  private RequestWrapper createSecondaryRequest(Request request, String lendingTenantId) {
    final String requestId = request.getId();
    log.info("createSecondaryRequest:: creating secondary request {} in lending tenant {}",
      requestId, lendingTenantId);
    Request secondaryRequest = tenantScopedExecutionService.execute(lendingTenantId,
      () -> circulationClient.createInstanceRequest(request));
    log.info("createSecondaryRequest:: secondary request {} created in lending tenant {}",
      requestId, lendingTenantId);
    log.debug("createSecondaryRequest:: secondary request: {}", () -> secondaryRequest);

    return new RequestWrapper(secondaryRequest, lendingTenantId);
  }

}
