package org.folio.client;

import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Instances;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "instance-storage/instances")
public interface InstanceClient extends GetByQueryClient<Instances> {

  @Override
  @GetExchange
  Instances getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange("/{id}")
  Instance get(@PathVariable String id);

}
