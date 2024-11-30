package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.UserGroup;

public interface UserGroupService {
  UserGroup create(UserGroup userGroup);
  UserGroup update(UserGroup userGroup);
  Collection<UserGroup> find(Collection<String> ids);
}
