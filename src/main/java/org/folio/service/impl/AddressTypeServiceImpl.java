package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.AddressTypeClient;
import org.folio.domain.dto.AddressType;
import org.folio.domain.dto.AddressTypes;
import org.folio.service.AddressTypeService;
import org.folio.support.BulkFetcher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class AddressTypeServiceImpl implements AddressTypeService {

  private final AddressTypeClient addressTypeClient;

  @Override
  public Collection<AddressType> findAddressTypes(Collection<String> ids) {
    log.info("findAddressTypes:: fetching address types by {} IDs", ids.size());
    log.debug("findAddressTypes:: ids={}", ids);
    return BulkFetcher.fetch(addressTypeClient, ids, AddressTypes::getAddressTypes);
  }
}
