package org.folio.service;

import static java.util.Collections.EMPTY_MAP;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.api.BaseIT;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserTenant;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.MessageHeaders;

class UserGroupEventHandlerTest extends BaseIT {
  private static final String USER_GROUP_CREATING_EVENT_SAMPLE = getMockDataAsString(
    "mockdata/kafka/usergroup_creating_event.json");
  private static final String USER_GROUP_UPDATING_EVENT_SAMPLE = getMockDataAsString(
    "mockdata/kafka/usergroup_updating_event.json");
  private static final String TENANT = "consortium";
  private static final String TENANT_ID = "a8b9a084-abbb-4299-be13-9fdc19249928";
  private static final String CONSORTIUM_ID = "785d5c71-399d-4978-bdff-fb88b72d140a";
  private static final String CENTRAL_TENANT_ID = "consortium";
  private static final String USER_GROUP_ID = "a1070927-53a1-4c3b-86be-f9f32b5bcab3";

  @MockBean
  private UserTenantsService userTenantsService;
  @MockBean
  private ConsortiaService consortiaService;
  @SpyBean
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @MockBean
  private UserGroupService userGroupService;
  @Autowired
  private KafkaEventListener eventListener;

  @Test
  void handleUserGroupCreatingEventShouldCreateUserGroupForAllDataTenants() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllDataTenants(anyString())).thenReturn(mockTenantCollection());
    when(userGroupService.create(any(UserGroup.class))).thenReturn(new UserGroup());

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    eventListener.handleUserGroupEvent(USER_GROUP_CREATING_EVENT_SAMPLE,
      getMessageHeaders(TENANT, TENANT_ID));

    verify(systemUserScopedExecutionService, times(3)).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));
    verify(userGroupService, times(2)).create(any(UserGroup.class));
  }

  @Test
  void handleUserGroupUpdatingEventShouldUpdateUserGroupForAllDataTenants() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllDataTenants(anyString())).thenReturn(mockTenantCollection());
    when(userGroupService.update(any(UserGroup.class))).thenReturn(new UserGroup());

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    eventListener.handleUserGroupEvent(USER_GROUP_UPDATING_EVENT_SAMPLE,
      getMessageHeaders(TENANT, TENANT_ID));

    verify(systemUserScopedExecutionService, times(3))
      .executeAsyncSystemUserScoped(anyString(), any(Runnable.class));
    verify(userGroupService, times(2)).update(any(UserGroup.class));
  }

  @Test
  void handleUserGroupCreatingEventShouldNotCreateUserGroupWithEmptyHeaders() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllDataTenants(anyString())).thenReturn(mockTenantCollection());
    when(userGroupService.create(any(UserGroup.class))).thenReturn(new UserGroup());

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    eventListener.handleUserGroupEvent(USER_GROUP_CREATING_EVENT_SAMPLE,
      new MessageHeaders(EMPTY_MAP));

    verify(systemUserScopedExecutionService, times(1)).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));
    verify(userGroupService, times(0)).create(any(UserGroup.class));
  }

  private UserTenant mockUserTenant() {
    return new UserTenant()
      .centralTenantId(CENTRAL_TENANT_ID)
      .consortiumId(CONSORTIUM_ID);
  }

  private TenantCollection mockTenantCollection() {
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
