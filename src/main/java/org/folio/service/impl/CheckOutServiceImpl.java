package org.folio.service.impl;

import org.folio.client.feign.CheckOutClient;
import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.folio.domain.dto.ConsortiumItem;
import org.folio.service.CheckOutService;
import org.folio.service.SearchService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class CheckOutServiceImpl implements CheckOutService {

  private final SearchService searchService;
  private final CheckOutClient checkOutClient;

  @Override
  public CheckOutResponse checkOut(CheckOutRequest checkOutRequest) {
    log.info("checkOutByBarcode:: checking out item {} to user {}", checkOutRequest.getItemBarcode(),
      checkOutRequest.getUserBarcode());
    String itemTenant = findItemTenant(checkOutRequest.getItemBarcode());

    CheckOutResponse checkOutResponse = checkOutClient.checkOut(checkOutRequest);
    log.info("checkOutByBarcode:: item checked out");

    return checkOutResponse;
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
