package org.folio.client.feign;

import org.folio.domain.dto.ConsortiaConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "consortia-configuration", url = "consortia-configuration", configuration = CustomFeignClientConfiguration.class)
public interface ConsortiaConfigurationClient {

  @GetMapping
  ConsortiaConfiguration getConfiguration();

}
