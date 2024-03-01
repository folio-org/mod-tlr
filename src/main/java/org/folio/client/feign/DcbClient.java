package org.folio.client.feign;

import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dcb", url = "${folio.okapi-url}", configuration = FeignClientConfiguration.class)
public interface DcbClient {

  @PostMapping("/ecs-tlr-transactions/{dcbTransactionId}")
  TransactionStatusResponse createDcbTransaction(@PathVariable String dcbTransactionId,
    @RequestBody DcbTransaction dcbTransaction);

}
