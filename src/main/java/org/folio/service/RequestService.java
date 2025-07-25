package org.folio.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.Request;
import org.folio.support.CqlQuery;

public interface RequestService {
  RequestWrapper createPrimaryRequest(Request request, String primaryRequestTenantId,
    String secondaryRequestTenantId);

  RequestWrapper createSecondaryRequest(Request request, String primaryRequestTenantId,
    Collection<String> secondaryRequestTenantIds);

  RequestWrapper createIntermediateRequest(Request intermediateRequest,
    String primaryRequestTenantId, String intermediateRequestTenantId,
    String secondaryRequestTenantId);

  CirculationItem updateCirculationItemOnRequestCreation(CirculationItem circulationItem,
    Request secondaryRequest);

  Item getItemFromStorage(String itemId, String tenantId);
  Instance getInstanceFromStorage(String instanceId, String tenantId);
  Request getRequestFromStorage(String requestId, String tenantId);
  Request getRequestFromStorage(String requestId);
  Collection<Request> getRequestsFromStorage(CqlQuery query, String idIndex, Collection<String> ids);
  Collection<Request> getRequestsFromStorage(CqlQuery query);
  Request updateRequestInStorage(Request request, String tenantId);
  List<Request> getRequestsQueueByInstanceId(String instanceId, String tenantId);
  List<Request> getRequestsQueueByInstanceId(String instanceId);
  List<Request> getRequestsQueueByItemId(String itemId);
  List<Request> getRequestsQueueByItemId(String instanceId, String tenantId);
  List<Request> reorderRequestsQueueForInstance(String instanceId, String tenantId, ReorderQueue reorderQueue);
  List<Request> reorderRequestsQueueForItem(String itemId, String tenantId, ReorderQueue reorderQueue);
  Optional<Request> findEcsRequestForLoan(Loan loan);
}
