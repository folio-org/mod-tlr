package org.folio.listener.kafka;

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
  public static final String CENTRAL_TENANT_ID = "consortium";
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
    KafkaEvent kafkaEvent = new KafkaEvent(event);
    log.info("handleRequestEvent:: event received: {}", kafkaEvent.getEventId());
    log.debug("handleRequestEvent:: event: {}", () -> event);
    systemUserScopedExecutionService.executeAsyncSystemUserScoped(CENTRAL_TENANT_ID, () ->
      eventHandler.handleRequestEvent(kafkaEvent));
    log.info("handleRequestEvent:: event consumed: {}", kafkaEvent.getEventId());
  }
}
