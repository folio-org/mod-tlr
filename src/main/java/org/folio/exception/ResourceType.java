package org.folio.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ResourceType {
  USER("user");

  private final String value;
}
