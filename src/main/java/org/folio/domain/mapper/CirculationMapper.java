package org.folio.domain.mapper;

import org.folio.domain.dto.CirculationDeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareClaimedReturnedItemAsMissingRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.folio.domain.dto.CirculationClaimItemReturnedRequest;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface CirculationMapper {

  CirculationDeclareItemLostRequest toCirculationDeclareItemLostRequest(
    DeclareItemLostRequest declareItemLostRequest);

  CirculationClaimItemReturnedRequest toCirculationClaimItemReturnedRequest(
    ClaimItemReturnedRequest claimItemReturnedRequest);

  CirculationDeclareClaimedReturnedItemAsMissingRequest toCirculationDeclareClaimedReturnedItemsAsMissingRequest(
    DeclareClaimedReturnedItemAsMissingRequest request);

}
