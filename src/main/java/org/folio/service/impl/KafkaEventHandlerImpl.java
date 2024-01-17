package org.folio.service.impl;

import org.folio.service.KafkaEventHandler;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class KafkaEventHandlerImpl implements KafkaEventHandler {

  @Override
  public void handle(String event) {
    log.info("handle:: event consumed: {}", event);
  }
}
