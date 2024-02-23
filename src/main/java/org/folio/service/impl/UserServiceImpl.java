package org.folio.service.impl;

import java.util.Optional;

import org.folio.client.feign.UsersClient;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.folio.exception.ResourceNotFoundException;
import org.folio.exception.ResourceType;
import org.folio.exception.TenantScopedExecutionException;
import org.folio.service.TenantScopedExecutionService;
import org.folio.service.UserService;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserServiceImpl implements UserService {

  private final UsersClient usersClient;
  private final TenantScopedExecutionService tenantScopedExecutionService;

  public void createShadowUser(EcsTlr ecsTlr, String borrowingTenantId, String lendingTenantId) {
    log.info("createShadowUser:: attempting to create shadow user for ECS TLR {}: " +
        "borrowingTenantId={}, lendingTenantId={}", ecsTlr.getId(), borrowingTenantId, lendingTenantId);
    final String userId = ecsTlr.getRequesterId();

    log.info("createShadowUser:: looking up user {} in borrowing tenant ({})", userId, borrowingTenantId);
    User realUser = findUser(userId, borrowingTenantId)
      .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, userId)); // TODO: losing cause here

    log.info("createShadowUser:: looking up user {} in lending tenant ({})", userId, lendingTenantId);
    findUser(userId, lendingTenantId).ifPresentOrElse(
      u -> log.info("createShadowUser:: user {} already exists in lending tenant ({})", userId, lendingTenantId),
      () -> createUser(buildShadowUser(realUser), lendingTenantId));
  }

  private Optional<User> findUser(String userId, String tenantId) {
    log.info("createShadowUser:: looking up user {} in tenant {}", userId, tenantId);
    try {
      User user = tenantScopedExecutionService.execute(tenantId, () -> usersClient.getUser(userId));
      log.info("fetchUser:: user {} found in tenant {}", userId, tenantId);
      log.debug("fetchUser:: user: {}", () -> user);
      return Optional.of(user);
    } catch (TenantScopedExecutionException e) {
      log.warn("fetchUser:: user {} not found in tenant {}", userId, tenantId, e);
      return Optional.ofNullable(e.getCause())
        .filter(cause -> cause instanceof FeignException.NotFound)
        .map(cause -> Optional.<User>empty())
        .orElseThrow(() -> e);
    }
  }

  private User createUser(User user, String tenantId) {
    log.info("createShadowUser:: creating user {} in tenant {}", user.getId(), tenantId);
    User newUser = tenantScopedExecutionService.execute(tenantId, () -> usersClient.postUser(user));
    log.info("createUser:: user {} was created in tenant {}", user.getId(), tenantId);
    log.debug("createUser:: user: {}", () -> newUser);

    return newUser;
  }

  private static User buildShadowUser(User realUser) {
    User shadowUser = new User()
      .id(realUser.getId())
      .username(realUser.getUsername())
      .patronGroup(realUser.getPatronGroup())
      .type(UserType.SHADOW.getValue())
      .active(true);

    UserPersonal personal = realUser.getPersonal();
    if (personal != null) {
      shadowUser.setPersonal(new UserPersonal()
        .firstName(personal.getFirstName())
        .lastName(personal.getLastName())
      );
    }

    log.debug("buildShadowUser:: result: {}", () -> shadowUser);
    return shadowUser;
  }

}
