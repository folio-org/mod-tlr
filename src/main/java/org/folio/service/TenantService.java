package org.folio.service;

import java.util.List;
import java.util.Optional;

import org.folio.domain.entity.EcsTlrEntity;

public interface TenantService {
  Optional<String> getBorrowingTenant(EcsTlrEntity ecsTlr);

  List<String> getLendingTenants(EcsTlrEntity ecsTlr);
}
