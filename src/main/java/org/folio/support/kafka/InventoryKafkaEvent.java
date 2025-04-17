package org.folio.support.kafka;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Builder
@With
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
public class InventoryKafkaEvent<T> extends KafkaEvent<T> {
  private String eventId;
  @Getter
  private String tenant;
  private InventoryKafkaEventType type;
  @JsonProperty("old")
  private T oldVersion;
  @JsonProperty("new")
  private T newVersion;

  static Map<InventoryKafkaEvent.InventoryKafkaEventType, EventType>
    EVENT_TYPE_INTERNAL_TO_GENERIC = Map.of(
    InventoryKafkaEventType.UPDATE, EventType.UPDATE,
    InventoryKafkaEventType.DELETE, EventType.DELETE,
    InventoryKafkaEventType.CREATE, EventType.CREATE,
    InventoryKafkaEventType.DELETE_ALL, EventType.DELETE_ALL,
    InventoryKafkaEventType.REINDEX, EventType.REINDEX,
    InventoryKafkaEventType.ITERATE, EventType.ITERATE,
    InventoryKafkaEventType.MIGRATION, EventType.MIGRATION);

//  static Map<EventType, InventoryKafkaEvent.InventoryKafkaEventType>
//    EVENT_TYPE_GENERIC_TO_INTERNAL = Map.of(
//    EventType.UPDATE,InventoryKafkaEventType.UPDATE,
//    EventType.DELETE,InventoryKafkaEventType.DELETE,
//    EventType.CREATE,InventoryKafkaEventType.CREATE,
//    EventType.DELETE_ALL,InventoryKafkaEventType.DELETE_ALL,
//    EventType.REINDEX,InventoryKafkaEventType.REINDEX,
//    EventType.ITERATE,InventoryKafkaEventType.ITERATE,
//    EventType.MIGRATION, InventoryKafkaEventType.MIGRATION);

  @Override
  public String getId() {
    return eventId;
  }

  @Override
  public EventType getGenericType() {
    return type == null ? null : EVENT_TYPE_INTERNAL_TO_GENERIC.get(type);
  }

//  public InventoryKafkaEvent<T> withGenericType(EventType type) {
//    return type == null ? this : this.withType(EVENT_TYPE_GENERIC_TO_INTERNAL.get(type));
//  }

  public enum InventoryKafkaEventType {
    UPDATE, DELETE, CREATE, DELETE_ALL, REINDEX, ITERATE, MIGRATION
  }
}
