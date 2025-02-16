package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.SharingInstance;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;

public interface ConsortiaService {
  TenantCollection getAllConsortiumTenants(String consortiumId);
  Collection<Tenant> getAllConsortiumTenants();
  String getCentralTenantId();
  SharingInstance shareInstance(String instanceId, String targetTenantId);
}
