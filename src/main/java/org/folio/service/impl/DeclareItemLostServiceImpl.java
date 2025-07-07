package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationErrorForwardingClient;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.CirculationMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.DeclareItemLostService;
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
public class DeclareItemLostServiceImpl implements DeclareItemLostService {

  private final CirculationErrorForwardingClient circulationClient;
  private final CirculationMapper circulationMapper;
  private final LoanService loanService;
  private final RequestService requestService;
  private final EcsTlrRepository ecsTlrRepository;
  private final SystemUserScopedExecutionService systemUserService;

  @Override
  public void declareItemLost(DeclareItemLostRequest declareLostRequest) {
    log.info("declareItemLost:: processing declare item lost request: {}", declareLostRequest);
    validateRequest(declareLostRequest);
    Loan localLoan = findLoan(declareLostRequest);
    declareItemLostInCirculation(localLoan, declareLostRequest);
    declareItemLostInLendingTenant(localLoan, declareLostRequest);
    log.info("declareItemLost:: successfully declared item lost for loan {}", declareLostRequest::getLoanId);
  }

  private static void validateRequest(DeclareItemLostRequest request) {
    boolean hasLoanId = request.getLoanId() != null;
    boolean hasItemId = request.getItemId() != null;
    boolean hasUserId = request.getUserId() != null;

    if ((hasLoanId && !hasItemId && !hasUserId) || (!hasLoanId && hasItemId && hasUserId)) {
      log.info("validateRequest:: declare item lost request us valid");
      return;
    }

    String errorMessage = "Invalid declare item lost request: must have either loanId or " +
      "(itemId and userId): " + request;
    log.error("validateRequest:: {}", errorMessage);
    throw new IllegalArgumentException(errorMessage);
  }

  private Loan findLoan(DeclareItemLostRequest request) {
    return Optional.ofNullable(request.getLoanId())
      .map(UUID::toString)
      .map(loanService::fetchLoan)
      .or(() -> loanService.findOpenLoan(request.getUserId().toString(), request.getItemId().toString()))
      .orElseThrow();
  }

  private ResponseEntity<Void> declareItemLostInCirculation(Loan loan,
    DeclareItemLostRequest declareItemLostRequest) {

    log.info("declareItemLostInCirculation:: declaring item lost for loan {}", loan::getId);
    return circulationClient.declareItemLost(loan.getId(),
      circulationMapper.toCirculationDeclareItemLostRequest(declareItemLostRequest));
  }

  private void declareItemLostInLendingTenant(Loan localLoan, DeclareItemLostRequest declareLostRequest) {
    log.info("declareItemLostInLendingTenant:: attempting to declare item lost in lending tenant");
    requestService.findEcsRequestForLoan(localLoan).ifPresentOrElse(
      ecsRequest -> declareItemLostInLendingTenant(localLoan, ecsRequest, declareLostRequest),
      () -> log.info("declareItemLost:: no ECS request found for loan {}", localLoan::getId));
  }

  private void declareItemLostInLendingTenant(Loan loan, Request ecsRequest,
    DeclareItemLostRequest declareLostRequest) {

    findEcsTlr(ecsRequest)
      .map(EcsTlrEntity::getSecondaryRequestTenantId)
      .ifPresentOrElse(
        lendingTenantId -> declareItemLost(loan, lendingTenantId, declareLostRequest),
        () -> log.info("declareItemLostInLendingTenant:: no ECS TLR found for request {}", ecsRequest::getId));
  }

  private Optional<EcsTlrEntity> findEcsTlr(Request request) {
    String requestId = request.getId();
    log.info("findEcsTlr:: looking for ECS TLR for request {}", requestId);

    // all requests (primary, intermediate, secondary) within the same ECS TLR have the same ID,
    // so we can search by either one
    return ecsTlrRepository.findByPrimaryRequestId(UUID.fromString(requestId));
  }

  private void declareItemLost(Loan loan, String tenantId, DeclareItemLostRequest declareLostRequest) {
    log.info("declareItemLost:: declaring item lost in tenant {}", tenantId);

    systemUserService.executeSystemUserScoped(tenantId, () -> {
      Loan openLoan = loanService.findOpenLoan(loan.getUserId(), loan.getItemId())
        .orElseThrow();
      return declareItemLostInCirculation(openLoan, declareLostRequest);
    });
  }

}
