package org.folio.domain.mapper;

import org.folio.domain.dto.EcsRequestExternal;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface EcsTlrMapper {

  @Mapping(target = "requestType", qualifiedByName = "StringToEcsTlrRequestType")
  @Mapping(target = "requestLevel", qualifiedByName = "StringToEcsTlrRequestLevel")
  @Mapping(target = "fulfillmentPreference", qualifiedByName = "StringToEcsTlrFulfillmentPreference")
  EcsTlr mapEntityToDto(EcsTlrEntity ecsTlrEntity);

  @Mapping(target = "requestLevel", qualifiedByName = "EcsRequestExternalToEcsTlrRequestLevel")
  @Mapping(target = "fulfillmentPreference", qualifiedByName = "EcsRequestExternalToEcsTlrFulfillmentPreference")
  EcsTlr mapEcsRequestExternalToEcsTlr(EcsRequestExternal ecsRequestExternal);

  @Mapping(target = "requestType", qualifiedByName = "RequestTypeToString")
  @Mapping(target = "requestLevel", qualifiedByName = "RequestLevelToString")
  @Mapping(target = "fulfillmentPreference", qualifiedByName = "FulfillmentPreferenceToString")
  EcsTlrEntity mapDtoToEntity(EcsTlr ecsTlr);

  @Mapping(target = "requestType", qualifiedByName = "StringToRequestType")
  @Mapping(target = "requestLevel", qualifiedByName = "StringToRequestLevel")
  @Mapping(target = "fulfillmentPreference", qualifiedByName = "StringToFulfillmentPreference")
  Request mapEntityToRequest(EcsTlrEntity ecsTlr);

  @Named("StringToEcsTlrRequestType")
  default EcsTlr.RequestTypeEnum mapDtoRequestType(String requestType) {
    return requestType != null ? EcsTlr.RequestTypeEnum.fromValue(requestType) : null;
  }

  @Named("StringToRequestType")
  default Request.RequestTypeEnum mapRequestType(String requestType) {
    return requestType != null ? Request.RequestTypeEnum.fromValue(requestType) : null;
  }

  @Named("StringToEcsTlrRequestLevel")
  default EcsTlr.RequestLevelEnum mapDtoRequestLevel(String requestLevel) {
    return requestLevel != null ? EcsTlr.RequestLevelEnum.fromValue(requestLevel) : null;
  }

  @Named("EcsRequestExternalToEcsTlrRequestLevel")
  default EcsTlr.RequestLevelEnum mapFromRequestExternalToDtoRequestLevel(
    EcsRequestExternal.RequestLevelEnum ecsRequestExternalRequestLevel) {

    return ecsRequestExternalRequestLevel != null
      ? EcsTlr.RequestLevelEnum.fromValue(ecsRequestExternalRequestLevel.getValue())
      : null;
  }

  @Named("StringToRequestLevel")
  default Request.RequestLevelEnum mapRequestLevel(String requestLevel) {
    return requestLevel != null ? Request.RequestLevelEnum.fromValue(requestLevel) : null;
  }

  @Named("StringToEcsTlrFulfillmentPreference")
  default EcsTlr.FulfillmentPreferenceEnum mapDtoFulfillmentPreference(String fulfillmentPreference) {
    return fulfillmentPreference != null ? EcsTlr.FulfillmentPreferenceEnum.fromValue(fulfillmentPreference) : null;
  }

  @Named("EcsRequestExternalToEcsTlrFulfillmentPreference")
  default EcsTlr.FulfillmentPreferenceEnum mapDtoFulfillmentPreference(
    EcsRequestExternal.FulfillmentPreferenceEnum fulfillmentPreference) {
    return fulfillmentPreference != null
      ? EcsTlr.FulfillmentPreferenceEnum.fromValue(fulfillmentPreference.getValue())
      : null;
  }

  @Named("StringToFulfillmentPreference")
  default Request.FulfillmentPreferenceEnum mapFulfillmentPreference(String fulfillmentPreference) {
    return fulfillmentPreference != null ? Request.FulfillmentPreferenceEnum.fromValue(fulfillmentPreference) : null;
  }

  @Named("RequestTypeToString")
  default String mapRequestTypeToString(EcsTlr.RequestTypeEnum requestTypeEnum) {
    return requestTypeEnum != null ? requestTypeEnum.getValue() : null;
  }

  @Named("RequestLevelToString")
  default String mapRequestLevelToString(EcsTlr.RequestLevelEnum requestLevelEnum) {
    return requestLevelEnum != null ? requestLevelEnum.getValue() : null;
  }

  @Named("FulfillmentPreferenceToString")
  default String mapFulfillmentPreferenceToString(EcsTlr.FulfillmentPreferenceEnum fulfillmentPreferenceEnum) {
    return fulfillmentPreferenceEnum != null ? fulfillmentPreferenceEnum.getValue() : null;
  }

}
