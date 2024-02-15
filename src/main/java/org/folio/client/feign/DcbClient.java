package org.folio.client.feign;

import org.folio.domain.dto.DcbTransaction;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "dcb", url = "${folio.okapi-url}", configuration = FeignClientConfiguration.class)
public interface DcbClient {

  @PostMapping("/ecs-tlr-transaction")
  DcbTransaction createDcbTransaction(DcbTransaction dcbTransaction);

}
