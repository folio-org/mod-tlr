package org.folio.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DefaultKafkaEvent<T> extends KafkaEvent<T> {
  private String id;
  private String tenant;
  private EventType type;
  private long timestamp;
  @ToString.Exclude
  private EventData<T> data;

  public DefaultKafkaEvent(String id, String tenant, EventType type, long timestamp,
    EventData<T> data, String tenantIdHeaderValue) {

    this.id = id;
    this.tenant = tenant;
    this.type = type;
    this.timestamp = timestamp;
    this.data = data;
    this.tenantIdHeaderValue = tenantIdHeaderValue;
  }

  public enum EventType {
    UPDATED, CREATED, DELETED, ALL_DELETED
  }

  @Builder
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EventData<T> {
    @JsonProperty("old")
    private T oldVersion;
    @JsonProperty("new")
    private T newVersion;
  }
}
