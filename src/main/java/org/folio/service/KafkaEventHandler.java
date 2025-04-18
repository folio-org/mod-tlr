package org.folio.service;

import org.folio.support.kafka.KafkaEvent;

public interface KafkaEventHandler<T> {
  void handle(KafkaEvent<T> event);
}
