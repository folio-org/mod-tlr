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
    log.info("findFirstUser:: finding a first userTenant");
    UserTenant firstUserTenant = null;
//    List<UserTenant> userTenants = userTenantsClient.getUserTenants(1).getUserTenants();
    UserTenantCollection userTenantCollection = userTenantsClient.getUserTenants(1);
    log.info("findFirstUserTenant:: userTenantCollection: {}", userTenantCollection);
    List<UserTenant> userTenants = userTenantCollection.getUserTenants();
    if (!userTenants.isEmpty()) {
      firstUserTenant = userTenants.get(0);
      log.info("findFirstUserTenant:: found userTenant: {}", firstUserTenant);
    }
    return firstUserTenant;
  }
}
