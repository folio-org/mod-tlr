package org.folio.service.impl;

import static org.folio.support.KafkaEvent.EventType.CREATED;
import static org.folio.support.KafkaEvent.EventType.UPDATED;

import org.folio.domain.dto.Loan;
import org.folio.service.KafkaEventHandler;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class LoanEventHandler implements KafkaEventHandler<Loan> {

  @Override
  public void handle(KafkaEvent<Loan> event) {
    log.info("handle:: processing loan event: {}", event::getId);
    if (event.getType() == CREATED) {
      handleLoanCreationEvent(event);
    } else if (event.getType() == UPDATED) {
      handleLoanUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getType);
    }
    log.info("handle:: loan event processed: {}", event::getId);
  }

  private void handleLoanCreationEvent(KafkaEvent<Loan> event) {
    log.info("handleLoanUpdateEvent:: handling loan creation event: {}", event::getId);
  }

  private void handleLoanUpdateEvent(KafkaEvent<Loan> event) {
    log.info("handleLoanUpdateEvent:: handling loan update event: {}", event::getId);
  }

}
