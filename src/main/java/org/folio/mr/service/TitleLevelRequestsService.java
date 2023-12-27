package org.folio.mr.service;

import java.util.Optional;
import java.util.UUID;

import org.folio.mr.domain.dto.TitleLevelRequest;

public interface TitleLevelRequestsService {
  Optional<TitleLevelRequest> get(UUID requestId);
}
