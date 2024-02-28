package org.folio.service;

import java.util.List;
import java.util.Optional;

import org.folio.domain.dto.EcsTlr;

public interface TenantService {
  Optional<String> getBorrowingTenant(EcsTlr ecsTlr);

  List<String> getLendingTenants(EcsTlr ecsTlr);
}
