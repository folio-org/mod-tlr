package org.folio.domain.entity;

import jakarta.persistence.GeneratedValue;
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
  @GeneratedValue
  private UUID id;
  private UUID instanceId;
  private UUID requesterId;
  private String requestType;
  private String requestLevel;
  private Date requestExpirationDate;
  private Date requestDate;
  private String patronComments;
  private String fulfillmentPreference;
  private UUID pickupServicePointId;
  private UUID itemId;
  private UUID holdingsRecordId;
  private UUID primaryRequestId;
  private String primaryRequestTenantId;
  private UUID primaryRequestDcbTransactionId;
  private UUID secondaryRequestId;
  private String secondaryRequestTenantId;
  private UUID secondaryRequestDcbTransactionId;
  private UUID intermediateRequestId;
  private String intermediateRequestTenantId;
  private UUID intermediateRequestDcbTransactionId;

}
