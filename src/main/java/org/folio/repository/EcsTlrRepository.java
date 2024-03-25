package org.folio.repository;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.entity.EcsTlrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EcsTlrRepository extends JpaRepository<EcsTlrEntity, UUID> {
  Optional<EcsTlrEntity> findBySecondaryRequestId(UUID secondaryRequestId);
}
