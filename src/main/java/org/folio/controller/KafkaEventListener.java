package org.folio.controller;

import org.folio.service.KafkaEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class KafkaEventListener {

  private final KafkaEventHandler eventHandler;

  @Autowired
  public KafkaEventListener(KafkaEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  @KafkaListener(topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request", groupId = "${kafka.consumer.group-id}")
  public void handleRequestUpdateEvent(String message) {
    log.info("handleRequestUpdateEvent:: message received: {}", message);
    eventHandler.handle(message);
  }

}