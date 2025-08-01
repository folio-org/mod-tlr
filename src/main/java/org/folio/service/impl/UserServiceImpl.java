package org.folio.service.impl;

import static java.util.Optional.ofNullable;

import java.util.Collection;

import org.apache.commons.lang3.BooleanUtils;
import org.folio.client.feign.UserClient;
import org.folio.domain.dto.User;
import org.folio.domain.dto.Users;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.BulkFetcher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserServiceImpl implements UserService {

  private final UserClient userClient;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  @Override
  public User find(String userId) {
    log.info("find:: looking up user {}", userId);
    return userClient.getUser(userId);
  }

  @Override
  public User create(User user) {
    log.info("create:: creating user {}", user.getId());
    return userClient.postUser(user);
  }

  @Override
  public User update(User user) {
    log.info("update:: updating user {}", user.getId());
    return userClient.putUser(user.getId(), user);
  }

  @Override
  public Collection<User> find(Collection<String> userIds) {
    log.info("find:: looking up users by {} IDs", userIds.size());
    log.debug("find:: ids={}", userIds);
    return BulkFetcher.fetch(userClient, userIds, Users::getUsers);
  }

  @Override
  public boolean isInactiveInTenant(String userId, String tenantId) {
    log.info("isInactiveInTenant:: checking if user {} is active", userId);

    return systemUserScopedExecutionService.executeSystemUserScoped(tenantId,
      () -> ofNullable(userClient.getUser(userId))
        .map(User::getActive)
        .map(BooleanUtils::negate)
        .orElseGet(() -> {
          log.warn("isInactiveInTenant:: user {} not found", tenantId);
          return true;
        }));
  }
}
