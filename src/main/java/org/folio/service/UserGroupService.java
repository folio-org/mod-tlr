package org.folio.service;

import org.folio.domain.dto.UserGroup;

public interface UserGroupService {
  UserGroup create(UserGroup userGroup);
  UserGroup update(UserGroup userGroup);
}
