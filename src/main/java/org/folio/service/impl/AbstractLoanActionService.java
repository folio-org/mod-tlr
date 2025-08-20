package org.folio.service.impl;

import static org.folio.domain.type.ErrorCode.INVALID_LOAN_ACTION_REQUEST;
import static org.folio.domain.type.ErrorCode.LOAN_NOT_FOUND;
import static org.folio.exception.ExceptionFactory.validationError;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Parameter;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.exception.ExceptionFactory;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.LoanService;
import org.folio.service.RequestService;
import org.folio.spring.service.SystemUserScopedExecutionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractLoanActionService<T> {

  private static final String INVALID_REQUEST_ERROR_MESSAGE =
    "Invalid request: must have either loanId or (itemId and userId)";

  protected final CirculationErrorForwardingClient circulationClient;
  protected final CirculationMapper circulationMapper;
  protected final LoanService loanService;
  protected final RequestService requestService;
  protected final EcsTlrRepository ecsTlrRepository;
  protected final SystemUserScopedExecutionService systemUserService;

  protected void perform(T actionRequest) {
    log.info("perform:: processing loan action request: {}", toString(actionRequest));
    validateRequest(actionRequest);
    Loan localLoan = findLoan(actionRequest);
    performActionInCirculation(localLoan, actionRequest);
    performActionInLendingTenant(localLoan, actionRequest);
    log.info("perform:: loan action request processed successfully");
  }

  private void validateRequest(T actionRequest) {
    boolean hasLoanId = getLoanId(actionRequest) != null;
    boolean hasItemId = getItemId(actionRequest) != null;
    boolean hasUserId = getUserId(actionRequest) != null;

    if ((hasLoanId && !hasItemId && !hasUserId) || (!hasLoanId && hasItemId && hasUserId)) {
      log.info("validateRequest:: request is valid");
      return;
    }

    log.error("validateRequest:: {}: {}", INVALID_REQUEST_ERROR_MESSAGE, toString(actionRequest));
    throw ExceptionFactory.badRequest(INVALID_REQUEST_ERROR_MESSAGE, INVALID_LOAN_ACTION_REQUEST, List.of(
      new Parameter().key("loanId").value(getLoanId(actionRequest)),
      new Parameter().key("userId").value(getUserId(actionRequest)),
      new Parameter().key("itemId").value(getItemId(actionRequest))
    ));
  }

  private Loan findLoan(T actionRequest) {
    return Optional.ofNullable(getLoanId(actionRequest))
      .map(this::fetchLoan)
      .orElseGet(() -> findOpenLoan(getUserId(actionRequest).toString(), getItemId(actionRequest).toString()));
  }

  private Optional<EcsTlrEntity> findEcsTlr(Request request) {
    String requestId = request.getId();
    log.info("findEcsTlr:: looking for ECS TLR for request {}", requestId);
    return ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(requestId));
  }

  private void performActionInLendingTenant(Loan localLoan, T actionRequest) {
    log.info("performActionInLendingTenant:: performing loan action in lending tenant");
    requestService.findEcsRequestForLoan(localLoan).ifPresentOrElse(
      ecsRequest -> performActionInLendingTenant(localLoan, ecsRequest, actionRequest),
      () -> log.info("performActionInLendingTenant:: no ECS request found for loan {}", localLoan::getId));
  }

  private void performActionInLendingTenant(Loan localLoan, Request ecsRequest, T actionRequest) {
    findEcsTlr(ecsRequest)
      .map(EcsTlrEntity::getSecondaryRequestTenantId)
      .ifPresentOrElse(
        lendingTenantId -> performAction(localLoan, lendingTenantId, actionRequest),
        () -> log.info("performActionInLendingTenant:: no ECS TLR found for request {}", ecsRequest::getId));
  }

  private void performAction(Loan localLoan, String tenantId, T actionRequest) {
    log.info("performAction:: tenant={}", tenantId);
    systemUserService.executeSystemUserScoped(tenantId, () -> {
      Loan loanInLendingTenant = findOpenLoan(localLoan.getUserId(), localLoan.getItemId());
      performActionInCirculation(loanInLendingTenant, actionRequest);
      return null;
    });
  }

  private Loan fetchLoan(String loanId) {
    return loanService.fetchLoan(loanId)
      .orElseThrow(() -> validationError("Loan not found", LOAN_NOT_FOUND, List.of(
        new Parameter().key("id").value(loanId))));
  }

  private Loan findOpenLoan(String userId, String itemId) {
    return loanService.findOpenLoan(userId, itemId)
      .orElseThrow(() -> validationError("Open loan not found", LOAN_NOT_FOUND, List.of(
        new Parameter().key("userId").value(userId),
        new Parameter().key("itemId").value(itemId))));
  }

  private String toString(T actionRequest) {
    return String.format("%s(loanId=%s, userId=%s, itemId=%s)",
      actionRequest.getClass().getSimpleName(),
      getLoanId(actionRequest),
      getUserId(actionRequest),
      getItemId(actionRequest));
  }

  protected static String toString(UUID uuid) {
    return uuid == null ? null : uuid.toString();
  }

  protected abstract void performActionInCirculation(Loan loan, T actionRequest);
  protected abstract String getLoanId(T actionRequest);
  protected abstract String getUserId(T actionRequest);
  protected abstract String getItemId(T actionRequest);
}
