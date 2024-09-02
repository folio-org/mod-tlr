package org.folio.client.feign;

import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Request;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "circulation", url = "circulation", configuration = FeignClientConfiguration.class)
public interface CirculationClient {

  @PostMapping("/requests/instances")
  Request createInstanceRequest(Request request);

  @PostMapping("/requests")
  Request createRequest(Request request);

  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePointsWithStubItem(
    @RequestParam("patronGroupId") String patronGroupId, @RequestParam("instanceId") String instanceId,
    @RequestParam("operation") String operation, @RequestParam("useStubItem") boolean useStubItem);

  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePointsWithStubItem(
    @RequestParam("operation") String operation, @RequestParam("requestId") String requestId,
    @RequestParam("useStubItem") boolean useStubItem);


  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedRoutingServicePoints(
    @RequestParam("patronGroupId") String patronGroupId, @RequestParam("instanceId") String instanceId,
    @RequestParam("operation") String operation,
    @RequestParam("ecsRequestRouting") boolean ecsRequestRouting);

  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedRoutingServicePoints(
    @RequestParam("operation") String operation, @RequestParam("requestId") String requestId,
    @RequestParam("ecsRequestRouting") boolean ecsRequestRouting);
}
