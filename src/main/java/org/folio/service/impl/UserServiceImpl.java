package org.folio.service.impl;

import java.util.Optional;
import java.util.function.Supplier;

import org.folio.client.feign.UsersClient;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
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

  public User createShadowUser(EcsTlr ecsTlr, String borrowingTenantId, String lendingTenantId) {
    final String requesterId = ecsTlr.getRequesterId();
    log.info("createShadowUser:: attempting to create shadow user for ECS TLR {}: " +
        "requesterId={}, borrowingTenantId={}, lendingTenantId={}", ecsTlr.getId(), requesterId,
      borrowingTenantId, lendingTenantId);

    log.info("createShadowUser:: looking up user {} in borrowing tenant ({})", requesterId, borrowingTenantId);
    User realUser = findUser(requesterId, borrowingTenantId);

    log.info("createShadowUser:: looking up user {} in lending tenant ({})", requesterId, lendingTenantId);
    return findOrCreateUser(requesterId, lendingTenantId, () -> buildShadowUser(realUser));
  }

  private User findOrCreateUser(String userId, String tenantId, Supplier<User> userSupplier) {
    try {
      User user = findUser(userId, tenantId);
      log.info("findOrCreateUser:: user {} already exists in tenant {}", userId, tenantId);
      return user;
    } catch (TenantScopedExecutionException e) {
      log.warn("findOrCreateUser:: failed to find user {} in tenant {}", userId, tenantId);
      return Optional.ofNullable(e.getCause())
        .filter(FeignException.NotFound.class::isInstance)
        .map(cause -> createUser(userSupplier.get(), tenantId))
        .orElseThrow(() -> e);
    }
  }

  private User findUser(String userId, String tenantId) {
    log.info("findUser:: looking up user {} in tenant {}", userId, tenantId);
    User user = tenantScopedExecutionService.execute(tenantId, () -> usersClient.getUser(userId));
    log.info("findUser:: user {} found in tenant {}", userId, tenantId);
    log.debug("findUser:: user: {}", () -> user);

    return user;
  }

  private User createUser(User user, String tenantId) {
    log.info("createUser:: creating user {} in tenant {}", user.getId(), tenantId);
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
