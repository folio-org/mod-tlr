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
    return folioContext.getTenantId();
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
    return getCentralTenantId().equals(tenantId);
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
      .orElseThrow(() -> new IllegalStateException("Failed to fetch user tenant"));
  }

  public static void clearCache() {
    log.info("clearCache:: clearing cache");
    CACHE.clear();
  }

  private record TenantContext(String tenantId, String consortiumId, String centralTenantId) { }

}
