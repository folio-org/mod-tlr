package org.folio.service;

import java.util.Collection;
import java.util.List;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.Request;

public interface RequestService {
  RequestWrapper createPrimaryRequest(Request request, String borrowingTenantId);

  RequestWrapper createSecondaryRequest(Request request, String borrowingTenantId,
    Collection<String> lendingTenantIds);

  Request getRequestFromStorage(String requestId, String tenantId);
  Request getRequestFromStorage(String requestId);
  Request updateRequestInStorage(Request request, String tenantId);
  List<Request> getRequestsByInstanceId(String instanceId);
}
