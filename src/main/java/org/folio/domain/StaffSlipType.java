package org.folio.domain;

import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.Request.RequestTypeEnum.PAGE;
import static org.folio.domain.dto.Request.StatusEnum.OPEN_NOT_YET_FILLED;

import java.util.EnumSet;

import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum StaffSlipType {
  PICK_SLIP(
    EnumSet.of(PAGED),
    EnumSet.of(OPEN_NOT_YET_FILLED),
    EnumSet.of(PAGE)
  );

  private final EnumSet<ItemStatus.NameEnum> relevantItemStatuses;
  private final EnumSet<Request.StatusEnum> relevantRequestStatuses;
  private final EnumSet<Request.RequestTypeEnum> relevantRequestTypes;
}
