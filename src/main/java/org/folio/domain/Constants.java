package org.folio.domain;

import static org.folio.domain.dto.Request.RequestTypeEnum.HOLD;

import org.folio.domain.dto.Request;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {
  public static final String CENTRAL_TENANT_ID = "consortium";
  public static final Request.RequestTypeEnum PRIMARY_REQUEST_TYPE = HOLD;
}
