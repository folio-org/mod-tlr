package org.folio.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class HttpFailureFeignException extends RuntimeException {
  private final String url;
  private final int statusCode;
  private final String responseBody;
}
