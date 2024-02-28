package org.folio.service;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.User;

public interface UserService {
  User createShadowUser(User realUser, String tenantId);

  User findUser(String userId, String tenantId);

}
