package org.folio.service.impl;

import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.util.Strings.EMPTY;
import static org.folio.spring.integration.XOkapiHeaders.PERMISSIONS;
import static org.folio.spring.integration.XOkapiHeaders.REQUEST_ID;

import java.util.Map;
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
import org.folio.spring.FolioExecutionContext;
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
  private final FolioExecutionContext folioExecutionContext;


  @Override
  public CheckOutResponse checkOut(CheckOutRequest checkOutRequest) {
    log.info("checkOut:: checking out item {} to user {}", checkOutRequest.getItemBarcode(),
      checkOutRequest.getUserBarcode());
    var itemTenant = findItemTenant(checkOutRequest.getItemBarcode());
    log.info("checkOut:: itemTenant: {} ", itemTenant);

    Map<String, String> permissions = getHeadersFromContext();
    var loanPolicy = executionService.executeSystemUserScoped(itemTenant,
      () -> retrieveLoanPolicy(checkOutRequest, permissions));
    loanPolicyCloningService.clone(loanPolicy);

    var checkOutResponse = checkOutClient.checkOut(checkOutRequest.forceLoanPolicyId(
      UUID.fromString(loanPolicy.getId())));
    log.info("checkOut:: item checked out");

    return checkOutResponse;
  }

  private LoanPolicy retrieveLoanPolicy(CheckOutRequest checkOutRequest, Map<String, String> permissions) {
    log.info("retrieveLoanPolicy:: checkOutRequest: {}", checkOutRequest);
    var checkOutDryRunResponse = checkOutClient.checkOutDryRun(
      checkOutDryRunRequestMapper.mapCheckOutRequestToCheckOutDryRunRequest(checkOutRequest),
      permissions);
    log.info("retrieveLoanPolicy:: checkOutDryRunResponse: {}", checkOutDryRunResponse);
    var loanPolicy = loanPolicyClient.get(checkOutDryRunResponse.getLoanPolicyId());
    log.debug("retrieveLoanPolicy:: loanPolicy: {}", loanPolicy);
    return loanPolicy;
  }

  protected Map<String, String> getHeadersFromContext() {
    return folioExecutionContext.getOkapiHeaders()
      .entrySet()
      .stream()
      .filter(entry -> PERMISSIONS.equalsIgnoreCase(entry.getKey()) ||
        REQUEST_ID.equalsIgnoreCase(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().stream().findFirst().orElse(EMPTY)));
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
