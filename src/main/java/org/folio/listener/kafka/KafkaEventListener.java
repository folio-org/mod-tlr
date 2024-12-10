package org.folio.listener.kafka;

import static org.folio.domain.Constants.CENTRAL_TENANT_ID;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestsBatchUpdate;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.exception.KafkaEventDeserializationException;
import org.folio.service.KafkaEventHandler;
import org.folio.service.impl.LoanEventHandler;
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
  private final LoanEventHandler loanEventHandler;
  private final UserGroupEventHandler userGroupEventHandler;
  private final UserEventHandler userEventHandler;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final RequestBatchUpdateEventHandler requestBatchEventHandler;

  @Autowired
  public KafkaEventListener(RequestEventHandler requestEventHandler,
    LoanEventHandler loanEventHandler, RequestBatchUpdateEventHandler requestBatchEventHandler,
    SystemUserScopedExecutionService systemUserScopedExecutionService,
    UserGroupEventHandler userGroupEventHandler, UserEventHandler userEventHandler) {

    this.requestEventHandler = requestEventHandler;
    this.loanEventHandler = loanEventHandler;
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
    handleEvent(eventString, requestEventHandler, messageHeaders, Request.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.loan",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleLoanEvent(String eventString, MessageHeaders messageHeaders) {
    handleEvent(eventString, loanEventHandler, messageHeaders, Loan.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request-queue-reordering",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestBatchUpdateEvent(String eventString, MessageHeaders messageHeaders) {
    handleEvent(eventString, requestBatchEventHandler, messageHeaders, RequestsBatchUpdate.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.userGroup",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserGroupEvent(String eventString, MessageHeaders messageHeaders) {
    handleEvent(eventString, userGroupEventHandler, messageHeaders, UserGroup.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.users",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserEvent(String eventString, MessageHeaders messageHeaders) {
    handleEvent(eventString, userEventHandler, messageHeaders, User.class);
  }

  private <T> void handleEvent(String eventString, KafkaEventHandler<T> handler,
    MessageHeaders messageHeaders, Class<T> payloadType) {

    log.debug("handleEvent:: event: {}", () -> eventString);
    KafkaEvent<T> event = deserialize(eventString, messageHeaders, payloadType);
    log.info("handleEvent:: event received: {}", event::getId);
    try {
      systemUserScopedExecutionService.executeAsyncSystemUserScoped(CENTRAL_TENANT_ID,
        () -> handler.handle(event));
    } catch (Exception e) {
      log.error("handleEvent:: failed to handle event in tenant {}", CENTRAL_TENANT_ID);
    }
    log.info("handleEvent:: event consumed: {}", event::getId);
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
