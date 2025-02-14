package org.folio.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.folio.domain.dto.UserTenant;
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
    TenantContext tenantContext;
    if (CACHE.containsKey(tenantId)) {
      tenantContext = CACHE.get(tenantId);
      log.info("getTenantContext:: cache hit: {}", tenantContext);
    } else {
      log.info("getTenantContext:: cache miss for tenant {}", tenantId);
      tenantContext = Optional.ofNullable(userTenantsService.findFirstUserTenant())
        .map(userTenant -> new TenantContext(tenantId, userTenant))
        .orElseThrow();
      log.info("getTenantContext:: populating cache: {}", tenantContext);
      CACHE.put(tenantId, tenantContext);
    }
    log.debug("getTenantContext:: cache: {}", CACHE);
    return tenantContext;
  }

  public static void clearCache() {
    log.info("clearCache:: clearing cache");
    CACHE.clear();
  }

  private record TenantContext(String tenantId, String consortiumId, String centralTenantId) {
    public TenantContext(String tenantId, UserTenant userTenant) {
      this(tenantId, userTenant.getConsortiumId(), userTenant.getTenantId());
    }
  }

}
