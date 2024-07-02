package org.folio.service;

import org.folio.domain.dto.TenantCollection;

public interface ConsortiaService {
  TenantCollection getAllDataTenants(String consortiumId);
}
