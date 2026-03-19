package org.folio.client;

import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.CirculationItems;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "circulation-item")
public interface CirculationItemClient {

  @GetExchange(value = "/{circulationItemId}")
  CirculationItem getCirculationItem(@PathVariable String circulationItemId);

  @PostExchange(value = "/{circulationItemId}")
  CirculationItem createCirculationItem(@PathVariable String circulationItemId,
    @RequestBody CirculationItem circulationItem);

  @PutExchange(value = "/{circulationItemId}")
  CirculationItem updateCirculationItem(@PathVariable String circulationItemId,
    @RequestBody CirculationItem circulationItem);

  @GetExchange
  CirculationItems getCirculationItems(@RequestParam("query") String query);
}
