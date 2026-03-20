package org.folio.client;

import org.folio.domain.dto.Location;
import org.folio.domain.dto.Locations;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "locations")
public interface LocationClient extends GetByQueryClient<Locations> {

  @Override
  @GetExchange
  Locations getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange("/{id}")
  Location findLocation(@PathVariable String id);

}
