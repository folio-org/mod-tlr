package org.folio.service;

import static org.mockito.Mockito.when;

import org.folio.api.BaseIT;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

abstract class BaseEventHandlerTest extends BaseIT {
  protected static final String TENANT = "consortium";
  protected static final String TENANT_ID = "a8b9a084-abbb-4299-be13-9fdc19249928";
  protected static final String CONSORTIUM_ID = "785d5c71-399d-4978-bdff-fb88b72d140a";
  protected static final String CENTRAL_TENANT_ID = "consortium";

  @MockitoBean
  protected ConsortiumService consortiumService;
  @MockitoBean
  protected ConsortiaService consortiaService;
  @MockitoSpyBean
  protected SystemUserScopedExecutionService systemUserScopedExecutionService;
  @Autowired
  protected KafkaEventListener eventListener;

  protected void mockConsortiumService() {
    when(consortiumService.getCurrentConsortiumId()).thenReturn(CONSORTIUM_ID);
    when(consortiumService.getCentralTenantId()).thenReturn(CENTRAL_TENANT_ID);
    when(consortiumService.isCurrentTenantConsortiumMember()).thenReturn(true);
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
