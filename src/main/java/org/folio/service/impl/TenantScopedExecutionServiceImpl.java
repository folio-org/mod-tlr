package org.folio.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.folio.exception.TenantScopedExecutionException;
import org.folio.service.TenantScopedExecutionService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class TenantScopedExecutionServiceImpl implements TenantScopedExecutionService {

  private final FolioModuleMetadata moduleMetadata;
  private final FolioExecutionContext executionContext;

  @Override
  public <T> T execute(String tenantId, Callable<T> action) {
    log.info("execute:: tenantId: {}", tenantId);
    Map<String, Collection<String>> headers = executionContext.getAllHeaders();
    headers.put(XOkapiHeaders.TENANT, List.of(tenantId));

    try (var x = new FolioExecutionContextSetter(moduleMetadata, headers)) {
      return action.call();
    } catch (Exception e) {
      log.error("execute:: execution failed for tenant {}: {}", tenantId, e.getMessage());
      throw new TenantScopedExecutionException(e, tenantId);
    }
  }
}
