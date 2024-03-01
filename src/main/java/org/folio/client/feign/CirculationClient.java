package org.folio.client.feign;

import org.folio.domain.dto.Request;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "circulation", url = "${folio.okapi-url}", configuration = FeignClientConfiguration.class)
public interface CirculationClient {

  @PostMapping("/requests/instances")
  Request createInstanceRequest(Request request);

  @PostMapping("/requests")
  Request createRequest(Request request);
}
