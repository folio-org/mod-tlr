package org.folio.client.feign;

import org.folio.domain.dto.TenantCollection;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "consortia", url = "consortia", configuration = FeignClientConfiguration.class)
public interface ConsortiaClient {

  @GetMapping(value = "/{consortiumId}/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
  TenantCollection getConsortiaTenants(@PathVariable String consortiumId);
}
