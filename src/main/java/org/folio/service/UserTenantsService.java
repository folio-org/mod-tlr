package org.folio.service;

import org.folio.domain.dto.UserTenant;

public interface UserTenantsService {
  UserTenant findFirstUserTenant();

  String getCentralTenantId();
}
