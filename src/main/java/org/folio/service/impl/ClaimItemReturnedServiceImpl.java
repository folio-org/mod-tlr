package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.ClaimItemReturnedService;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ClaimItemReturnedServiceImpl implements ClaimItemReturnedService {

  private final CirculationErrorForwardingClient circulationClient;
  private final CirculationMapper circulationMapper;
  private final LoanService loanService;
  private final RequestService requestService;
  private final EcsTlrRepository ecsTlrRepository;
  private final SystemUserScopedExecutionService systemUserService;

  @Override
  public void claimItemReturned(ClaimItemReturnedRequest claimReturnedRequest) {
    log.info("claimItemReturned:: processing claim item returned request: {}", claimReturnedRequest);
    validateRequest(claimReturnedRequest);
    Loan localLoan = findLoan(claimReturnedRequest);
    claimItemReturnedInCirculation(localLoan, claimReturnedRequest);
    claimItemReturnedInLendingTenant(localLoan, claimReturnedRequest);
    log.info("claimItemReturned:: item successfully claimed returned");
  }

  private static void validateRequest(ClaimItemReturnedRequest request) {
    boolean hasLoanId = request.getLoanId() != null;
    boolean hasItemId = request.getItemId() != null;
    boolean hasUserId = request.getUserId() != null;

    if ((hasLoanId && !hasItemId && !hasUserId) || (!hasLoanId && hasItemId && hasUserId)) {
      log.info("validateRequest:: claim item returned request is valid");
      return;
    }

    String errorMessage = "Invalid claim item returned request: must have either loanId or " +
      "(itemId and userId): " + request;
    log.error("validateRequest:: {}", errorMessage);
    throw new IllegalArgumentException(errorMessage);
  }

  private Loan findLoan(ClaimItemReturnedRequest request) {
    return Optional.ofNullable(request.getLoanId())
      .map(UUID::toString)
      .map(loanService::fetchLoan)
      .or(() -> loanService.findOpenLoan(request.getUserId().toString(), request.getItemId().toString()))
      .orElseThrow();
  }

  private ResponseEntity<Void> claimItemReturnedInCirculation(Loan loan,
    ClaimItemReturnedRequest claimItemReturnedRequest) {

    log.info("claimItemReturnedInCirculation:: claiming item returned for loan {}", loan::getId);
    return circulationClient.claimItemReturned(loan.getId(),
      circulationMapper.toCirculationClaimItemReturnedRequest(claimItemReturnedRequest));
  }

  private void claimItemReturnedInLendingTenant(Loan localLoan, ClaimItemReturnedRequest claimReturnedRequest) {
    log.info("claimItemReturnedInLendingTenant:: attempting to claim item returned in lending tenant");
    requestService.findEcsRequestForLoan(localLoan).ifPresentOrElse(
      ecsRequest -> claimItemReturnedInLendingTenant(localLoan, ecsRequest, claimReturnedRequest),
      () -> log.info("claimItemReturned:: no ECS request found for loan {}", localLoan::getId));
  }

  private void claimItemReturnedInLendingTenant(Loan loan, Request ecsRequest,
    ClaimItemReturnedRequest claimReturnedRequest) {

    findEcsTlr(ecsRequest)
      .map(EcsTlrEntity::getSecondaryRequestTenantId)
      .ifPresentOrElse(
        lendingTenantId -> claimItemReturned(loan, lendingTenantId, claimReturnedRequest),
        () -> log.info("claimItemReturnedInLendingTenant:: no ECS TLR found for request {}", ecsRequest::getId));
  }

  private Optional<EcsTlrEntity> findEcsTlr(Request request) {
    String requestId = request.getId();
    log.info("findEcsTlr:: looking for ECS TLR for request {}", requestId);

    // all requests (primary, intermediate, secondary) within the same ECS TLR have the same ID,
    // so we can search by either one
    return ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(requestId));
  }

  private void claimItemReturned(Loan loan, String tenantId, ClaimItemReturnedRequest claimReturnedRequest) {
    log.info("claimItemReturned:: claiming item returned in tenant {}", tenantId);

    systemUserService.executeSystemUserScoped(tenantId, () -> {
      Loan openLoan = loanService.findOpenLoan(loan.getUserId(), loan.getItemId())
        .orElseThrow();
      return claimItemReturnedInCirculation(openLoan, claimReturnedRequest);
    });
  }

}
