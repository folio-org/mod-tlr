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
@Table(name = "tlr_settings")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TlrSettingsEntity {

  @Id
  private UUID id;
  private boolean ecsTlrFeatureEnabled;
  private String excludeFromEcsRequestLendingTenantSearch;
}
