package org.folio.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class KafkaEvent<T> {
  @With
  @JsonIgnore
  String tenantIdHeaderValue;

  @With
  @JsonIgnore
  String userIdHeaderValue;
}
