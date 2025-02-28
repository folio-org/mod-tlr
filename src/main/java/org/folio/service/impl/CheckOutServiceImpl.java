package org.folio.service.impl;

import org.folio.client.feign.CirculationClient;
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
  private final CirculationClient circulationClient;

  @Override
  public CheckOutResponse checkOut(CheckOutRequest checkOutRequest) {
    log.info("checkOutByBarcode:: checking out item {} to user {}", checkOutRequest.getItemBarcode(),
      checkOutRequest.getUserBarcode());
    String itemTenant = findItemTenant(checkOutRequest.getItemBarcode());
    log.info("checkOut:: item tenant: {}", itemTenant);

    return circulationClient.checkOut(checkOutRequest);
  }

  private String findItemTenant(String itemBarcode) {
    if (itemBarcode == null) {
      throw new IllegalArgumentException("Item barcode cannot be null");
    }

    return searchService.searchItem(itemBarcode)
      .map(ConsortiumItem::getTenantId)
      .orElseThrow(() -> new IllegalStateException("Failed to find tenant for item with barcode " +
        itemBarcode));
  }

}
