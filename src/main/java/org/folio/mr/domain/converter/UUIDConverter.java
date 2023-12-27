package org.folio.mr.domain.converter;

import jakarta.persistence.AttributeConverter;
import java.util.UUID;

public class UUIDConverter implements AttributeConverter<String, UUID> {
  @Override
  public UUID convertToDatabaseColumn(String str) {
    return str == null ? null : UUID.fromString(str);
  }

  @Override
  public String convertToEntityAttribute(UUID uuid) {
    return uuid == null ? null : uuid.toString();
  }
}
