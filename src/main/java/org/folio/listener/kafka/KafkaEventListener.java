package org.folio.listener.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.folio.service.KafkaEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class KafkaEventListener {
  private final KafkaEventHandler eventHandler;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  public KafkaEventListener(@Autowired KafkaEventHandler eventHandler,
    @Autowired SystemUserScopedExecutionService systemUserScopedExecutionService) {
    this.eventHandler = eventHandler;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
  }

  @KafkaListener(
    topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void handleRequestEvent(String event) {
    try {
      KafkaEvent kafkaEvent = new KafkaEvent(event);
      log.info("handleRequestEvent:: event received: {}", kafkaEvent.getEventId());
      log.debug("handleRequestEvent:: event: {}", event);
      systemUserScopedExecutionService.executeAsyncSystemUserScoped(kafkaEvent.getTenant(), () ->
        eventHandler.handleRequestEvent(kafkaEvent));
      log.info("handleRequestEvent:: event consumed: {}", kafkaEvent.getEventId());
    } catch (JsonProcessingException e) {
      log.error("handleRequestEvent:: could not parse input payload for processing event", e);
    }
  }
}
