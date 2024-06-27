package org.folio.service;

import org.folio.support.KafkaEvent;
import org.springframework.messaging.MessageHeaders;

public interface KafkaEventHandler<T> {
  void handle(KafkaEvent<T> event, MessageHeaders messageHeaders);
}
