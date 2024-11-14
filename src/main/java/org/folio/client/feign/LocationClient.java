package org.folio.client.feign;

import org.folio.domain.dto.Location;
import org.folio.domain.dto.Locations;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "locations", url = "locations", configuration = FeignClientConfiguration.class)
public interface LocationClient extends GetByQueryClient<Locations> {

  @GetMapping("/{id}")
  Location findLocation(@PathVariable String id);

}
