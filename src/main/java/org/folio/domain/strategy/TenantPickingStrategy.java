package org.folio.domain.strategy;

import java.util.Set;

public interface TenantPickingStrategy {
  Set<String> pickTenants(String instanceId);
}
