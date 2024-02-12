package org.folio.domain.strategy;

import java.util.Optional;

public interface TenantPickingStrategy {
  Optional<String> pickTenant(String instanceId);
}
