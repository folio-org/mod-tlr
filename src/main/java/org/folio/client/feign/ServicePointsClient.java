package org.folio.client.feign;

import org.folio.domain.dto.ServicePoint;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "service-points", url = "service-points", configuration = FeignClientConfiguration.class)
public interface ServicePointsClient {

  @PostMapping
  ServicePoint postServicePoint(@RequestBody ServicePoint servicePoint);

  @GetMapping("/{id}")
  ServicePoint getServicePoint(@PathVariable String id);
}
