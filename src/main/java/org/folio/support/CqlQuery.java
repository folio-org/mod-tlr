package org.folio.support;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Collection;

public record CqlQuery(String query) {

  public static final String MULTIPLE_VALUES_DELIMITER = " or ";
  public static final String EXACT_MATCH_QUERY_TEMPLATE = "%s==\"%s\"";
  public static final String MATCH_QUERY_TEMPLATE = "%s=\"%s\"";
  public static final String EXACT_MATCH_ANY_QUERY_TEMPLATE = "%s==(%s)";
  public static final String GREATER_THAN_QUERY_TEMPLATE = "%s>\"%s\"";
  public static final String LESS_THAN_QUERY_TEMPLATE = "%s<\"%s\"";

  public static CqlQuery empty() {
    return new CqlQuery(EMPTY);
  }

  public static CqlQuery exactMatch(String index, String value) {
    return new CqlQuery(format(EXACT_MATCH_QUERY_TEMPLATE, index, value));
  }

  public static CqlQuery match(String index, String value) {
    return new CqlQuery(format(MATCH_QUERY_TEMPLATE, index, value));
  }

  public static CqlQuery hasValue(String index) {
    return match(index, EMPTY);
  }

  public static CqlQuery exactMatchAnyId(Collection<String> values) {
    return exactMatchAny("id", values);
  }

  public static CqlQuery exactMatchAny(String index, Collection<String> values) {
    if (isBlank(index)) {
      throw new IllegalArgumentException("Index cannot be blank");
    }
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Values cannot be null or empty");
    }

    String joinedValues = values.stream()
      .map(value -> "\"" + value + "\"")
      .collect(joining(MULTIPLE_VALUES_DELIMITER));

    return new CqlQuery(format(EXACT_MATCH_ANY_QUERY_TEMPLATE, index, joinedValues));
  }

  public CqlQuery and(CqlQuery other) {
    if (other == null || isBlank(other.query())) {
      return this;
    }
    if (isBlank(query)) {
      return other;
    }

    return new CqlQuery(format("%s and (%s)", query, other.query()));
  }

  public CqlQuery not(CqlQuery other) {
    if (other == null || isBlank(other.query())) {
      return this;
    }
    if (isBlank(query)) {
      return other;
    }

    return new CqlQuery(format("%s not (%s)", query, other.query()));
  }

  public static CqlQuery greaterThen(String index, Object value) {
    return new CqlQuery(format(GREATER_THAN_QUERY_TEMPLATE, index, value));
  }

  public static CqlQuery lessThen(String index, Object value) {
    return new CqlQuery(format(LESS_THAN_QUERY_TEMPLATE, index, value));
  }

  @Override
  public String toString() {
    return query;
  }
}
