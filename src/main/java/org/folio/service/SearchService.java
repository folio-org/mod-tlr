package org.folio.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchService {
  Optional<List<String>> getTenantsByInstanceId(UUID instanceId);
}
