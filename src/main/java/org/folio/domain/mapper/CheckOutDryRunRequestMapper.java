package org.folio.domain.mapper;

import org.folio.domain.dto.CheckOutDryRunRequest;
import org.folio.domain.dto.CheckOutRequest;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface CheckOutDryRunRequestMapper {

  CheckOutDryRunRequest mapCheckOutRequestToCheckOutDryRunRequest(CheckOutRequest checkOutRequest);
}
