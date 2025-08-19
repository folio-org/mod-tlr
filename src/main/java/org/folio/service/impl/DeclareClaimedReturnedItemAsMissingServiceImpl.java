package org.folio.service.impl;

import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DeclareClaimedReturnedItemAsMissingService;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
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

  @Override
  public void declareMissing(DeclareClaimedReturnedItemAsMissingRequest request) {
    log.info("declareMissing:: declaring claimed returned item as missing");
    execute(request);
  }

  @Override
  protected void performActionInCirculation(Loan loan,
    DeclareClaimedReturnedItemAsMissingRequest request) {

    log.info("declareItemLostInCirculation:: declaring item lost for loan {}", loan::getId);
    circulationClient.declareClaimedReturnedItemAsMissing(loan.getId(),
      circulationMapper.toCirculationDeclareClaimedReturnedItemsAsMissingRequest(request));
  }

  @Override
  protected UUID getLoanId(DeclareClaimedReturnedItemAsMissingRequest actionRequest) {
    return actionRequest.getLoanId();
  }

  @Override
  protected UUID getUserId(DeclareClaimedReturnedItemAsMissingRequest actionRequest) {
    return actionRequest.getUserId();
  }

  @Override
  protected UUID getItemId(DeclareClaimedReturnedItemAsMissingRequest actionRequest) {
    return actionRequest.getItemId();
  }

}
