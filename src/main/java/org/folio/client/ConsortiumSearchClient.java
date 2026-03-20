package org.folio.client;

import org.folio.domain.dto.BatchIds;
import org.folio.domain.dto.ConsortiumItems;
import org.folio.domain.dto.SearchItemResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "search/consortium")
public interface ConsortiumSearchClient {

  @GetExchange("/item/{itemId}")
  SearchItemResponse searchItem(@PathVariable("itemId") String itemId);

  @PostExchange("/batch/items")
  ConsortiumItems searchItems(@RequestBody BatchIds batchIds);

}
