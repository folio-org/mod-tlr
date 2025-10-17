package org.folio.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.folio.service.ConsortiumService;
import org.folio.service.UserTenantsService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ConsortiumServiceImpl implements ConsortiumService {

  private static final Map<String, TenantContext> CACHE = new ConcurrentHashMap<>();

  private final UserTenantsService userTenantsService;
  private final FolioExecutionContext folioContext;

  @Override
  public String getCurrentTenantId() {
    return Optional.ofNullable(folioContext.getTenantId())
      .orElseThrow(() -> new IllegalStateException("Failed to resolve current tenant ID"));
  }

  @Override
  public String getCurrentConsortiumId() {
    return getTenantContext(getCurrentTenantId())
      .consortiumId();
  }

  @Override
  public String getCentralTenantId() {
    return getTenantContext(getCurrentTenantId())
      .centralTenantId();
  }

  @Override
  public boolean isCurrentTenantCentral() {
    return isCentralTenant(getCurrentTenantId());
  }

  @Override
  public boolean isCentralTenant(String tenantId) {
    return tenantId != null && tenantId.equals(getCentralTenantId());
  }

  @Override
  public boolean isCurrentTenantConsortiumMember() {
    return getCurrentConsortiumId() != null;
  }

  private TenantContext getTenantContext(String tenantId) {
    TenantContext tenantContext = CACHE.get(tenantId);
    if (tenantContext != null) {
      log.info("getTenantContext:: cache hit: {}", tenantContext);
    } else {
      log.info("getTenantContext:: cache miss for tenant {}", tenantId);
      tenantContext = buildTenantContext(tenantId);
      log.info("getTenantContext:: caching: {}", tenantContext);
      CACHE.put(tenantId, tenantContext);
    }
    log.debug("getTenantContext:: cache: {}", CACHE);
    return tenantContext;
  }

  private TenantContext buildTenantContext(String tenantId) {
    return Optional.ofNullable(userTenantsService.findFirstUserTenant())
      .map(ut -> new TenantContext(tenantId, ut.getConsortiumId(), ut.getCentralTenantId()))
      .orElseGet(() -> new TenantContext(tenantId, null, null));
  }

  public static void clearCache() {
    log.info("clearCache:: clearing cache");
    CACHE.clear();
  }

  private record TenantContext(String tenantId, String consortiumId, String centralTenantId) { }

}
