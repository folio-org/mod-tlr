package org.folio.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.folio.client.feign.ConsortiaClient;
import org.folio.client.feign.ConsortiaConfigurationClient;
import org.folio.domain.dto.ConsortiaConfiguration;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;
import org.folio.domain.dto.UserTenant;
import org.folio.service.ConsortiaService;
import org.folio.service.UserTenantsService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiaServiceImpl implements ConsortiaService {
  private final ConsortiaClient consortiaClient;
  private final ConsortiaConfigurationClient consortiaConfigurationClient;
  private final UserTenantsService userTenantsService;

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

  @Override
  public String getCentralTenantId() {
    log.info("getCentralTenantId:: resolving central tenant ID");
    String centralTenantId = Optional.ofNullable(consortiaConfigurationClient.getConfiguration())
      .map(ConsortiaConfiguration::getCentralTenantId)
      .orElseThrow();
    log.info("getCentralTenantId:: central tenant ID: {}", centralTenantId);
    return centralTenantId;
  }
}
