package org.folio.domain.mapper;

import org.folio.domain.dto.TitleLevelRequest;
import org.folio.domain.entity.TitleLevelRequestEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TitleLevelRequestMapper {
  TitleLevelRequest mapEntityToDto(TitleLevelRequestEntity requestEntity);
  TitleLevelRequestEntity mapDtoToEntity(TitleLevelRequest request);
}
