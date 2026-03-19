package org.folio.service.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

import java.util.Collection;

import org.apache.commons.lang3.BooleanUtils;
import org.folio.client.UserClient;
import org.folio.domain.dto.User;
import org.folio.domain.dto.Users;
import org.folio.service.UserService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.scope.FolioExecutionContextService;
import org.folio.support.BulkFetcher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserServiceImpl implements UserService {

  private final UserClient userClient;
  private final FolioExecutionContextService contextService;
  private final FolioExecutionContext folioContext;

  @Override
  public User find(String userId) {
    log.info("find:: looking up user {}", userId);
    return userClient.getUser(userId);
  }

  @Override
  public User create(User user) {
    log.info("create:: creating user {}", user.getId());
    try {
      return userClient.postUser(user);
    } catch (HttpStatusCodeException e) {
      if (isUserAlreadyExistsError(e)) {
        log.info("create:: user {} already exists, repeating find()", user.getId());
        return find(user.getId());
      }
      throw e;
    }
  }

  private boolean isUserAlreadyExistsError(HttpStatusCodeException e) {
    if (e.getStatusCode().value() != HttpStatus.UNPROCESSABLE_CONTENT.value()) {
      log.info("isUserAlreadyExistsError:: status: {}, not a duplicate user error", e.getStatusCode().value());
      return false;
    }
    return e.getResponseBodyAsString(UTF_8).contains("User with this id already exists");
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

    return contextService.execute(tenantId, folioContext,
      () -> ofNullable(userClient.getUser(userId))
        .map(User::getActive)
        .map(BooleanUtils::negate)
        .orElseGet(() -> {
          log.warn("isInactiveInTenant:: user {} not found", tenantId);
          return true;
        }));
  }
}
