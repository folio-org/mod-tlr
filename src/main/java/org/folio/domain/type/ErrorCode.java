package org.folio.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  ECS_REQUEST_CANNOT_BE_PLACED_FOR_INACTIVE_PATRON(
    "ECS_REQUEST_CANNOT_BE_PLACED_FOR_INACTIVE_PATRON"),
  LOAN_NOT_FOUND("LOAN_NOT_FOUND"),
  INVALID_LOAN_ACTION_REQUEST("INVALID_LOAN_ACTION_REQUEST");

  private final String value;
}
