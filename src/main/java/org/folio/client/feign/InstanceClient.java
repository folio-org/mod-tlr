package org.folio.client.feign;

import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Instances;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "instances", url = "instance-storage/instances", configuration = FeignClientConfiguration.class)
public interface InstanceClient extends GetByQueryClient<Instances> {

  @GetMapping("/{id}")
  Instance get(@PathVariable String id);

}
