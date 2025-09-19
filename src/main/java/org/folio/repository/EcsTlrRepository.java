package org.folio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.domain.entity.EcsTlrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EcsTlrRepository extends JpaRepository<EcsTlrEntity, UUID> {
  Optional<EcsTlrEntity> findBySecondaryRequestId(UUID secondaryRequestId);
  Optional<EcsTlrEntity> findByPrimaryRequestId(UUID primaryRequestId);
  Optional<EcsTlrEntity> findByInstanceId(UUID instanceId);
  List<EcsTlrEntity> findByPrimaryRequestIdIn(List<UUID> primaryRequestIds);
  List<EcsTlrEntity> findByItemId(UUID itemId);

  @Query("""
    SELECT ecsr from EcsTlrEntity ecsr
    WHERE ecsr.requestLevel = 'Title'
    AND ecsr.requesterId = ?1
    AND ecsr.instanceId = ?2
    AND ecsr.primaryRequestStatus IN ('Open - Not yet filled', 'Open - Awaiting pickup',
        'Open - In transit', 'Open - Awaiting delivery')
    """)
  Optional<List<EcsTlrEntity>> findOpenRequests(UUID requesterId, UUID instanceId);
}
