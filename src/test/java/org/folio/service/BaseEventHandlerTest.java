package org.folio.service;

import org.folio.api.BaseIT;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;
import org.folio.domain.dto.UserTenant;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

abstract class BaseEventHandlerTest extends BaseIT {
  protected static final String TENANT = "consortium";
  protected static final String TENANT_ID = "a8b9a084-abbb-4299-be13-9fdc19249928";
  protected static final String CONSORTIUM_ID = "785d5c71-399d-4978-bdff-fb88b72d140a";
  protected static final String CENTRAL_TENANT_ID = "consortium";

  @MockBean
  protected UserTenantsService userTenantsService;
  @MockBean
  protected ConsortiaService consortiaService;
  @SpyBean
  protected SystemUserScopedExecutionService systemUserScopedExecutionService;
  @Autowired
  protected KafkaEventListener eventListener;

  protected UserTenant mockUserTenant() {
    return new UserTenant()
      .centralTenantId(CENTRAL_TENANT_ID)
      .consortiumId(CONSORTIUM_ID);
  }

  protected TenantCollection mockTenantCollection() {
    return new TenantCollection()
      .addTenantsItem(
        new Tenant()
          .id("central tenant")
          .code("11")
          .isCentral(true)
          .name("Central tenant"))
      .addTenantsItem(
        new Tenant()
          .id("first data tenant")
          .code("22")
          .isCentral(false)
          .name("First data tenant"))
      .addTenantsItem(
        new Tenant()
          .id("second data tenant")
          .code("33")
          .isCentral(false)
          .name("Second data tenant"));
  }
}
