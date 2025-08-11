package org.folio.service.impl;

import static org.folio.spring.integration.XOkapiHeaders.PERMISSIONS;
import static org.folio.spring.integration.XOkapiHeaders.REQUEST_ID;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
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
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.utils.LoggingUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import liquibase.util.LogUtil;
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
    log.info("checkOut:: checking out item {} to user {}", checkOutRequest.getItemBarcode(),
      checkOutRequest.getUserBarcode());
    var itemTenant = findItemTenant(checkOutRequest.getItemBarcode());
    log.info("checkOut:: itemTenant: {} ", itemTenant);

    var loanPolicy = executionService.executeSystemUserScoped(itemTenant,
      () -> retrieveLoanPolicy(checkOutRequest, getHeadersFromContext()));
    loanPolicyCloningService.clone(loanPolicy);

    var checkOutResponse = checkOutClient.checkOut(checkOutRequest.forceLoanPolicyId(
      UUID.fromString(loanPolicy.getId())));
    log.info("checkOut:: item checked out");

    return checkOutResponse;
  }

  private LoanPolicy retrieveLoanPolicy(CheckOutRequest checkOutRequest, Map<String, String> headers) {
    log.info("retrieveLoanPolicy:: checkOutRequest: {}", checkOutRequest);
    var checkOutDryRunResponse = checkOutClient.checkOutDryRun(
      checkOutDryRunRequestMapper.mapCheckOutRequestToCheckOutDryRunRequest(checkOutRequest),
      headers);
    log.info("retrieveLoanPolicy:: checkOutDryRunResponse: {}", checkOutDryRunResponse);
    var loanPolicy = loanPolicyClient.get(checkOutDryRunResponse.getLoanPolicyId());
    log.debug("retrieveLoanPolicy:: loanPolicy: {}", loanPolicy);
    return loanPolicy;
  }

  protected Map<String, String> getHeadersFromContext() {
    log.info("getHeadersFromContext:: extracting headers from servlet request context");
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      log.warn("getHeadersFromContext:: no request context available");
      return Map.of();
    }
    var request = attrs.getRequest();
    log.info("getHeadersFromContext:: headerNames: {}", Collections.list(request.getHeaderNames()));


    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      String headerValue = request.getHeader(headerName);
      if (headerValue != null) {
        log.info("getHeadersFromContext:: found header: {} with value: {}", headerName, headerValue);
      }
    }


    Map<String, String> headers = new HashMap<>();
    var permissionsHeader = request.getHeader(PERMISSIONS);
    if (permissionsHeader != null) {
      headers.put(PERMISSIONS, permissionsHeader);
      log.info("getHeadersFromContext:: found {} header", PERMISSIONS);
    }
    var requestIdHeader = request.getHeader(REQUEST_ID);
    if (requestIdHeader != null) {
      headers.put(REQUEST_ID, requestIdHeader);
      log.info("getHeadersFromContext:: found {} header", REQUEST_ID);
    }
    log.info("getHeadersFromContext:: extracted headers: {}", headers.keySet());
    return headers;
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
