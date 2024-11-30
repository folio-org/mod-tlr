package org.folio.client.feign;

import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.Requests;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "circulation-request", url = "circulation/requests", configuration = FeignClientConfiguration.class)
public interface RequestCirculationClient {

  @GetMapping("/queue/instance/{instanceId}")
  Requests getRequestsQueueByInstanceId(@PathVariable String instanceId);

  @GetMapping("/queue/item/{itemId}")
  Requests getRequestsQueueByItemId(@PathVariable String itemId);

  @PostMapping("/queue/instance/{instanceId}/reorder")
  Requests reorderRequestsQueueForInstanceId(@PathVariable String instanceId,
    @RequestBody ReorderQueue reorderQueue);

  @PostMapping("/queue/item/{itemId}/reorder")
  Requests reorderRequestsQueueForItemId(@PathVariable String itemId,
    @RequestBody ReorderQueue reorderQueue);
}
