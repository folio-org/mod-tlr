package org.folio.repository;

import org.folio.domain.entity.TitleLevelRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TitleLevelRequestsRepository extends JpaRepository<TitleLevelRequestEntity, UUID> {
}
