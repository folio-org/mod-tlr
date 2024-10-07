package org.folio.service;

import java.util.List;
import java.util.Optional;

import org.folio.domain.entity.EcsTlrEntity;

public interface TenantService {
  Optional<String> getPrimaryRequestTenantId(EcsTlrEntity ecsTlr);

  List<String> getSecondaryRequestTenants(EcsTlrEntity ecsTlr);
}
