package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.LoanActionService;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
@Service
public class LoanActionServiceImpl implements LoanActionService {

  protected final CirculationErrorForwardingClient circulationClient;
  protected final CirculationMapper circulationMapper;
  protected final LoanService loanService;
  protected final RequestService requestService;
  protected final EcsTlrRepository ecsTlrRepository;
  protected final SystemUserScopedExecutionService systemUserService;

  @Override
  public void declareItemLost(DeclareItemLostRequest request) {
    log.info("declareItemLost:: declaring item lost: {}", request);
    process(LoanActionRequest.from(request));
  }

  @Override
  public void claimItemReturned(ClaimItemReturnedRequest request) {
    log.info("claimItemReturned:: claiming item returned: {}", request);
    process(LoanActionRequest.from(request));
  }

  @Override
  public void declareClaimedReturnedItemMissing(DeclareClaimedReturnedItemAsMissingRequest request) {
    log.info("declareClaimedReturnedItemMissing:: declaring item missing: {}", request);
    process(LoanActionRequest.from(request));
  }

  private <T> void process(LoanActionRequest<T> actionRequest) {
    log.info("process:: processing loan action request: {}", actionRequest);
    validateRequest(actionRequest);
    Loan localLoan = findLoan(actionRequest);
    performActionInCirculation(localLoan, actionRequest);
    performActionInLendingTenant(localLoan, actionRequest);
    log.info("process:: loan action request processed successfully");
  }

  private <T> void validateRequest(LoanActionRequest<T> actionRequest) {
    boolean hasLoanId = actionRequest.loanId() != null;
    boolean hasItemId = actionRequest.itemId() != null;
    boolean hasUserId = actionRequest.userId() != null;

    if ((hasLoanId && !hasItemId && !hasUserId) || (!hasLoanId && hasItemId && hasUserId)) {
      log.info("validateRequest:: request is valid");
      return;
    }

    String errorMessage = String.format("Invalid request: must have either loanId or " +
      "(itemId and userId): %s", actionRequest);
    log.error("validateRequest:: {}", errorMessage);
    throw new IllegalArgumentException(errorMessage);
  }

  private <T> Loan findLoan(LoanActionRequest<T> actionRequest) {
    return Optional.ofNullable(actionRequest.loanId())
      .map(UUID::toString)
      .map(loanService::fetchLoan)
      .or(() -> loanService.findOpenLoan(actionRequest.userId().toString(), actionRequest.itemId().toString()))
      .orElseThrow();
  }

  private Optional<EcsTlrEntity> findEcsTlr(Request request) {
    String requestId = request.getId();
    log.info("findEcsTlr:: looking for ECS TLR for request {}", requestId);
    return ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(requestId));
  }

  private <T> void performActionInLendingTenant(Loan localLoan, LoanActionRequest<T> actionRequest) {
    log.info("performActionInLendingTenant:: performing loan action in lending tenant");
    requestService.findEcsRequestForLoan(localLoan).ifPresentOrElse(
      ecsRequest -> performActionInLendingTenant(localLoan, ecsRequest, actionRequest),
      () -> log.info("performActionInLendingTenant:: no ECS request found for loan {}", localLoan::getId));
  }

  private <T> void performActionInLendingTenant(Loan loan, Request ecsRequest,
    LoanActionRequest<T> actionRequest) {

    findEcsTlr(ecsRequest)
      .map(EcsTlrEntity::getSecondaryRequestTenantId)
      .ifPresentOrElse(
        lendingTenantId -> performAction(loan, lendingTenantId, actionRequest),
        () -> log.info("performActionInLendingTenant:: no ECS TLR found for request {}", ecsRequest::getId));
  }

  private <T> void performAction(Loan loan, String tenantId, LoanActionRequest<T> actionRequest) {
    log.info("performAction:: tenant={}", tenantId);
    systemUserService.executeSystemUserScoped(tenantId, () -> {
      Loan openLoan = loanService.findOpenLoan(loan.getUserId(), loan.getItemId()).orElseThrow();
      performActionInCirculation(openLoan, actionRequest);
      return null;
    });
  }

  private <T> void performActionInCirculation(Loan loan, LoanActionRequest<T> actionRequest) {
    switch (actionRequest.originalRequest()) {
      case DeclareItemLostRequest request -> circulationClient.declareItemLost(loan.getId(),
        circulationMapper.toCirculationDeclareItemLostRequest(request));
      case ClaimItemReturnedRequest request -> circulationClient.claimItemReturned(loan.getId(),
        circulationMapper.toCirculationClaimItemReturnedRequest(request));
      case DeclareClaimedReturnedItemAsMissingRequest request ->
        circulationClient.declareClaimedReturnedItemAsMissing(loan.getId(),
        circulationMapper.toCirculationDeclareClaimedReturnedItemsAsMissingRequest(request));
      default -> throw new UnsupportedOperationException("Unsupported action request type: " +
        actionRequest.originalRequest().getClass().getSimpleName());
    }
  }

  private record LoanActionRequest<T>(T originalRequest, UUID loanId, UUID itemId, UUID userId) {

    public static LoanActionRequest<DeclareItemLostRequest> from(DeclareItemLostRequest request) {
      return new LoanActionRequest<>(request, request.getLoanId(), request.getItemId(), request.getUserId());
    }

    public static LoanActionRequest<ClaimItemReturnedRequest> from(ClaimItemReturnedRequest request) {
      return new LoanActionRequest<>(request, request.getLoanId(), request.getItemId(), request.getUserId());
    }

    public static LoanActionRequest<DeclareClaimedReturnedItemAsMissingRequest> from(
      DeclareClaimedReturnedItemAsMissingRequest request) {

      return new LoanActionRequest<>(request, request.getLoanId(), request.getItemId(), request.getUserId());
    }
  }

}
