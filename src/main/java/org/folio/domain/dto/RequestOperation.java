package org.folio.domain.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum RequestOperation {
  CREATE("create"),
  REPLACE("replace");

  private final String value;

  public static RequestOperation from(String operation) {
    return valueOf(operation.toUpperCase());
  }

  @Override
  public String toString() {
    return getValue();
  }
}
