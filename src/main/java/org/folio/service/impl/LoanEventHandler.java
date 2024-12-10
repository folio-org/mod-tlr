package org.folio.service.impl;

import static org.folio.support.KafkaEvent.EventType.UPDATED;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.Loan;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DcbService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class LoanEventHandler implements KafkaEventHandler<Loan> {
  private static final String LOAN_ACTION_CHECKED_IN = "checkedin";

  private final DcbService dcbService;
  private final EcsTlrRepository ecsTlrRepository;
  private final SystemUserScopedExecutionService executionService;
  private final ServicePointService servicePointService;
  private final RequestService requestService;

  @Override
  public void handle(KafkaEvent<Loan> event) {
    log.info("handle:: processing loan event: {}", event::getId);
    if (event.getType() == UPDATED) {
      handleUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getType);
    }
    log.info("handle:: loan event processed: {}", event::getId);
  }

  private void handleUpdateEvent(KafkaEvent<Loan> event) {
    Loan loan = event.getData().getNewVersion();
    String loanAction = loan.getAction();
    log.info("handle:: loan action: {}", loanAction);
    if (LOAN_ACTION_CHECKED_IN.equals(loanAction)) {
      log.info("handleUpdateEvent:: processing check-in event: {}", event::getId);
      handleCheckInEvent(loan);
    } else {
      log.info("handleUpdateEvent:: unsupported loan action: {}", loanAction);
    }
  }

  private void handleCheckInEvent(Loan loan) {
    findEcsTlr(loan)
      .ifPresent(this::updateEcsTlrOnCheckIn);
  }

  private Optional<EcsTlrEntity> findEcsTlr(Loan loan) {
    log.info("findEcsTlr:: searching ECS TLR for loan {}", loan::getId);
    return ecsTlrRepository.findByItemIdAndRequesterId(UUID.fromString(loan.getItemId()),
        UUID.fromString(loan.getUserId()))
      .stream()
      .filter(this::isForLoan)
      .findFirst();
  }

  private boolean isForLoan(EcsTlrEntity ecsTlr) {
    return true;
  }

  private void updateEcsTlrOnCheckIn(EcsTlrEntity ecsTlr) {

  }

}
