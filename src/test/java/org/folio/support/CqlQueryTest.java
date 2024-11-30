package org.folio.support;

import static java.util.Collections.emptyList;
import static org.folio.support.CqlQuery.empty;
import static org.folio.support.CqlQuery.exactMatch;
import static org.folio.support.CqlQuery.exactMatchAny;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CqlQueryTest {

  @Test
  void exactMatchBuildsCorrectQuery() {
    assertThat( exactMatch("key", "value"), is(new CqlQuery("key==\"value\"")));
  }

  @Test
  void exactMatchAnyBuildsCorrectQuery() {
    assertThat(exactMatchAny("key", List.of("value1", "value2")),
      is(new CqlQuery("key==(\"value1\" or \"value2\")")));
  }

  @Test
  void exactMatchAnyThrowsExceptionWhenCollectionOfValuesIsEmpty() {
    List<String> values = emptyList();
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> exactMatchAny("index", values));
    assertThat(exception.getMessage(), is("Values cannot be null or empty"));
  }

  @Test
  void exactMatchAnyThrowsExceptionWhenCollectionOfValuesIsNull() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> exactMatchAny("index", null));
    assertThat(exception.getMessage(), is("Values cannot be null or empty"));
  }

  @Test
  void exactMatchAnyThrowsExceptionWhenIndexIsNull() {
    List<String> values = List.of("value");
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> exactMatchAny(null, values));
    assertThat(exception.getMessage(), is("Index cannot be blank"));
  }

  @Test
  void exactMatchAnyThrowsExceptionWhenIndexIsEmptyString() {
    List<String> values = List.of("value");
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> exactMatchAny("", values));
    assertThat(exception.getMessage(), is("Index cannot be blank"));
  }

  @Test
  void emptyQuery() {
    assertThat(CqlQuery.empty().toString(), is(""));
  }

  @Test
  void exactMatchAnyIdBuildCorrectQuery() {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();
    assertThat(CqlQuery.exactMatchAnyId(List.of(uuid1, uuid2)).toString(),
      is(String.format("id==(\"%s\" or \"%s\")", uuid1, uuid2)));
  }

  @Test
  void andBuildsCorrectQuery() {
    assertThat(exactMatch("key1", "value1").and(exactMatch("key2", "value2")).toString(),
      is("key1==\"value1\" and (key2==\"value2\")"));
    assertThat(empty().and(exactMatch("key2", "value2")).toString(), is("key2==\"value2\""));
    assertThat(exactMatch("key1", "value1").and(empty()).toString(), is("key1==\"value1\""));
    assertThat(exactMatch("key1", "value1").and(null).toString(), is("key1==\"value1\""));
  }
}