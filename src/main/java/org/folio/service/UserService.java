package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.User;

public interface UserService {
  User find(String userId);
  User create(User user);
  User update(User user);
  Collection<User> find(Collection<String> userIds);
}
