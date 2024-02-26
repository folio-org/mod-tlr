package org.folio.service.impl;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.dto.Request;
import org.folio.service.RequestService;
import org.folio.service.TenantScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestServiceImpl implements RequestService {
  private final TenantScopedExecutionService tenantScopedExecutionService;
  private final CirculationClient circulationClient;

  @Override
  public Request createPrimaryRequest(Request request, String tenantId) {
    final String requestId = request.getId();
    log.info("createPrimaryRequest:: creating primary request {} in tenant {}", requestId, tenantId);
    Request primaryRequest = tenantScopedExecutionService.execute(tenantId,
      () -> circulationClient.createRequest(request));
    log.info("createPrimaryRequest:: primary request {} created in tenant {}", requestId, tenantId);
    log.debug("createPrimaryRequest:: request: {}", () -> primaryRequest);

    return primaryRequest;
  }

  @Override
  public Request createSecondaryRequest(Request request, String tenantId) {
    final String requestId = request.getId();
    log.info("createSecondaryRequest:: creating secondary request {} in tenant {}", requestId, tenantId);
    Request secondaryRequest = tenantScopedExecutionService.execute(tenantId,
      () -> circulationClient.createInstanceRequest(request));
    log.info("createSecondaryRequest:: secondary request {} created in tenant {}", requestId, tenantId);
    log.debug("createSecondaryRequest:: request: {}", () -> secondaryRequest);

    return secondaryRequest;
  }
}
