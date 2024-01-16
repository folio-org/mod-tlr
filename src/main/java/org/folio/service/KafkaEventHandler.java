package org.folio.service;

public interface KafkaEventHandler {
  void handle(String event);
}
