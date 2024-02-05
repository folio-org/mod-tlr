package org.folio.service.impl;

import lombok.AllArgsConstructor;
import org.folio.service.EcsTlrService;
import org.folio.service.KafkaEventHandler;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

import java.util.UUID;

@AllArgsConstructor
@Service
@Log4j2
public class KafkaEventHandlerImpl implements KafkaEventHandler {

  private final EcsTlrService ecsTlrService;
  @Override
  public void handleRequestEvent(String event) {
    log.info("handle:: request event consumed: {}", event);
    KafkaEvent kafkaEvent = new KafkaEvent(event);
    if (kafkaEvent.getEventType() == KafkaEvent.EventType.UPDATED) {
      if (kafkaEvent.getNewNode().has("itemId")) {
        ecsTlrService.updateRequestItem(UUID.fromString(kafkaEvent.getNewNode().get("id").asText()),
          UUID.fromString(kafkaEvent.getNewNode().get("itemId").asText()));
      }
    }
  }
}
