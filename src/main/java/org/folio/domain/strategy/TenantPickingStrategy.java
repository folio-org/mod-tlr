package org.folio.domain.strategy;

import java.util.List;
import java.util.Optional;

public interface TenantPickingStrategy {
  List<String> pickTenants(String instanceId);
}
