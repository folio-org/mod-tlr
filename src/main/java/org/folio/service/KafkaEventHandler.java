package org.folio.service;

import org.folio.support.KafkaEvent;
import org.springframework.messaging.MessageHeaders;

public interface KafkaEventHandler {
  void handleRequestEvent(KafkaEvent event);
  void handleUserGroupCreatingEvent(KafkaEvent event, MessageHeaders messageHeaders);
  void handleUserGroupUpdatingEvent(KafkaEvent event, MessageHeaders messageHeaders);
}
