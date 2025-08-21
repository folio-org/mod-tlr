package org.folio.service;

import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;

public interface DeclareClaimedReturnedItemAsMissingService {
  void declareMissing(DeclareClaimedReturnedItemAsMissingRequest request);
}
