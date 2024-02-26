package org.folio.service;

import java.util.Optional;

import org.folio.domain.dto.EcsTlr;

public interface TenantService {
  Optional<String> pickBorrowingTenant(EcsTlr ecsTlr);

  Optional<String> pickLendingTenant(EcsTlr ecsTlr);
}
