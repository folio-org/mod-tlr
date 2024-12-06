package org.folio.client.feign;

import org.folio.domain.dto.HoldingsRecord;
import org.folio.domain.dto.HoldingsRecords;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "holdings", url = "holdings-storage/holdings", configuration = FeignClientConfiguration.class)
public interface HoldingClient extends GetByQueryClient<HoldingsRecords> {

  @GetMapping("/{id}")
  HoldingsRecord get(@PathVariable String id);

}
