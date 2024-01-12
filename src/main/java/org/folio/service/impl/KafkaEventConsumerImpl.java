package org.folio.service.impl;

import org.folio.service.KafkaEventConsumer;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class KafkaEventConsumerImpl implements KafkaEventConsumer {

  @Override
  public void consume(String message) {
    log.info("consume:: message consumed : {}", message);
  }
}
