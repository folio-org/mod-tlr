package org.folio.client;

import org.folio.domain.dto.Item;
import org.folio.domain.dto.Items;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "item-storage/items")
public interface ItemClient extends GetByQueryClient<Items> {

  @Override
  @GetExchange
  Items getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange("/{id}")
  Item get(@PathVariable String id);
}
