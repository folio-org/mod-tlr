package org.folio.client.feign;

import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dcb-ecs-transactions", url = "ecs-request-transactions",
  configuration = FeignClientConfiguration.class)

public interface DcbEcsTransactionClient {

  @PostMapping("/{dcbTransactionId}")
  TransactionStatusResponse createTransaction(@PathVariable String dcbTransactionId,
    @RequestBody DcbTransaction dcbTransaction);

}
