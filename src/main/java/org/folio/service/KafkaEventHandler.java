package org.folio.service;

public interface KafkaEventHandler {
  void handleRequestEvent(String event);
}
