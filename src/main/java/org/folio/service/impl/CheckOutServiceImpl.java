package org.folio.service.impl;

import java.util.UUID;

import org.folio.client.feign.CheckOutClient;
import org.folio.client.feign.LoanPolicyClient;
import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.folio.domain.dto.ConsortiumItem;
import org.folio.domain.dto.LoanPolicy;
import org.folio.domain.mapper.CheckOutDryRunRequestMapper;
import org.folio.service.CheckOutService;
import org.folio.service.CloningService;
import org.folio.service.SearchService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class CheckOutServiceImpl implements CheckOutService {

  private final SearchService searchService;
  private final CloningService<LoanPolicy> loanPolicyCloningService;
  private final CheckOutClient checkOutClient;
  private final LoanPolicyClient loanPolicyClient;
  private final CheckOutDryRunRequestMapper checkOutDryRunRequestMapper;
  private final SystemUserScopedExecutionService executionService;


  @Override
  public CheckOutResponse checkOut(CheckOutRequest checkOutRequest) {
    String targetTenant = checkOutRequest.getTargetTenantId();
    log.info("checkOut:: checking out item {} to user {} in tenant {}",
      checkOutRequest.getItemBarcode(), checkOutRequest.getUserBarcode(), targetTenant);

    var itemTenant = findItemTenant(checkOutRequest.getItemBarcode());
    var loanPolicy = executionService.executeSystemUserScoped(itemTenant,
      () -> retrieveLoanPolicy(checkOutRequest));

    return executionService.executeSystemUserScoped(targetTenant, () -> {
      loanPolicyCloningService.clone(loanPolicy);
      CheckOutRequest circulationCheckOutRequest = checkOutRequest
        .forceLoanPolicyId(UUID.fromString(loanPolicy.getId()))
        .targetTenantId(null);
      CheckOutResponse checkOutResponse = checkOutClient.checkOut(circulationCheckOutRequest);
      log.info("checkOut:: item checked out");
      return checkOutResponse;
    });
  }

  private LoanPolicy retrieveLoanPolicy(CheckOutRequest checkOutRequest) {
    var checkOutDryRunResponse = checkOutClient.checkOutDryRun(checkOutDryRunRequestMapper
      .mapCheckOutRequestToCheckOutDryRunRequest(checkOutRequest));
    log.info("retrieveLoanPolicy:: checkOutDryRunResponse: {}", checkOutDryRunResponse);
    var loanPolicy = loanPolicyClient.get(checkOutDryRunResponse.getLoanPolicyId());
    log.debug("retrieveLoanPolicy:: loanPolicy: {}", loanPolicy);

    return loanPolicy;
  }

  private String findItemTenant(String itemBarcode) {
    if (itemBarcode == null) {
      throw new IllegalArgumentException("Item barcode cannot be null");
    }

    String itemTenant = searchService.searchItem(itemBarcode)
      .map(ConsortiumItem::getTenantId)
      .orElseThrow(() -> new IllegalStateException("Failed to find tenant for item with barcode " +
        itemBarcode));

    log.info("findItemTenant:: item found in tenant {}", itemTenant);
    return itemTenant;
  }

}
