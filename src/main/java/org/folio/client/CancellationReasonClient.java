package org.folio.client;

import org.folio.domain.dto.CancellationReason;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "cancellation-reason-storage/cancellation-reasons")
public interface CancellationReasonClient {

  @GetExchange("/{id}")
  CancellationReason getCancellationReason(@PathVariable String id);
}
