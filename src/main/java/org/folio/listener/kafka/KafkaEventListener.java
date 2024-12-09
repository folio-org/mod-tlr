package org.folio.listener.kafka;

import static org.folio.domain.Constants.CENTRAL_TENANT_ID;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestsBatchUpdate;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.exception.KafkaEventDeserializationException;
import org.folio.service.KafkaEventHandler;
import org.folio.service.impl.RequestBatchUpdateEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.service.impl.UserEventHandler;
import org.folio.service.impl.UserGroupEventHandler;
import org.folio.spring.integration.XOkapiHeaders;
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
  private final RequestEventHandler requestEventHandler;
  private final UserGroupEventHandler userGroupEventHandler;
  private final UserEventHandler userEventHandler;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final RequestBatchUpdateEventHandler requestBatchEventHandler;

  public KafkaEventListener(@Autowired RequestEventHandler requestEventHandler,
    @Autowired RequestBatchUpdateEventHandler requestBatchEventHandler,
    @Autowired SystemUserScopedExecutionService systemUserScopedExecutionService,
    @Autowired UserGroupEventHandler userGroupEventHandler,
    @Autowired UserEventHandler userEventHandler) {

    this.requestEventHandler = requestEventHandler;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
    this.userGroupEventHandler = userGroupEventHandler;
    this.requestBatchEventHandler = requestBatchEventHandler;
    this.userEventHandler = userEventHandler;
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestEvent(String eventString, MessageHeaders messageHeaders) {
    log.debug("handleRequestEvent:: event: {}", () -> eventString);
    KafkaEvent<Request> event = deserialize(eventString, messageHeaders, Request.class);
    log.info("handleRequestEvent:: event received: {}", event::getId);
    handleEvent(event, requestEventHandler);
    log.info("handleRequestEvent:: event consumed: {}", event::getId);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request-queue-reordering",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestBatchUpdateEvent(String eventString, MessageHeaders messageHeaders) {
    log.debug("handleRequestBatchUpdateEvent:: event: {}", () -> eventString);
    KafkaEvent<RequestsBatchUpdate> event = deserialize(eventString, messageHeaders, RequestsBatchUpdate.class);
    log.info("handleRequestBatchUpdateEvent:: event received: {}", event::getId);
    handleEvent(event, requestBatchEventHandler);
    log.info("handleRequestBatchUpdateEvent:: event consumed: {}", event::getId);
  }

  private <T> void handleEvent(KafkaEvent<T> event, KafkaEventHandler<T> handler) {
    try {
      systemUserScopedExecutionService.executeAsyncSystemUserScoped(CENTRAL_TENANT_ID,
        () -> handler.handle(event));
    } catch (Exception e) {
      log.error("handleEvent:: Failed to handle Kafka event in tenant {}", CENTRAL_TENANT_ID);
    }
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.userGroup",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserGroupEvent(String eventString, MessageHeaders messageHeaders) {
    KafkaEvent<UserGroup> event = deserialize(eventString, messageHeaders, UserGroup.class);

    log.info("handleUserGroupEvent:: event received: {}", event::getId);
    log.debug("handleUserGroupEvent:: event: {}", () -> event);
    handleEvent(event, userGroupEventHandler);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.users",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserEvent(String eventString, MessageHeaders messageHeaders) {
    KafkaEvent<User> event = deserialize(eventString, messageHeaders, User.class);

    log.info("handleUserEvent:: event received: {}", event::getId);
    handleEvent(event, userEventHandler);
  }

  private static <T> KafkaEvent<T> deserialize(String eventString, MessageHeaders messageHeaders,
    Class<T> dataType) {

    try {
      JavaType eventType = objectMapper.getTypeFactory()
        .constructParametricType(KafkaEvent.class, dataType);
      var kafkaEvent = objectMapper.<KafkaEvent<T>>readValue(eventString, eventType);
      return Optional.ofNullable(getHeaderValue(messageHeaders, XOkapiHeaders.TENANT))
        .map(kafkaEvent::withTenantIdHeaderValue)
        .orElseThrow(() -> new KafkaEventDeserializationException(
          "Failed to get tenant ID from message headers"));
    } catch (JsonProcessingException e) {
      log.error("deserialize:: failed to deserialize event", e);
      throw new KafkaEventDeserializationException(e);
    }
  }

  private static String getHeaderValue(MessageHeaders headers, String headerName) {
    log.debug("getHeaderValue:: headers: {}, headerName: {}", () -> headers, () -> headerName);
    var headerValue = headers.get(headerName);
    var value = headerValue == null
      ? null
      : new String((byte[]) headerValue, StandardCharsets.UTF_8);
    log.info("getHeaderValue:: header {} value is {}", headerName, value);
    return value;
  }
}
