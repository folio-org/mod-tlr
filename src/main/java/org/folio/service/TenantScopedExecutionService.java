package org.folio.service;

import java.util.concurrent.Callable;

public interface TenantScopedExecutionService {

  <T> T execute(String tenantId, Callable<T> action);
}
