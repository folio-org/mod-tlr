package org.folio.service;

import org.folio.domain.dto.Request;

public interface RequestService {
  Request createPrimaryRequest(Request request, String tenantId);
  Request createSecondaryRequest(Request request, String tenantId);
}
