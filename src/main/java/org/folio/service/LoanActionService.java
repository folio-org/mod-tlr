package org.folio.service;

import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareItemLostRequest;

public interface LoanActionService {
  void declareItemLost(DeclareItemLostRequest request);
  void claimItemReturned(ClaimItemReturnedRequest request);
  void declareClaimedReturnedItemMissing(DeclareClaimedReturnedItemAsMissingRequest request);
}
