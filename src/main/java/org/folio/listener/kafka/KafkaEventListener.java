package org.folio.listener.kafka;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.UserGroup;
import org.folio.exception.KafkaEventDeserializationException;
import org.folio.service.KafkaEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.service.impl.UserGroupEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class KafkaEventListener {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String CENTRAL_TENANT_ID = "consortium";
  private final RequestEventHandler requestEventHandler;
  private final UserGroupEventHandler userGroupEventHandler;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  public KafkaEventListener(@Autowired RequestEventHandler requestEventHandler,
    @Autowired SystemUserScopedExecutionService systemUserScopedExecutionService,
    @Autowired UserGroupEventHandler userGroupEventHandler) {

    this.requestEventHandler = requestEventHandler;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
    this.userGroupEventHandler = userGroupEventHandler;
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestEvent(String eventString) {
    log.debug("handleRequestEvent:: event: {}", () -> eventString);
    KafkaEvent<Request> event = deserialize(eventString, Request.class);
    log.info("handleRequestEvent:: event received: {}", event::getId);
    handleEvent(event, requestEventHandler);
    log.info("handleRequestEvent:: event consumed: {}", event::getId);
  }

  private <T> void handleEvent(KafkaEvent<T> event, KafkaEventHandler<T> handler) {
    systemUserScopedExecutionService.executeAsyncSystemUserScoped(CENTRAL_TENANT_ID,
      () -> handler.handle(event, null));
  }

  private <T> void handleEvent(KafkaEvent<T> event, MessageHeaders messageHeaders,
    KafkaEventHandler<T> handler) {

    systemUserScopedExecutionService.executeAsyncSystemUserScoped(CENTRAL_TENANT_ID,
      () -> handler.handle(event, messageHeaders));
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.userGroup",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserGroupEvent(String eventString, MessageHeaders messageHeaders) {
    KafkaEvent<UserGroup> event = deserialize(eventString, UserGroup.class);

    log.info("handleUserGroupEvent:: event received: {}", event::getId);
    log.debug("handleUserGroupEvent:: event: {}", () -> event);
//    KafkaEvent.EventType eventType = event.getType();
//    if (eventType == KafkaEvent.EventType.CREATED) {
//      userGroupEventHandler.handleUserGroupCreatingEvent(event, messageHeaders);
//    }
//    if (eventType == KafkaEvent.EventType.UPDATED) {
//      userGroupEventHandler.handleUserGroupUpdatingEvent(event, messageHeaders);
//    }
    handleEvent(event, messageHeaders, userGroupEventHandler);
  }

  private static <T> KafkaEvent<T> deserialize(String eventString, Class<T> dataType) {
    try {
      JavaType eventType = objectMapper.getTypeFactory()
        .constructParametricType(KafkaEvent.class, dataType);
      return objectMapper.readValue(eventString, eventType);
    } catch (JsonProcessingException e) {
      log.error("deserialize:: failed to deserialize event", e);
      throw new KafkaEventDeserializationException(e);
    }
  }
}
