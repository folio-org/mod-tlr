package org.folio.listener;

import static java.util.UUID.randomUUID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.folio.exception.KafkaEventDeserializationException;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.service.ConsortiumService;
import org.folio.service.impl.ItemEventHandler;
import org.folio.service.impl.LoanEventHandler;
import org.folio.service.impl.RequestBatchUpdateEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.service.impl.UserEventHandler;
import org.folio.service.impl.UserGroupEventHandler;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextService;
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
  ItemEventHandler itemEventHandler;
  @Mock
  LoanEventHandler loanEventHandler;
  @Mock
  RequestBatchUpdateEventHandler requestBatchEventHandler;
  @Mock
  FolioExecutionContextService contextService;
  @Mock
  FolioExecutionContext folioContext;
  @Mock
  UserGroupEventHandler userGroupEventHandler;
  @Mock
  UserEventHandler userEventHandler;
  @Mock
  ConsortiumService consortiumService;
  @Mock
  FolioModuleMetadata folioModuleMetadata;
  @InjectMocks
  KafkaEventListener kafkaEventListener;

  @Test
  void shouldHandleExceptionInEventHandler() {
    when(consortiumService.isCurrentTenantConsortiumMember()).thenReturn(true);
    when(consortiumService.getCentralTenantId()).thenReturn("central");
    doThrow(new NullPointerException("NPE")).when(contextService)
      .execute(any(String.class), any(FolioExecutionContext.class), any(Runnable.class));
    kafkaEventListener.handleRequestEvent("{}",
      new MessageHeaders(Map.of(
        TENANT, "default".getBytes(),
        USER_ID, "08d51c7a-0f36-4f3d-9e35-d285612a23df".getBytes()
      )));

    verify(contextService).execute(any(String.class), any(FolioExecutionContext.class), any(Runnable.class));
  }

  @Test
  void shouldNotThrowExceptionWhenHeaderUserIdIsNotFound() {
    when(consortiumService.isCurrentTenantConsortiumMember()).thenReturn(false);
    assertDoesNotThrow(() -> kafkaEventListener.handleRequestEvent("{}",
      new MessageHeaders(Map.of(TENANT, "test".getBytes()))));
  }

  @Test
  void shouldThrowExceptionWhenHeaderTenantIsNotFound() {
    MessageHeaders headers = new MessageHeaders(Map.of(USER_ID, randomUUID().toString().getBytes()));
    assertThrows(KafkaEventDeserializationException.class,
      () -> kafkaEventListener.handleRequestEvent("{}", headers));
  }

  @Test
  void shouldIgnoreEventIfTenantIsNotConsortiumMember() {
    when(consortiumService.isCurrentTenantConsortiumMember()).thenReturn(false);
    kafkaEventListener.handleRequestEvent("{}", new MessageHeaders(Map.of(TENANT, "test".getBytes())));
    verifyNoInteractions(contextService);
  }

}
