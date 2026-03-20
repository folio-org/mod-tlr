package org.folio.service;

import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.folio.util.TestUtils.mockFolioExecutionContextService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.domain.dto.User;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class UserEventHandlerTest extends BaseEventHandlerTest {
  private static final String USER_UPDATING_EVENT_SAMPLE = getMockDataAsString(
    "mockdata/kafka/user_updating_event.json");
  @MockitoBean
  private UserService userService;

  @Test
  void handleUserUpdatingEventShouldUpdateUserForAllDataTenants() {
    mockConsortiumService();
    when(consortiaService.getAllConsortiumTenants(anyString())).thenReturn(mockTenantCollection());
    when(userService.update(any(User.class))).thenReturn(new User());
    mockFolioExecutionContextService(contextService);

    eventListener.handleUserEvent(USER_UPDATING_EVENT_SAMPLE, buildKafkaHeaders(CENTRAL_TENANT_ID));

    verify(contextService, times(3))
      .execute(anyString(), any(FolioExecutionContext.class), any(Runnable.class));
    verify(userService, times(2)).update(any(User.class));
  }
}
