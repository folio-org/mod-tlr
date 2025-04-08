package org.folio.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InventoryKafkaEvent<T> extends KafkaEvent<T> {
  private String eventId;
  private EventType type;
  @JsonProperty("old")
  private T oldVersion;
  @JsonProperty("new")
  private T newVersion;

  public enum EventType {
    UPDATE, DELETE, CREATE, DELETE_ALL, REINDEX, ITERATE, MIGRATION
  }
}
