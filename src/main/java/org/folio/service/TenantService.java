package org.folio.service;

import java.util.Optional;

public interface TenantService {
  Optional<String> getBorrowingTenant();

  Optional<String> getLendingTenant(String instanceId);
}
