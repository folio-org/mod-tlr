package org.folio.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LoanAction {
  CHECKED_IN("checkedin"),
  RESOLVE_CLAIM_AS_RETURNED_BY_PATRON("checkedInReturnedByPatron"),
  RESOLVE_CLAIM_AS_FOUND_BY_LIBRARY("checkedInFoundByLibrary");

  private final String value;
}

