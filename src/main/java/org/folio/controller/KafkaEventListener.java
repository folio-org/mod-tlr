package org.folio.controller;

import org.folio.service.KafkaEventConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
@AllArgsConstructor
public class KafkaEventListener {

  @Autowired
  private final KafkaEventConsumer consumerService;

  @KafkaListener(topicPattern = "${folio.environment}\\.\\w+\\.circulation\\.request", groupId = "${kafka.consumer.group-id}")
  public void handleRequestUpdateEvent(String message) {
    log.info("listen:: message received: {}", message);
    consumerService.consume(message);
  }

}