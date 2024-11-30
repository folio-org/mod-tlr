package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.UserGroupClient;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserGroups;
import org.folio.service.UserGroupService;
import org.folio.support.BulkFetcher;
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

  @Override
  public Collection<UserGroup> find(Collection<String> ids) {
    log.info("find:: fetching userGroups by {} IDs", ids::size);
    log.debug("find:: ids={}", ids);
    return BulkFetcher.fetch(userGroupClient, ids, UserGroups::getUsergroups);
  }
}
