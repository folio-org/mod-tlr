package org.folio.domain.mapper;

import org.folio.domain.dto.EcsTlrSettings;
import org.folio.domain.entity.EcsTlrSettingsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface EcsTlrSettingsMapper {

  EcsTlrSettings mapEntityToDto(EcsTlrSettingsEntity ecsTlrSettingsEntity);

  EcsTlrSettingsEntity mapDtoToEntity(EcsTlrSettings ecsTlrSettings);
}
