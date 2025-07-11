package org.folio.domain.mapper;

import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface CirculationMapper {

  CirculationDeclareItemLostRequest toCirculationDeclareItemLostRequest(
    DeclareItemLostRequest declareItemLostRequest);

}
