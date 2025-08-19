package org.folio.client.feign;

import org.folio.client.feign.config.ErrorForwardingFeignClientConfiguration;
import org.folio.domain.dto.CirculationClaimItemReturnedRequest;
import org.folio.domain.dto.CirculationDeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "circulation-error-forwarding", url = "circulation",
  configuration = { FeignClientConfiguration.class, ErrorForwardingFeignClientConfiguration.class })
public interface CirculationErrorForwardingClient {

  @PostMapping("/loans/{loanId}/declare-item-lost")
  ResponseEntity<Void> declareItemLost(@PathVariable String loanId,
    @RequestBody CirculationDeclareItemLostRequest request);

  @PostMapping("/loans/{loanId}/claim-item-returned")
  ResponseEntity<Void> claimItemReturned(@PathVariable String loanId,
    @RequestBody CirculationClaimItemReturnedRequest request);

  @PostMapping("/loans/{loanId}/declare-claimed-returned-item-as-missing")
  ResponseEntity<Void> declareClaimedReturnedItemAsMissing(@PathVariable String loanId,
    @RequestBody CirculationDeclareClaimedReturnedItemAsMissingRequest request);

}
