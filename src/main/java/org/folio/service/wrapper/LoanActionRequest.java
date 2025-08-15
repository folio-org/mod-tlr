package org.folio.service.wrapper;

import java.util.UUID;

import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareItemLostRequest;

public record LoanActionRequest<T>(T originalRequest, UUID loanId, UUID itemId, UUID userId) {

  public static LoanActionRequest<DeclareItemLostRequest> from(DeclareItemLostRequest request) {
    return new LoanActionRequest<>(request, request.getLoanId(), request.getItemId(), request.getUserId());
  }

  public static LoanActionRequest<ClaimItemReturnedRequest> from(ClaimItemReturnedRequest request) {
    return new LoanActionRequest<>(request, request.getLoanId(), request.getItemId(), request.getUserId());
  }

  public static LoanActionRequest<DeclareClaimedReturnedItemAsMissingRequest> from(
    DeclareClaimedReturnedItemAsMissingRequest request) {

    return new LoanActionRequest<>(request, request.getLoanId(), request.getItemId(), request.getUserId());
  }

}

