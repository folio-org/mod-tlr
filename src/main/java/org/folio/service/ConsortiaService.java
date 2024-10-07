package org.folio.service;

import org.folio.domain.dto.TenantCollection;

public interface ConsortiaService {
  TenantCollection getAllDataTenants(String consortiumId);

//  boolean isCurrentTenantCentral();

//  <T> T executeInTenant(String tenantId, Callable<T> action);
}
