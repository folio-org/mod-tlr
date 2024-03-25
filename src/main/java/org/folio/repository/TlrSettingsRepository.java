package org.folio.repository;

import java.util.UUID;

import org.folio.domain.entity.TlrSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TlrSettingsRepository extends JpaRepository<TlrSettingsEntity, UUID> {
}
