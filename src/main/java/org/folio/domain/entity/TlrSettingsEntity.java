package org.folio.domain.entity;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
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
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "exclude_from_ecs_request_lending_tenant_search")
  private List<String> excludeFromEcsRequestLendingTenantSearch;
}
