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
public class DefaultKafkaEvent<T> extends KafkaEvent<T> {
  private String id;
  private String tenant;
  private DefaultKafkaEventType type;
  private long timestamp;
  @ToString.Exclude
  private DefaultKafkaEventData<T> data;

  static Map<DefaultKafkaEventType, EventType> EVENT_TYPE_INTERNAL_TO_GENERIC = Map.of(
    DefaultKafkaEventType.UPDATED, EventType.UPDATE,
    DefaultKafkaEventType.CREATED, EventType.CREATE,
    DefaultKafkaEventType.DELETED, EventType.DELETE,
    DefaultKafkaEventType.ALL_DELETED, EventType.DELETE_ALL);

  static Map<EventType, DefaultKafkaEventType> EVENT_TYPE_GENERIC_TO_INTERNAL = Map.of(
    EventType.UPDATE, DefaultKafkaEventType.UPDATED,
    EventType.CREATE,DefaultKafkaEventType.CREATED,
    EventType.DELETE, DefaultKafkaEventType.DELETED,
    EventType.DELETE_ALL, DefaultKafkaEventType.ALL_DELETED);

  @Override
  public T getNewVersion() {
    return data.getNewVersion();
  }

  @Override
  public T getOldVersion() {
    return data.getOldVersion();
  }

  @Override
  public EventType getGenericType() {
    return type == null ? null : EVENT_TYPE_INTERNAL_TO_GENERIC.get(type);
  }

  public DefaultKafkaEvent<T> withGenericType(EventType type) {
    return type == null ? this : this.withType(EVENT_TYPE_GENERIC_TO_INTERNAL.get(type));
  }

  public enum DefaultKafkaEventType {
    UPDATED, CREATED, DELETED, ALL_DELETED
  }

  @Builder
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DefaultKafkaEventData<T> {
    @JsonProperty("old")
    private T oldVersion;
    @JsonProperty("new")
    private T newVersion;
  }
}
