package org.folio.domain.mapper;

import org.folio.domain.dto.TlrSettings;
import org.folio.domain.entity.TlrSettingsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TlrSettingsMapper {

  @Mapping(target = "id", ignore = true)
  TlrSettings mapEntityToDto(TlrSettingsEntity tlrSettingsEntity);

  TlrSettingsEntity mapDtoToEntity(TlrSettings tlrSettings);
}
