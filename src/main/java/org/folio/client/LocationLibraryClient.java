package org.folio.client;

import org.folio.domain.dto.Libraries;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "location-units/libraries")
public interface LocationLibraryClient extends GetByQueryClient<Libraries> {

  @Override
  @GetExchange
  Libraries getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}
