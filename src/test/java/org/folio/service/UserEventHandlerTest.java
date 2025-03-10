package org.folio.service;

import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.domain.dto.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class UserEventHandlerTest extends BaseEventHandlerTest {
  private static final String USER_UPDATING_EVENT_SAMPLE = getMockDataAsString(
    "mockdata/kafka/user_updating_event.json");
  @MockitoBean
  private UserService userService;

  @Test
  void handleUserUpdatingEventShouldUpdateUserForAllDataTenants() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(mockUserTenant());
    when(consortiaService.getAllConsortiumTenants(anyString())).thenReturn(mockTenantCollection());
    when(userService.update(any(User.class))).thenReturn(new User());
    when(consortiaService.getCentralTenantId()).thenReturn(CENTRAL_TENANT_ID);

    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    eventListener.handleUserEvent(USER_UPDATING_EVENT_SAMPLE, buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(systemUserScopedExecutionService, times(3))
      .executeAsyncSystemUserScoped(anyString(), any(Runnable.class));
    verify(userService, times(2)).update(any(User.class));
  }
}
