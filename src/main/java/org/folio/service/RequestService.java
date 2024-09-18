package org.folio.service;

import java.util.Collection;
import java.util.List;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.InventoryInstance;
import org.folio.domain.dto.InventoryItem;
import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;

public interface RequestService {
  RequestWrapper createPrimaryRequest(Request request, String borrowingTenantId);

  RequestWrapper createSecondaryRequest(Request request, String borrowingTenantId,
    Collection<String> lendingTenantIds);

  CirculationItem createCirculationItem(EcsTlrEntity ecsTlr, Request secondaryRequest,
    String borrowingTenantId, String lendingTenantId);

  CirculationItem updateCirculationItemOnRequestCreation(CirculationItem circulationItem,
    Request secondaryRequest);

  InventoryItem getItemFromStorage(String itemId, String tenantId);

  InventoryInstance getInstanceFromStorage(String instanceId, String tenantId);

  Request getRequestFromStorage(String requestId, String tenantId);
  Request getRequestFromStorage(String requestId);
  Request updateRequestInStorage(Request request, String tenantId);
  List<Request> getRequestsQueueByInstanceId(String instanceId, String tenantId);
  List<Request> getRequestsQueueByInstanceId(String instanceId);
  List<Request> getRequestsQueueByItemId(String itemId);
  List<Request> getRequestsQueueByItemId(String instanceId, String tenantId);
  List<Request> reorderRequestsQueueForInstance(String instanceId, String tenantId, ReorderQueue reorderQueue);
  List<Request> reorderRequestsQueueForItem(String itemId, String tenantId, ReorderQueue reorderQueue);
}
