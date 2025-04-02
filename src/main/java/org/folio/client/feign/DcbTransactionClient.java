package org.folio.client.feign;

import org.folio.domain.dto.DcbUpdateTransaction;
import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dcb-transactions", url = "transactions", configuration = FeignClientConfiguration.class)
public interface DcbTransactionClient {

  @GetMapping("/{dcbTransactionId}/status")
  TransactionStatusResponse getDcbTransactionStatus(@PathVariable String dcbTransactionId);

  @PutMapping("/{dcbTransactionId}/status")
  TransactionStatusResponse changeDcbTransactionStatus(@PathVariable String dcbTransactionId,
    @RequestBody TransactionStatus newStatus);

  @PutMapping("/{dcbTransactionId}")
  TransactionStatusResponse updateDcbTransaction(@PathVariable String dcbTransactionId,
    @RequestBody DcbUpdateTransaction dcbUpdateTransaction);

}
