package org.folio.client.feign;

import org.folio.domain.dto.Requests;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "circulation-request", url = "circulation/requests",
  configuration = FeignClientConfiguration.class)
public interface RequestCirculationClient {

  @GetMapping("/queue/instance/{instanceId}")
  Requests getRequestsQueueByInstanceId(@PathVariable String instanceId);
}
