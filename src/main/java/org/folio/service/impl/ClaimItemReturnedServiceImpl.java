package org.folio.service.impl;

import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.ClaimItemReturnedService;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class ClaimItemReturnedServiceImpl extends AbstractLoanActionService<ClaimItemReturnedRequest>
implements ClaimItemReturnedService {

  public ClaimItemReturnedServiceImpl(
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
  public void claimItemReturned(ClaimItemReturnedRequest request) {
    log.info("claimItemReturned:: claiming item returned");
    execute(request);
  }

  @Override
  protected void performActionInCirculation(Loan loan, ClaimItemReturnedRequest request) {
    log.info("performActionInCirculation:: claiming item returned for loan {}", loan::getId);
    circulationClient.claimItemReturned(loan.getId(),
      circulationMapper.toCirculationClaimItemReturnedRequest(request));
  }

  @Override
  protected UUID getLoanId(ClaimItemReturnedRequest actionRequest) {
    return actionRequest.getLoanId();
  }

  @Override
  protected UUID getUserId(ClaimItemReturnedRequest actionRequest) {
    return actionRequest.getUserId();
  }

  @Override
  protected UUID getItemId(ClaimItemReturnedRequest actionRequest) {
    return actionRequest.getItemId();
  }
}
