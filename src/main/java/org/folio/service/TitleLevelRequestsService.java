package org.folio.service;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.TitleLevelRequest;

public interface TitleLevelRequestsService {
  Optional<TitleLevelRequest> get(UUID requestId);
}
