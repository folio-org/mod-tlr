package org.folio.client;

import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.ServicePoints;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "service-points")
public interface ServicePointClient extends GetByQueryClient<ServicePoints> {

  @Override
  @GetExchange
  ServicePoints getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @PostExchange
  ServicePoint postServicePoint(@RequestBody ServicePoint servicePoint);

  @GetExchange("/{id}")
  ServicePoint getServicePoint(@PathVariable String id);
}
