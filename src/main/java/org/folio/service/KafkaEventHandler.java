package org.folio.service;

import org.folio.support.KafkaEvent;

public interface KafkaEventHandler<T> {
  void handle(KafkaEvent<T> event);
}
