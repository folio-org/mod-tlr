package org.folio.repository;

import org.folio.domain.entity.EcsTlrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EcsTlrRepository extends JpaRepository<EcsTlrEntity, UUID> {
  Optional<EcsTlrEntity> findBySecondaryTlrId(UUID secondaryTlrId);
}
