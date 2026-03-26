package org.folio.client;

import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.TransactionStatusResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "ecs-request-transactions")
public interface DcbEcsTransactionClient {

  @PostExchange("/{dcbTransactionId}")
  TransactionStatusResponse createTransaction(@PathVariable String dcbTransactionId,
    @RequestBody DcbTransaction dcbTransaction);

  @PatchExchange("/{dcbTransactionId}")
  TransactionStatusResponse updateTransaction(@PathVariable String dcbTransactionId,
    @RequestBody DcbTransaction dcbTransaction);

}
