package org.folio.service;

import org.folio.domain.dto.EcsTlr;

public interface UserService {
  void createShadowUser(EcsTlr ecsTlr, String remoteTenantId);

}
