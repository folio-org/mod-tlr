package org.folio.mr.domain.mapper;

import org.folio.mr.domain.dto.TitleLevelRequest;
import org.folio.mr.domain.entity.TitleLevelRequestEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TitleLevelRequestMapper {
  TitleLevelRequest mapEntityToDto(TitleLevelRequestEntity requestEntity);
  TitleLevelRequestEntity mapDtoToEntity(TitleLevelRequest request);
}
