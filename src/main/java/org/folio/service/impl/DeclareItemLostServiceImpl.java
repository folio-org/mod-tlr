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
    log.info("declareItemLost:: declaring item lost for loan {}", declareLostRequest::getLoanId);
    declareItemLostInCirculation(declareLostRequest);
    declareItemLostInLendingTenant(declareLostRequest);
    log.info("declareItemLost:: successfully declared item lost for loan {}", declareLostRequest::getLoanId);
  }

  private void declareItemLostInLendingTenant(DeclareItemLostRequest declareLostRequest) {
    log.info("declareItemLostInLendingTenant:: attempting to declare item lost in lending tenant");
    Loan localLoan = findLoan(declareLostRequest);
    requestService.findEcsRequestForLoan(localLoan).ifPresentOrElse(
      ecsRequest -> declareItemLostInLendingTenant(localLoan, ecsRequest, declareLostRequest),
      () -> log.info("declareItemLost:: no ECS request found for loan {}", localLoan::getId));
  }

  private Loan findLoan(DeclareItemLostRequest request) {
    return Optional.ofNullable(request.getLoanId())
      .map(UUID::toString)
      .map(loanService::fetchLoan)
      .orElseThrow();
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
      String loanId = loanService.findOpenLoan(loan.getUserId(), loan.getItemId())
        .map(Loan::getId)
        .orElseThrow();

      log.info("declareItemLost:: open loan found: {}", loanId);
      return declareItemLostInCirculation(loanId, declareLostRequest);
    });
  }

  private void declareItemLostInCirculation(DeclareItemLostRequest declareItemLostRequest) {
    declareItemLostInCirculation(declareItemLostRequest.getLoanId().toString(), declareItemLostRequest);
  }

  private ResponseEntity<Void> declareItemLostInCirculation(String loanId,
    DeclareItemLostRequest declareItemLostRequest) {

    log.info("declareItemLostInCirculation:: declaring item lost for loan {}", loanId);
    return circulationClient.declareItemLost(loanId,
      circulationMapper.toCirculationDeclareItemLostRequest(declareItemLostRequest));
  }

}
