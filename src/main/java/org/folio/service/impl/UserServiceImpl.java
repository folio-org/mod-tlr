package org.folio.service.impl;

import org.folio.client.feign.UsersClient;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.folio.exception.ResourceNotFoundException;
import org.folio.exception.ResourceType;
import org.folio.service.TenantScopedExecutionService;
import org.folio.service.UserService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserServiceImpl implements UserService {

  private final UsersClient usersClient;
  private final FolioExecutionContext executionContext;
  private final TenantScopedExecutionService tenantScopedExecutionService;

  public void createShadowUser(EcsTlr ecsTlr, String remoteTenantId) {
    log.info("createShadowUser:: attempting to create shadow user for ECS TLR {} in tenant {}",
      ecsTlr.getId(), remoteTenantId);

    final String userId = ecsTlr.getRequesterId();
    final String localTenantId = executionContext.getTenantId();

    log.info("createShadowUser:: looking up user {} in local tenant ({})", userId, localTenantId);
    User realUser = fetchUser(userId);

    log.info("createShadowUser:: looking up user {} in remote tenant ({})", userId, remoteTenantId);
    tenantScopedExecutionService.execute(remoteTenantId, () -> {
      try {
        User existingShadowUser = usersClient.getUser(userId);
        log.info("createShadowUser:: user {} found in remote tenant ({}), doing nothing",
          userId, remoteTenantId);
        log.debug("createShadowUser:: existing shadow user: {}", () -> existingShadowUser);
        return null;
      } catch (FeignException.NotFound e) {
        log.info("createShadowUser:: user {} not found in remote tenant ({})", userId, remoteTenantId);
      }
      log.info("createShadowUser:: creating shadow user {} in remote tenant ({})", userId, remoteTenantId);
      User newShadowUser = usersClient.postUser(buildShadowUser(realUser));
      log.info("createShadowUser:: shadow user {} created in remote tenant ({})",
        newShadowUser.getId(), remoteTenantId);
      log.debug("createShadowUser:: created shadow user: {}", () -> newShadowUser);

      return newShadowUser;
    });
  }

  private User fetchUser(String userId) {
    User realUser;
    try {
      realUser = usersClient.getUser(userId);
    } catch (FeignException.NotFound e) {
      log.error("fetchUser:: user {} not found in local tenant", userId, e);
      throw new ResourceNotFoundException(ResourceType.USER, userId, e);
    }
    log.info("fetchUser:: user {} found in local tenant", userId);
    log.debug("fetchUser:: real user: {}", () -> realUser);

    return realUser;
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
