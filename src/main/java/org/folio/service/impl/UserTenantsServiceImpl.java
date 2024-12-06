package org.folio.service.impl;

import java.util.List;

import org.folio.client.feign.UserTenantsClient;
import org.folio.domain.dto.UserTenant;
import org.folio.domain.dto.UserTenantCollection;
import org.folio.service.UserTenantsService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserTenantsServiceImpl implements UserTenantsService {

  private final UserTenantsClient userTenantsClient;

  @Override
  public UserTenant findFirstUserTenant() {
    log.info("findFirstUserTenant:: finding first userTenant");
    UserTenant firstUserTenant = null;
    UserTenantCollection userTenantCollection = userTenantsClient.getUserTenants(1);
    log.debug("findFirstUserTenant:: userTenantCollection: {}", () -> userTenantCollection);
    if (userTenantCollection != null) {
      log.debug("findFirstUserTenant:: userTenantCollection: {}", () -> userTenantCollection);
      List<UserTenant> userTenants = userTenantCollection.getUserTenants();
      if (!userTenants.isEmpty()) {
        firstUserTenant = userTenants.get(0);
        log.debug("findFirstUserTenant:: found userTenant: {}", firstUserTenant);
      }
    }
    log.debug("findFirstUserTenant:: result: {}", firstUserTenant);
    return firstUserTenant;
  }

  @Override
  public String getCentralTenantId() {
    return findFirstUserTenant().getCentralTenantId();
  }
}

