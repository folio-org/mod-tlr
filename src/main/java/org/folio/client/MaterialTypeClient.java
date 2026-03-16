package org.folio.client;

import org.folio.domain.dto.MaterialTypes;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "material-types")
public interface MaterialTypeClient extends GetByQueryClient<MaterialTypes> {

  @Override
  @GetExchange
  MaterialTypes getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}
