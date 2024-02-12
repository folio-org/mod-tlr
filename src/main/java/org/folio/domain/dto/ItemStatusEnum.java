package org.folio.domain.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ItemStatusEnum {
  AVAILABLE("Available"),
  CHECKED_OUT("Checked out"),
  IN_TRANSIT("In transit");

  private final String value;
}
