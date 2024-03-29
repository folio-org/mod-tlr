package org.folio.service;

import java.util.Collection;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.Request;

public interface RequestService {
  RequestWrapper createPrimaryRequest(Request request, String borrowingTenantId);

  RequestWrapper createSecondaryRequest(Request request, String borrowingTenantId,
    Collection<String> lendingTenantIds);
}
