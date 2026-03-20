package org.folio.client;

import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.Requests;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "circulation/requests")
public interface RequestCirculationClient {

  @GetExchange("/queue/instance/{instanceId}")
  Requests getRequestsQueueByInstanceId(@PathVariable String instanceId);

  @GetExchange("/queue/item/{itemId}")
  Requests getRequestsQueueByItemId(@PathVariable String itemId);

  @PostExchange("/queue/instance/{instanceId}/reorder")
  Requests reorderRequestsQueueForInstanceId(@PathVariable String instanceId,
    @RequestBody ReorderQueue reorderQueue);

  @PostExchange("/queue/item/{itemId}/reorder")
  Requests reorderRequestsQueueForItemId(@PathVariable String itemId,
    @RequestBody ReorderQueue reorderQueue);
}
