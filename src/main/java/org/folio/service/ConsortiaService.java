package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;

public interface ConsortiaService {
  TenantCollection getAllDataTenants(String consortiumId);

//  boolean isCurrentTenantCentral();

//  <T> T executeInTenant(String tenantId, Callable<T> action);
  TenantCollection getAllConsortiumTenants(String consortiumId);
  Collection<Tenant> getAllConsortiumTenants();
}
