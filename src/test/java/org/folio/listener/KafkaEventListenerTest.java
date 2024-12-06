package org.folio.listener;

import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.folio.listener.kafka.KafkaEventListener;
import org.folio.service.impl.LoanEventHandler;
import org.folio.service.impl.RequestBatchUpdateEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.service.impl.UserEventHandler;
import org.folio.service.impl.UserGroupEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageHeaders;

@ExtendWith(MockitoExtension.class)
class KafkaEventListenerTest {
  @Mock
  RequestEventHandler requestEventHandler;
  @Mock
  LoanEventHandler loanEventHandler;
  @Mock
  RequestBatchUpdateEventHandler requestBatchEventHandler;
  @Mock
  SystemUserScopedExecutionService systemUserScopedExecutionService;
  @Mock
  UserGroupEventHandler userGroupEventHandler;
  @Mock
  UserEventHandler userEventHandler;

  @Test
  void shouldHandleExceptionInEventHandler() {
    doThrow(new NullPointerException("NPE")).when(systemUserScopedExecutionService)
      .executeAsyncSystemUserScoped(any(), any());
    KafkaEventListener kafkaEventListener = new KafkaEventListener(requestEventHandler,
      loanEventHandler, requestBatchEventHandler, systemUserScopedExecutionService,
      userGroupEventHandler, userEventHandler);
    kafkaEventListener.handleRequestEvent("{}",
      new MessageHeaders(Map.of(TENANT, "default".getBytes())));

    verify(systemUserScopedExecutionService).executeAsyncSystemUserScoped(any(), any());
  }
}
