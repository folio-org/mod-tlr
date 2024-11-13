package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.AddressType;

public interface AddressTypeService {
  Collection<AddressType> findAddressTypes(Collection<String> ids);
}
