package org.folio.service.impl;

import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.folio.support.KafkaEvent.ITEM_ID;
import static org.folio.support.KafkaEvent.getUUIDFromNode;

import org.folio.service.EcsTlrService;
import org.folio.service.KafkaEventHandler;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class KafkaEventHandlerImpl implements KafkaEventHandler {

  private final EcsTlrService ecsTlrService;

  @Override
  public void handleRequestEvent(KafkaEvent event) {
    log.info("handleRequestEvent:: processing request event: {}", () -> event);
    if (event.getEventType() == UPDATED && event.hasNewNode() && event.getNewNode().has(ITEM_ID)) {
      log.info("handleRequestEvent:: handling request event: {}", () -> event);
      ecsTlrService.handleSecondaryRequestUpdate(getUUIDFromNode(event.getNewNode(), "id"),
        getUUIDFromNode(event.getNewNode(), ITEM_ID));
    } else {
      log.info("handleRequestEvent:: ignoring event: {}", () -> event);
    }
    log.info("handleRequestEvent:: request event processed: {}", () -> event);
  }
}
