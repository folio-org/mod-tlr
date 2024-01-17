package org.folio.domain.entity;

import java.util.Date;
import java.util.UUID;

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
  private UUID id;
  private UUID instanceId;
  private UUID requesterId;
  private String requestType;
  private String requestLevel;
  private Date requestExpirationDate;
  private String patronComments;
  private String fulfillmentPreference;
  private UUID pickupServicePointId;
}
