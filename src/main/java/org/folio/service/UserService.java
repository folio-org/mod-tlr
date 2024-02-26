package org.folio.service;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.User;

public interface UserService {
  User createShadowUser(EcsTlr ecsTlr, String borrowingTenantId, String lendingTenantId);

}
