package org.folio.client;

import org.folio.domain.dto.HoldingsRecord;
import org.folio.domain.dto.HoldingsRecords;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "holdings-storage/holdings")
public interface HoldingClient extends GetByQueryClient<HoldingsRecords> {

  @Override
  @GetExchange
  HoldingsRecords getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange("/{id}")
  HoldingsRecord get(@PathVariable String id);

}
