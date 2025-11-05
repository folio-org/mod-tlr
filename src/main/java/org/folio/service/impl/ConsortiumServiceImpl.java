package org.folio.service.impl;

import static org.apache.commons.lang3.ObjectUtils.requireNonEmpty;
import static org.apache.commons.lang3.StringUtils.isAnyBlank;

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
    requireNonEmpty(tenantId, "tenantId can not be null or empty");
    TenantContext cachedContext = CACHE.get(tenantId);
    if (cachedContext != null) {
      log.info("getTenantContext:: cache hit: {}", cachedContext);
      return cachedContext;
    }

    log.info("getTenantContext:: cache miss for tenant {}", tenantId);
    UserTenant userTenant = userTenantsService.findFirstUserTenant();
    if (isValid(userTenant)) {
      TenantContext newContext = new TenantContext(tenantId, userTenant.getConsortiumId(),
        userTenant.getCentralTenantId());
      addToCache(newContext);
      return newContext;
    }

    log.info("getTenantContext:: building temporary empty context for tenant {}", tenantId);
    return new TenantContext(tenantId, null, null);
  }

  private static boolean isValid(UserTenant userTenant) {
    if (userTenant == null) {
      log.warn("isValid:: user-tenant is null");
      return false;
    }
    if (isAnyBlank(userTenant.getConsortiumId(), userTenant.getCentralTenantId())) {
      log.warn("isValid:: user-tenant lacks one or more required properties: {}", userTenant);
      return false;
    }
    return true;
  }

  private static void addToCache(TenantContext context) {
    log.info("addToCache:: caching: {}", context);
    CACHE.put(context.tenantId(), context);
    log.debug("addToCache:: cache: {}", CACHE);
  }

  public static void clearCache() {
    log.info("clearCache:: clearing cache");
    CACHE.clear();
  }

  private record TenantContext(String tenantId, String consortiumId, String centralTenantId) { }

}
