package org.folio.client;

import org.folio.domain.dto.Campuses;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "location-units/campuses")
public interface LocationCampusClient extends GetByQueryClient<Campuses> {

  @Override
  @GetExchange
  Campuses getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}
