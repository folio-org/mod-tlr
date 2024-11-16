package org.folio.client.feign;

import org.folio.spring.config.FeignClientConfiguration;
import org.folio.support.CqlQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name="get-by-query", configuration = FeignClientConfiguration.class)
public interface GetByQueryClient<T> {
  int DEFAULT_LIMIT = 500;

  @GetMapping
  T getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  default T getByQuery(CqlQuery query) {
    return getByQuery(query, DEFAULT_LIMIT);
  }
}
