package org.folio.client.feign;

import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Request;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "circulation", url = "circulation", configuration = FeignClientConfiguration.class)
public interface CirculationClient {

  @PostMapping("/requests/instances")
  Request createInstanceRequest(Request request);

  @PostMapping("/requests")
  Request createRequest(Request request);

  @PostMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePoints(
    @RequestParam("requesterId") String requesterId, @RequestParam("instanceId") String instanceId,
    @RequestParam("useStubItem") boolean useStubItem);
}
