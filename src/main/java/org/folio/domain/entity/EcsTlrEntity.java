package org.folio.domain.entity;

import java.util.UUID;

import org.folio.domain.converter.UUIDConverter;
import org.joda.time.DateTime;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ecs_tlr")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EcsTlrEntity {

  @Id
  @Convert(converter = UUIDConverter.class)
  private UUID id;
  @Convert(converter = UUIDConverter.class)
  private UUID instanceId;
  @Convert(converter = UUIDConverter.class)
  private UUID requesterId;
  private String requestType;
  private String requestLevel;
  private DateTime requestExpirationDate;
  private String patronComments;
  private String fulfillmentPreference;
  @Convert(converter = UUIDConverter.class)
  private UUID pickupServicePointId;
}
