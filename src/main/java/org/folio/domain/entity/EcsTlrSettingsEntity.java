package org.folio.domain.entity;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ecs_tlr_settings")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EcsTlrSettingsEntity {

  @Id
  private UUID id;
  private boolean ecsTlrFeatureEnabled;
}
