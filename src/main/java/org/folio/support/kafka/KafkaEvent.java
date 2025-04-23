package org.folio.support.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public abstract class KafkaEvent<T> {
  @JsonIgnore
  private String tenantIdHeaderValue;

  @JsonIgnore
  private String userIdHeaderValue;

  public KafkaEvent<T> withTenantIdHeaderValue(String tenantIdHeaderValue) {
    this.tenantIdHeaderValue = tenantIdHeaderValue;
    return this;
  }

  public KafkaEvent<T> withUserIdHeaderValue(String userIdHeaderValue) {
    this.userIdHeaderValue = userIdHeaderValue;
    return this;
  }

  public abstract String getId();
  public abstract String getTenant();
  public abstract EventType getGenericType();
  public abstract T getNewVersion();
  public abstract T getOldVersion();
}
