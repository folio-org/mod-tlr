package org.folio.client;

import org.folio.domain.dto.UserTenantCollection;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "user-tenants")
public interface UserTenantsClient {

  @GetExchange
  UserTenantCollection getUserTenants(@RequestParam(name = "limit", required = false) Integer limit);
}
