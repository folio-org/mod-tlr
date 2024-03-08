package org.folio.service.impl;

import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@Primary
public class CustomTenantService extends TenantService {
  private final PrepareSystemUserService systemUserService;

  public CustomTenantService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase,
                             PrepareSystemUserService systemUserService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.systemUserService = systemUserService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    log.info("afterTenantUpdate:: start");
    log.debug("afterTenantUpdate:: parameters tenantAttributes: {}", tenantAttributes);
    systemUserService.setupSystemUser();
    log.info("afterTenantUpdate:: finished");
  }
}
