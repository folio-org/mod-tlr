package org.folio.client;

import org.folio.domain.dto.TransactionStatus;
import org.folio.domain.dto.TransactionStatusResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "transactions")
public interface DcbTransactionClient {

  @GetExchange("/{dcbTransactionId}/status")
  TransactionStatusResponse getDcbTransactionStatus(@PathVariable String dcbTransactionId);

  @PutExchange("/{dcbTransactionId}/status")
  TransactionStatusResponse changeDcbTransactionStatus(@PathVariable String dcbTransactionId,
    @RequestBody TransactionStatus newStatus);

}
