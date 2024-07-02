package org.folio.service.impl;

import org.folio.client.feign.UserGroupClient;
import org.folio.domain.dto.UserGroup;
import org.folio.service.UserGroupService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserGroupServiceImpl implements UserGroupService {

  private final UserGroupClient userGroupClient;

  @Override
  public UserGroup create(UserGroup userGroup) {
    log.info("create:: creating userGroup {}", userGroup.getId());
    return userGroupClient.postUserGroup(userGroup);
  }

  @Override
  public UserGroup update(UserGroup userGroup) {
    log.info("update:: updating userGroup {}", userGroup.getId());
    return userGroupClient.putUserGroup(userGroup.getId(), userGroup);
  }
}
