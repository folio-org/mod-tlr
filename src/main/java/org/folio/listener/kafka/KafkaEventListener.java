package org.folio.listener.kafka;

import org.folio.domain.dto.Request;
import org.folio.service.KafkaEventHandler;
import org.folio.service.impl.RequestEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
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
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  public KafkaEventListener(@Autowired RequestEventHandler requestEventHandler,
    @Autowired SystemUserScopedExecutionService systemUserScopedExecutionService) {

    this.requestEventHandler = requestEventHandler;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
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
      () -> handler.handle(event));
  }

  private static <T> KafkaEvent<T> deserialize(String eventString, Class<T> dataType) {
    try {
      JavaType eventType = objectMapper.getTypeFactory()
        .constructParametricType(KafkaEvent.class, dataType);
      return objectMapper.readValue(eventString, eventType);
    } catch (JsonProcessingException e) {
      log.error("deserialize:: failed to deserialize event", e);
      throw new RuntimeException(e);
    }
  }

}
