package org.folio.service.impl;

import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DeclareItemLostService;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class DeclareItemLostServiceImpl extends AbstractLoanActionService<DeclareItemLostRequest>
implements DeclareItemLostService {

  public DeclareItemLostServiceImpl(
    CirculationErrorForwardingClient circulationClient,
    CirculationMapper circulationMapper,
    LoanService loanService,
    RequestService requestService,
    EcsTlrRepository ecsTlrRepository,
    SystemUserScopedExecutionService systemUserService) {

    super(circulationClient, circulationMapper, loanService, requestService, ecsTlrRepository,
      systemUserService);
  }

  @Override
  public void declareItemLost(DeclareItemLostRequest request) {
    log.info("declareItemLost:: declaring item lost");
    perform(request);
  }

  @Override
  protected void performActionInCirculation(Loan loan, DeclareItemLostRequest request) {
    log.info("performActionInCirculation:: declaring item lost for loan {}", loan::getId);
    circulationClient.declareItemLost(loan.getId(),
      circulationMapper.toCirculationDeclareItemLostRequest(request));
  }

  @Override
  protected UUID getLoanId(DeclareItemLostRequest actionRequest) {
    return actionRequest.getLoanId();
  }

  @Override
  protected UUID getUserId(DeclareItemLostRequest actionRequest) {
    return actionRequest.getUserId();
  }

  @Override
  protected UUID getItemId(DeclareItemLostRequest actionRequest) {
    return actionRequest.getItemId();
  }
}
