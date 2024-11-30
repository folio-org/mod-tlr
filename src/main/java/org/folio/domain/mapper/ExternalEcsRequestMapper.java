package org.folio.domain.mapper;

import org.folio.domain.dto.EcsRequestExternal;
import org.folio.domain.dto.EcsTlr;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ExternalEcsRequestMapper {

  @Mapping(target = "requestLevel", qualifiedByName = "ExternalEcsRequestToEcsTlrRequestLevel")
  @Mapping(target = "fulfillmentPreference", qualifiedByName = "ExternalEcsRequestToEcsTlrFulfillmentPreference")
  EcsTlr mapEcsRequestExternalToEcsTlr(EcsRequestExternal ecsRequestExternal);

  @Named("ExternalEcsRequestToEcsTlrRequestLevel")
  default EcsTlr.RequestLevelEnum mapExternalEcsRequestToEcsTlrRequestLevel(
    EcsRequestExternal.RequestLevelEnum ecsRequestExternalRequestLevel) {

    return ecsRequestExternalRequestLevel != null
      ? EcsTlr.RequestLevelEnum.fromValue(ecsRequestExternalRequestLevel.getValue())
      : null;
  }

  @Named("ExternalEcsRequestToEcsTlrFulfillmentPreference")
  default EcsTlr.FulfillmentPreferenceEnum mapExternalEcsRequestToEcsTlrFulfillmentPreference(
    EcsRequestExternal.FulfillmentPreferenceEnum fulfillmentPreference) {
    return fulfillmentPreference != null
      ? EcsTlr.FulfillmentPreferenceEnum.fromValue(fulfillmentPreference.getValue())
      : null;
  }

}
