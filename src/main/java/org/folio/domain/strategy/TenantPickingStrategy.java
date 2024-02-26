package org.folio.domain.strategy;

import java.util.List;

public interface TenantPickingStrategy {
  List<String> pickTenants(String instanceId);
}
