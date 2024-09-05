package org.folio.client.feign;

import org.folio.domain.dto.CirculationItem;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "circulation-item", url = "circulation-item", configuration = FeignClientConfiguration.class)
public interface CirculationItemClient {

  @PostMapping("/")
  CirculationItem createCirculationItem(CirculationItem circulationItem);

}
