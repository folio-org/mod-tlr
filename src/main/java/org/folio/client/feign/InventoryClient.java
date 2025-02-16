package org.folio.client.feign;

import org.folio.domain.dto.ExtendedInstance;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory" , configuration = FeignClientConfiguration.class)
public interface InventoryClient {

  @GetMapping(value = "instances/{instanceId}")
  ExtendedInstance getInstance(@PathVariable String instanceId);

  @PostMapping(value = "instances")
  ExtendedInstance postInstance(@RequestBody ExtendedInstance instance);

}