package org.folio.service;

import java.util.List;

import org.folio.domain.entity.EcsTlrEntity;

public interface TenantService {
  String getBorrowingTenant(EcsTlrEntity ecsTlr);

  List<String> getLendingTenants(EcsTlrEntity ecsTlr);
}
