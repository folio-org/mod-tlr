package org.folio.client.feign;

import org.folio.domain.dto.CirculationItem;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "circulation-item", url = "circulation-item",
  configuration = FeignClientConfiguration.class, dismiss404 = true)
public interface CirculationItemClient {

  @GetMapping(value = "/{circulationItemId}")
  CirculationItem getCirculationItem(@PathVariable String circulationItemId);

  @PostMapping(value = "/{circulationItemId}")
  CirculationItem createCirculationItem(@PathVariable String circulationItemId,
    @RequestBody CirculationItem circulationItem);

  @PostMapping(value = "/{circulationItemId}")
  CirculationItem updateCirculationItem(@PathVariable String circulationItemId,
    @RequestBody CirculationItem circulationItem);

}
