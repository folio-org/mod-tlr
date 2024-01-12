package org.folio.service;

public interface KafkaEventConsumer {
  void consume(String message);
}
