package org.folio.listener.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.folio.domain.dto.Item;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.RequestsBatchUpdate;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.exception.KafkaEventDeserializationException;
import org.folio.service.ConsortiaService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.impl.ItemEventHandler;
import org.folio.service.impl.LoanEventHandler;
import org.folio.service.impl.RequestBatchUpdateEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.service.impl.UserEventHandler;
import org.folio.service.impl.UserGroupEventHandler;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.kafka.DefaultKafkaEvent;
import org.folio.support.kafka.InventoryKafkaEvent;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
@RequiredArgsConstructor
public class KafkaEventListener {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final RequestEventHandler requestEventHandler;
  private final LoanEventHandler loanEventHandler;
  private final ItemEventHandler itemEventHandler;
  private final UserGroupEventHandler userGroupEventHandler;
  private final UserEventHandler userEventHandler;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final RequestBatchUpdateEventHandler requestBatchEventHandler;
  private final ConsortiaService consortiaService;
  private final FolioModuleMetadata folioModuleMetadata;

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestEvent(String eventString, @Headers Map<String, Object> messageHeaders) {
    handleEvent(eventString, requestEventHandler, messageHeaders, DefaultKafkaEvent.class, Request.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.loan",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleLoanEvent(String eventString, @Headers Map<String, Object> messageHeaders) {
    handleEvent(eventString, loanEventHandler, messageHeaders, DefaultKafkaEvent.class, Loan.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.inventory\\.item",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleItemEvent(String eventString, @Headers Map<String, Object> messageHeaders) {
    handleEvent(eventString, itemEventHandler, messageHeaders, InventoryKafkaEvent.class, Item.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request-queue-reordering",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestBatchUpdateEvent(String eventString, @Headers Map<String, Object> messageHeaders) {
    handleEvent(eventString, requestBatchEventHandler, messageHeaders, DefaultKafkaEvent.class, RequestsBatchUpdate.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.userGroup",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserGroupEvent(String eventString, @Headers Map<String, Object> messageHeaders) {
    handleEvent(eventString, userGroupEventHandler, messageHeaders, DefaultKafkaEvent.class, UserGroup.class);
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.users\\.users",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleUserEvent(String eventString, @Headers Map<String, Object> messageHeaders) {
    handleEvent(eventString, userEventHandler, messageHeaders, DefaultKafkaEvent.class, User.class);
  }

  private <E, T> void handleEvent(String eventString, KafkaEventHandler<T> handler,
    Map<String, Object> messageHeaders, Class<E> kafkaEventClass, Class<T> payloadType) {

    log.debug("handleEvent:: event: {}", () -> eventString);
    KafkaEvent<T> event = deserialize(eventString, messageHeaders, kafkaEventClass, payloadType);
    log.info("handleEvent:: event received: {}", event::getId);

    FolioExecutionContext context = DefaultFolioExecutionContext.fromMessageHeaders(
      folioModuleMetadata, messageHeaders);

    try (FolioExecutionContextSetter ignored = new FolioExecutionContextSetter(context)) {
      String centralTenantId = consortiaService.getCentralTenantId();
      systemUserScopedExecutionService.executeAsyncSystemUserScoped(centralTenantId,
        () -> handler.handle(event));
    } catch (Exception e) {
      log.error("handleEvent:: failed to handle event {}", event.getId(), e);
    }
    log.info("handleEvent:: event consumed: {}", event::getId);
  }

  private static <E, T> KafkaEvent<T> deserialize(String eventString, Map<String, Object> messageHeaders,
    Class<E> kafkaEventClass, Class<T> dataType) {

    try {
      JavaType eventType = objectMapper
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .getTypeFactory()
        .constructParametricType(kafkaEventClass, dataType);
      return objectMapper.<KafkaEvent<T>>readValue(eventString, eventType)
        .withTenantIdHeaderValue(getHeaderValue(messageHeaders, XOkapiHeaders.TENANT))
        .withUserIdHeaderValue(getHeaderValue(messageHeaders, XOkapiHeaders.USER_ID));
    } catch (JsonProcessingException e) {
      log.error("deserialize:: failed to deserialize event", e);
      throw new KafkaEventDeserializationException(e);
    }
  }

  private static String getHeaderValue(Map<String, Object> headers, String headerName) {
    log.debug("getHeaderValue:: headers: {}, headerName: {}", () -> headers, () -> headerName);
    var headerValue = headers.get(headerName);
    if (headerValue == null) {
      throw new KafkaEventDeserializationException(
        String.format("Failed to get [%s] from message headers", headerName));
    }
    var value = new String((byte[]) headerValue, StandardCharsets.UTF_8);
    log.info("getHeaderValue:: header {} value is {}", headerName, value);
    return value;
  }
}
