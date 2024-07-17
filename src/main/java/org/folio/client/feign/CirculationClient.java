package org.folio.client.feign;

import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Request;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "circulation", url = "circulation", configuration = FeignClientConfiguration.class)
public interface CirculationClient {

  @PostMapping("/requests/instances")
  Request createInstanceRequest(Request request);

  @GetMapping("/requests/{requestId}")
  Request getRequest(@PathVariable String requestId);

  @PostMapping("/requests")
  Request createRequest(Request request);

  @PutMapping("/requests/{requestId}")
  Request updateRequest(@RequestBody Request request, @PathVariable String requestId);

  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePointsWithStubItem(
    @RequestParam("requesterId") String requesterId, @RequestParam("instanceId") String instanceId,
    @RequestParam("operation") String operation, @RequestParam("useStubItem") boolean useStubItem);


  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedRoutingServicePoints(
    @RequestParam("requesterId") String requesterId, @RequestParam("instanceId") String instanceId,
    @RequestParam("operation") String operation,
    @RequestParam("ecsRequestRouting") boolean ecsRequestRouting);
}
