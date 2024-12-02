package org.folio.service;

import java.util.List;

import org.folio.domain.entity.EcsTlrEntity;

public interface TenantService {
  String getPrimaryRequestTenantId(EcsTlrEntity ecsTlr);

  List<String> getSecondaryRequestTenants(EcsTlrEntity ecsTlr);
}
