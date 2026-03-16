package org.folio.client;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "request-storage/requests")
public interface RequestStorageClient extends GetByQueryClient<Requests> {

  @Override
  @GetExchange
  Requests getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange("/{requestId}")
  Request getRequest(@PathVariable String requestId);

  @PutExchange("/{requestId}")
  Request updateRequest(@PathVariable String requestId, @RequestBody Request request);
}
