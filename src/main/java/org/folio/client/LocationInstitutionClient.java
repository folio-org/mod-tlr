package org.folio.client;

import org.folio.domain.dto.Institutions;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "location-units/institutions")
public interface LocationInstitutionClient extends GetByQueryClient<Institutions> {

  @Override
  @GetExchange
  Institutions getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}
