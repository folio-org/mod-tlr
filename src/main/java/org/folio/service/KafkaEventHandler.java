package org.folio.service;

import org.folio.support.KafkaEvent;

public interface KafkaEventHandler {
  void handleRequestEvent(KafkaEvent event);
}
