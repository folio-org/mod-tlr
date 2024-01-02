package org.folio.domain.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UUIDConverterTest {
  private static final UUIDConverter CONVERTER = new UUIDConverter();

  @Test
  void convertValidUUIDStringToUUID() {
    UUID uuid = UUID.randomUUID();
    assertEquals(CONVERTER.convertToDatabaseColumn(uuid.toString()), uuid);
  }

  @Test
  void convertNullUUIDStringToUUID() {
    assertNull(CONVERTER.convertToDatabaseColumn(null));
  }

  @Test
  void convertValidUUIDToString() {
    UUID uuid = UUID.randomUUID();
    assertEquals(CONVERTER.convertToEntityAttribute(uuid), uuid.toString());
  }

  @Test
  void convertNullUUIDToString() {
    assertNull(CONVERTER.convertToEntityAttribute(null));
  }

}