package org.folio.domain.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UUIDConverterTest {
  private static final UUIDConverter CONVERTER = new UUIDConverter();

  @Test
  public void convertValidUUIDStringToUUID() {
    UUID uuid = UUID.randomUUID();
    assertEquals(CONVERTER.convertToDatabaseColumn(uuid.toString()), uuid);
  }

  @Test
  public void convertNullUUIDStringToUUID() {
    assertNull(CONVERTER.convertToDatabaseColumn(null));
  }

  @Test
  public void convertValidUUIDToString() {
    UUID uuid = UUID.randomUUID();
    assertEquals(CONVERTER.convertToEntityAttribute(uuid), uuid.toString());
  }

  @Test
  public void convertNullUUIDToString() {
    assertNull(CONVERTER.convertToEntityAttribute(null));
  }

}