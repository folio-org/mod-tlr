package org.folio.service;

import org.folio.domain.dto.User;

public interface UserService {
  User find(String userId);
  User create(User user);
}
