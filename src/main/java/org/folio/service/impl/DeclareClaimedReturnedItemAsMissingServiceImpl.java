package org.folio.service.impl;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DeclareClaimedReturnedItemAsMissingService;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
import org.folio.service.wrapper.LoanActionRequest;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class DeclareClaimedReturnedItemAsMissingServiceImpl
  extends AbstractLoanActionService<DeclareClaimedReturnedItemAsMissingRequest>
  implements DeclareClaimedReturnedItemAsMissingService {

  public DeclareClaimedReturnedItemAsMissingServiceImpl(
    CirculationErrorForwardingClient circulationClient,
    CirculationMapper circulationMapper,
    LoanService loanService,
    RequestService requestService,
    EcsTlrRepository ecsTlrRepository,
    SystemUserScopedExecutionService systemUserService) {

    super(circulationClient, circulationMapper, loanService, requestService, ecsTlrRepository,
      systemUserService);
  }

  public void declareMissing(DeclareClaimedReturnedItemAsMissingRequest request) {
    process(LoanActionRequest.from(request));
  }

  @Override
  protected void performActionInCirculation(Loan loan,
    LoanActionRequest<DeclareClaimedReturnedItemAsMissingRequest> actionRequest) {

    log.info("declareItemLostInCirculation:: declaring item lost for loan {}", loan::getId);
    circulationClient.declareClaimedReturnedItemAsMissing(loan.getId(),
      circulationMapper.toCirculationDeclareClaimedReturnedItemsAsMissingRequest(
        actionRequest.originalRequest()));
  }

}
