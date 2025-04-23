package org.folio.listener;

import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.folio.listener.kafka.KafkaEventListener;
import org.folio.service.ConsortiaService;
import org.folio.service.impl.LoanEventHandler;
import org.folio.service.impl.RequestBatchUpdateEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.service.impl.UserEventHandler;
import org.folio.service.impl.UserGroupEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
  @Mock
  ConsortiaService consortiaService;
  @InjectMocks
  KafkaEventListener kafkaEventListener;

  @Test
  void shouldHandleExceptionInEventHandler() {
    doThrow(new NullPointerException("NPE")).when(systemUserScopedExecutionService)
      .executeAsyncSystemUserScoped(any(), any());
    kafkaEventListener.handleRequestEvent("{}",
      new MessageHeaders(Map.of(
        TENANT, "default".getBytes(),
        USER_ID, "08d51c7a-0f36-4f3d-9e35-d285612a23df".getBytes()
      )));

    verify(systemUserScopedExecutionService).executeAsyncSystemUserScoped(any(), any());
  }
}
