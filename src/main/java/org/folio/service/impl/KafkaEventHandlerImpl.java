package org.folio.service.impl;

import lombok.AllArgsConstructor;
import org.folio.service.EcsTlrService;
import org.folio.service.KafkaEventHandler;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

import static org.folio.support.KafkaEvent.ITEM_ID;
import static org.folio.support.KafkaEvent.getUUIDFromNode;

@AllArgsConstructor
@Service
@Log4j2
public class KafkaEventHandlerImpl implements KafkaEventHandler {

  private final EcsTlrService ecsTlrService;
  @Override
  public void handleRequestEvent(String event) {
    log.info("handle:: request event consumed: {}", event);
    KafkaEvent kafkaEvent = new KafkaEvent(event);
    if (kafkaEvent.getEventType() == KafkaEvent.EventType.UPDATED && kafkaEvent.getNewNode().has(ITEM_ID)) {
        ecsTlrService.updateRequestItem(getUUIDFromNode(kafkaEvent.getNewNode(), "id"),
          getUUIDFromNode(kafkaEvent.getNewNode(), ITEM_ID));
    }
  }
}
