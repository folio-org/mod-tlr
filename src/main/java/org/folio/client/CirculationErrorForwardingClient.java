package org.folio.client;

import org.folio.domain.dto.CirculationClaimItemReturnedRequest;
import org.folio.domain.dto.CirculationDeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "circulation")
public interface CirculationErrorForwardingClient {

  @PostExchange("/loans/{loanId}/declare-item-lost")
  ResponseEntity<Void> declareItemLost(@PathVariable String loanId,
    @RequestBody CirculationDeclareItemLostRequest request);

  @PostExchange("/loans/{loanId}/claim-item-returned")
  ResponseEntity<Void> claimItemReturned(@PathVariable String loanId,
    @RequestBody CirculationClaimItemReturnedRequest request);

  @PostExchange("/loans/{loanId}/declare-claimed-returned-item-as-missing")
  ResponseEntity<Void> declareClaimedReturnedItemAsMissing(@PathVariable String loanId,
    @RequestBody CirculationDeclareClaimedReturnedItemAsMissingRequest request);

}
