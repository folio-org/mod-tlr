package org.folio.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.folio.client.feign.ConsortiaClient;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;
import org.folio.domain.dto.UserTenant;
import org.folio.service.ConsortiaService;
import org.folio.service.UserTenantsService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiaServiceImpl implements ConsortiaService {
  private final ConsortiaClient consortiaClient;
  private final UserTenantsService userTenantsService;

  @Override
  public TenantCollection getAllDataTenants(String consortiumId) {
    return consortiaClient.getConsortiaTenants(consortiumId);
  }

  @Override
  public TenantCollection getAllConsortiumTenants(String consortiumId) {
    return consortiaClient.getConsortiaTenants(consortiumId);
  }

  @Override
  public Collection<Tenant> getAllConsortiumTenants() {
    log.info("getAllConsortiumTenants:: fetching consortium tenants");
    List<Tenant> tenants = Optional.ofNullable(userTenantsService.findFirstUserTenant())
      .map(UserTenant::getConsortiumId)
      .map(consortiaClient::getConsortiaTenants)
      .map(TenantCollection::getTenants)
      .orElseGet(Collections::emptyList);

    log.info("getAllConsortiumTenants:: found {} consortium tenants", tenants::size);
    return tenants;
  }

//  @Override
//  public boolean isCurrentTenantCentral() {
//    var userTenant = userTenantsService.findFirstUserTenant();
//    var centralTenantId = userTenant.getCentralTenantId();
//    var currentTenantId = userTenant.getTenantId();
//
//    if (centralTenantId == null || currentTenantId == null) {
//      log.warn("isCurrentTenantCentral:: Cannot determine central tenant or current tenant");
//      return false;
//    }
//
//    return centralTenantId.equals(currentTenantId);
//  }
//
//  @Override
//  public <T> T executeInTenant(String tenantId, Callable<T> action) {
//    if (isCurrentTenantCentral()) {
//      try {
//        return action.call();
//      } catch (Exception e) {
//        log.info("executeInTenant:: Failed to execute in Central tenant");
//        return null;
//      }
//    } else {
//      return systemUserScopedExecutionService.executeSystemUserScoped(
//        tenantId, action);
//    }
//  }
}
