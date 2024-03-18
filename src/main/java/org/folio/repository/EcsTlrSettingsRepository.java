package org.folio.repository;

import java.util.UUID;

import org.folio.domain.entity.EcsTlrSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EcsTlrSettingsRepository extends JpaRepository<EcsTlrSettingsEntity, UUID> {
}
