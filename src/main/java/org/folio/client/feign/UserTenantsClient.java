package org.folio.client.feign;

import org.folio.domain.dto.UserTenantCollection;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-tenants", url = "user-tenants", configuration = FeignClientConfiguration.class)
public interface UserTenantsClient {

  @GetMapping()
  UserTenantCollection getUserTenants(@RequestParam(name = "limit", required = false) Integer limit);
}
