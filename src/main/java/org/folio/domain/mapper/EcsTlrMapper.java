package org.folio.domain.mapper;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.entity.EcsTlrEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface EcsTlrMapper {

  @Mapping(target = "requestType", qualifiedByName = "StringToRequestType")
  @Mapping(target = "requestLevel", qualifiedByName = "StringToRequestLevel")
  @Mapping(target = "fulfillmentPreference", qualifiedByName = "StringToFulfillmentPreference")
  EcsTlr mapEntityToDto(EcsTlrEntity ecsTlrEntity);
  EcsTlrEntity mapDtoToEntity(EcsTlr ecsTlr);

  @Named("StringToRequestType")
  default EcsTlr.RequestTypeEnum mapRequestType(String requestType) {
    return requestType != null ? EcsTlr.RequestTypeEnum.fromValue(requestType) : null;
  }

  @Named("StringToRequestLevel")
  default EcsTlr.RequestLevelEnum mapRequestLevel(String requestLevel) {
    return requestLevel != null ? EcsTlr.RequestLevelEnum.fromValue(requestLevel) : null;
  }

  @Named("StringToFulfillmentPreference")
  default EcsTlr.FulfillmentPreferenceEnum mapFulfillmentPreference(String fulfillmentPreference) {
    return fulfillmentPreference != null ? EcsTlr.FulfillmentPreferenceEnum.fromValue(fulfillmentPreference) : null;
  }
}
