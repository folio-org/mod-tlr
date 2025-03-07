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
  AllowedServicePointsResponse allowedServicePointsByInstance(
    @RequestParam("patronGroupId") String patronGroupId,
    @RequestParam("operation") String operation,
    @RequestParam("instanceId") String instanceId);

  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePointsByItem(
    @RequestParam("patronGroupId") String patronGroupId,
    @RequestParam("operation") String operation,
    @RequestParam("itemId") String itemId);

  @GetMapping("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePoints(
    @RequestParam("operation") String operation,
    @RequestParam("requestId") String requestId);
}
