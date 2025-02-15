package org.folio.client.feign;

import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Instances;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "instances", url = "instance-storage/instances", configuration = FeignClientConfiguration.class)
public interface InstanceClient extends GetByQueryClient<Instances> {

  @GetMapping("/{id}")
  Instance get(@PathVariable String id);

  @PostMapping
  Instance post(@RequestBody Instance instance);

}
