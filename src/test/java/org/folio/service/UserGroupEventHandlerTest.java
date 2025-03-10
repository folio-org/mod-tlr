package org.folio.service;

import static java.util.Collections.EMPTY_MAP;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.domain.dto.UserGroup;
import org.folio.exception.KafkaEventDeserializationException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;

import lombok.SneakyThrows;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class UserGroupEventHandlerTest extends BaseEventHandlerTest {
  private static final String USER_GROUP_CREATING_EVENT_SAMPLE = getMockDataAsString(
    "mockdata/kafka/usergroup_creating_event.json");
  private static final String USER_GROUP_UPDATING_EVENT_SAMPLE = getMockDataAsString(
    "mockdata/kafka/usergroup_updating_event.json");
  @MockitoBean
  private UserGroupService userGroupService;

  @Test
  void handleUserGroupCreatingEventShouldCreateUserGroupForAllDataTenants() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllConsortiumTenants(anyString())).thenReturn(mockTenantCollection());
    when(userGroupService.create(any(UserGroup.class))).thenReturn(new UserGroup());
    when(consortiaService.getCentralTenantId()).thenReturn(CENTRAL_TENANT_ID);

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    eventListener.handleUserGroupEvent(USER_GROUP_CREATING_EVENT_SAMPLE,
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(systemUserScopedExecutionService, times(3)).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));
    verify(userGroupService, times(2)).create(any(UserGroup.class));
  }

  @Test
  void handleUserGroupUpdatingEventShouldUpdateUserGroupForAllDataTenants() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllConsortiumTenants(anyString())).thenReturn(mockTenantCollection());
    when(userGroupService.update(any(UserGroup.class))).thenReturn(new UserGroup());
    when(consortiaService.getCentralTenantId()).thenReturn(CENTRAL_TENANT_ID);

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    eventListener.handleUserGroupEvent(USER_GROUP_UPDATING_EVENT_SAMPLE,
      buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(systemUserScopedExecutionService, times(3))
      .executeAsyncSystemUserScoped(anyString(), any(Runnable.class));
    verify(userGroupService, times(2)).update(any(UserGroup.class));
  }

  @Test
  @SneakyThrows
  void handleUserGroupCreatingEventShouldNotCreateUserGroupWithEmptyHeaders() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllConsortiumTenants(anyString())).thenReturn(mockTenantCollection());
    when(userGroupService.create(any(UserGroup.class))).thenReturn(new UserGroup());

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    try {
      eventListener.handleUserGroupEvent(USER_GROUP_CREATING_EVENT_SAMPLE,
        new MessageHeaders(EMPTY_MAP));
      verify(systemUserScopedExecutionService, times(1)).executeAsyncSystemUserScoped(
        anyString(), any(Runnable.class));
      verify(userGroupService, times(0)).create(any(UserGroup.class));
    } catch (KafkaEventDeserializationException e) {
      verify(systemUserScopedExecutionService, times(0)).executeAsyncSystemUserScoped(
        anyString(), any(Runnable.class));
      verify(userGroupService, times(0)).create(any(UserGroup.class));
    }
  }
}
