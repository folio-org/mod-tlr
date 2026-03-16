package org.folio.client;

import org.folio.domain.dto.AddressTypes;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "addresstypes")
public interface AddressTypeClient extends GetByQueryClient<AddressTypes> {

  @Override
  @GetExchange
  AddressTypes getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}
