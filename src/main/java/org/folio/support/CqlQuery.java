package org.folio.support;

import static java.lang.String.format;

public record CqlQuery(String query) {
  @Override
  public String toString() {
    return query;
  }

  public static CqlQuery exactMatch(String index, String value) {
    return new CqlQuery(format("%s==\"%s\"", index, value));
  }
}
