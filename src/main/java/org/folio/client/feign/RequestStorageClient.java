package org.folio.client.feign;

import org.folio.domain.dto.Request;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "request-storage", url = "request-storage/requests", configuration = FeignClientConfiguration.class)
public interface RequestStorageClient {

  @GetMapping("/{requestId}")
  Request getRequest(@PathVariable String requestId);

  @PutMapping("/{requestId}")
  Request updateRequest(@PathVariable String requestId, @RequestBody Request request);

}
