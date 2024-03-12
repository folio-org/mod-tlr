package org.folio.listener.kafka;

import org.folio.service.KafkaEventHandler;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import lombok.extern.log4j.Log4j2;
import static org.folio.support.KafkaEvent.getHeaderValue;

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
  public void handleRequestEvent(String event, MessageHeaders messageHeaders) {
    String tenantId = getHeaderValue(messageHeaders, XOkapiHeaders.TENANT, null).get(0);
    KafkaEvent kafkaEvent = new KafkaEvent(event);
      log.info("handleRequestEvent:: event received: {}", kafkaEvent.getEventId());
      log.debug("handleRequestEvent:: event: {}", () -> event);
      systemUserScopedExecutionService.executeAsyncSystemUserScoped(tenantId, () ->
        eventHandler.handleRequestEvent(kafkaEvent));
      log.info("handleRequestEvent:: event consumed: {}", kafkaEvent.getEventId());
  }
}
