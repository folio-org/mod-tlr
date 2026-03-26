package org.folio.client;

import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Request;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "circulation")
public interface CirculationClient {

  @PostExchange("/requests/instances")
  Request createInstanceRequest(@RequestBody Request request);

  @PostExchange("/requests")
  Request createRequest(@RequestBody Request request);

  @GetExchange("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePointsByInstance(
    @RequestParam("patronGroupId") String patronGroupId,
    @RequestParam("operation") String operation,
    @RequestParam("instanceId") String instanceId);

  @GetExchange("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePointsByItem(
    @RequestParam("patronGroupId") String patronGroupId,
    @RequestParam("operation") String operation,
    @RequestParam("itemId") String itemId);

  @GetExchange("/requests/allowed-service-points")
  AllowedServicePointsResponse allowedServicePoints(
    @RequestParam("operation") String operation,
    @RequestParam("requestId") String requestId);
}
