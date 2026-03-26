package org.folio.client;

import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface GetByQueryClient<T> {

  int DEFAULT_LIMIT = 1000;

  @GetExchange
  T getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  default T getByQuery(CqlQuery query) {
    return getByQuery(query, DEFAULT_LIMIT);
  }

}
