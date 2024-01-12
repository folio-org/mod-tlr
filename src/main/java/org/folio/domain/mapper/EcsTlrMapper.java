package org.folio.domain.mapper;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.entity.EcsTlrEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface EcsTlrMapper {
  EcsTlr mapEntityToDto(EcsTlrEntity ecsTlrEntity);
  EcsTlrEntity mapDtoToEntity(EcsTlr ecsTlr);
}
