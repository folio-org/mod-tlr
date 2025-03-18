package org.folio.service.impl;

import static java.lang.String.format;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.folio.client.feign.CirculationClient;
import org.folio.client.feign.CirculationItemClient;
import org.folio.client.feign.InstanceClient;
import org.folio.client.feign.ItemClient;
import org.folio.client.feign.RequestCirculationClient;
import org.folio.client.feign.RequestStorageClient;
import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.CirculationItemStatus;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.ReorderQueue;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.User;
import org.folio.exception.RequestCreatingException;
import org.folio.service.CloningService;
import org.folio.service.ConsortiaService;
import org.folio.service.ConsortiumService;
import org.folio.service.InventoryService;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.BulkFetcher;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestServiceImpl implements RequestService {

  private final SystemUserScopedExecutionService executionService;
  private final CirculationClient circulationClient;
  private final CirculationItemClient circulationItemClient;
  private final ItemClient itemClient;
  private final InstanceClient instanceClient;
  private final RequestCirculationClient requestCirculationClient;
  private final RequestStorageClient requestStorageClient;
  private final UserService userService;
  private final ServicePointService servicePointService;
  private final CloningService<User> userCloningService;
  private final CloningService<ServicePoint> servicePointCloningService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final ConsortiaService consortiaService;
  private final ConsortiumService consortiumService;
  private final InventoryService inventoryService;

  public static final String HOLDINGS_RECORD_ID = "10cd3a5a-d36f-4c7a-bc4f-e1ae3cf820c9";

  @Override
  public RequestWrapper createPrimaryRequest(Request primaryRequest,
    String primaryRequestTenantId, String secondaryRequestTenantId) {

    final String requestId = primaryRequest.getId();
    log.info("createPrimaryRequest:: creating primary request {} in tenant {}", requestId,
      primaryRequestTenantId);

    createShadowInstance(primaryRequest.getInstanceId(), primaryRequestTenantId);

    return executionService.executeSystemUserScoped(primaryRequestTenantId, () -> {
      CirculationItem circItem = createCirculationItem(primaryRequest, secondaryRequestTenantId);
      Request request = circulationClient.createRequest(primaryRequest);
      log.info("createPrimaryRequest:: primary request {} created in tenant {}",
        requestId, primaryRequestTenantId);
      log.debug("createPrimaryRequest:: primary request: {}", () -> request);
      updateCirculationItemOnRequestCreation(circItem, request);
      return new RequestWrapper(request, primaryRequestTenantId);
    });
  }

  private void createShadowInstance(String instanceId, String targetTenantId) {
    log.info("createShadowInstance:: checking if instance must be shared with primary request tenant");
    if (consortiumService.isCentralTenant(targetTenantId)) {
      log.info("createShadowInstance:: tenant {} is central tenant, doing nothing", targetTenantId);
      return;
    }

    systemUserScopedExecutionService.executeSystemUserScoped(targetTenantId, () -> {
      if (inventoryService.findInstance(instanceId).isPresent()) {
        log.info("createShadowInstance:: instance {} already exists in tenant {}, doing nothing",
          instanceId, targetTenantId);
      } else {
        consortiaService.shareInstance(instanceId, targetTenantId);
      }
      return instanceId;
    });
  }

  @Override
  public RequestWrapper createSecondaryRequest(Request request, String primaryRequestTenantId,
    Collection<String> secondaryRequestTenantIds) {

    final String requestId = request.getId();
    final String requesterId = request.getRequesterId();
    final String pickupServicePointId = request.getPickupServicePointId();

    log.info("createSecondaryRequest:: creating secondary request {} in one of potential " +
      "tenants: {}", requestId, secondaryRequestTenantIds);

    User primaryRequestRequester = executionService.executeSystemUserScoped(primaryRequestTenantId,
      () -> userService.find(requesterId));
    ServicePoint primaryRequestPickupServicePoint = executionService.executeSystemUserScoped(
      primaryRequestTenantId, () -> servicePointService.find(pickupServicePointId));

    for (String secondaryRequestTenantId : secondaryRequestTenantIds) {
      try {
        return executionService.executeSystemUserScoped(secondaryRequestTenantId, () -> {
          log.info("createSecondaryRequest:: creating requester {} in tenant {}",
            requesterId, secondaryRequestTenantId);
          cloneRequester(primaryRequestRequester);

          log.info("createSecondaryRequest:: creating pickup service point {} in tenant {}",
            pickupServicePointId, secondaryRequestTenantId);
          servicePointCloningService.clone(primaryRequestPickupServicePoint);

          log.info("createSecondaryRequest:: creating secondary request {} in tenant {}",
            requestId, secondaryRequestTenantId);
          Request secondaryRequest = circulationClient.createRequest(request);
          log.info("createSecondaryRequest:: secondary request {} created in tenant {}",
            secondaryRequest.getId(), secondaryRequestTenantId);
          log.debug("createSecondaryRequest:: secondary request: {}", () -> secondaryRequest);

          return new RequestWrapper(secondaryRequest, secondaryRequestTenantId);
        });
      } catch (Exception e) {
        log.error("createSecondaryRequest:: failed to create secondary request in tenant {}: {}",
          secondaryRequestTenantId, e.getMessage());
        log.debug("createSecondaryRequest:: ", e);
      }
    }

    String errorMessage = format(
      "Failed to create secondary request for instance %s in all potential tenants: %s",
      request.getInstanceId(), secondaryRequestTenantIds);
    log.error("createSecondaryRequest:: {}", errorMessage);
    throw new RequestCreatingException(errorMessage);
  }

  @Override
  public RequestWrapper createIntermediateRequest(Request intermediateRequest,
    String primaryRequestTenantId, String intermediateRequestTenantId,
    String secondaryRequestTenantId) {

    log.info("createIntermediateRequest:: creating intermediate request in tenant {}, instance {}," +
        " item {}, requester {}", intermediateRequestTenantId, intermediateRequest.getInstanceId(),
      intermediateRequest.getItemId(), intermediateRequest.getRequesterId());

    try {
      final String requesterId = intermediateRequest.getRequesterId();
      final String pickupServicePointId = intermediateRequest.getPickupServicePointId();

      User primaryRequestRequester = executionService.executeSystemUserScoped(primaryRequestTenantId,
        () -> userService.find(requesterId));
      ServicePoint primaryRequestPickupServicePoint = executionService.executeSystemUserScoped(
        primaryRequestTenantId, () -> servicePointService.find(pickupServicePointId));

      log.info("createIntermediateRequest:: creating requester {} in tenant {}",
        requesterId, intermediateRequestTenantId);
      cloneRequester(primaryRequestRequester);

      log.info("createIntermediateRequest:: creating pickup service point {} in tenant {}",
        pickupServicePointId, intermediateRequestTenantId);
      servicePointCloningService.clone(primaryRequestPickupServicePoint);

      CirculationItem circItem = createCirculationItem(intermediateRequest, secondaryRequestTenantId);

      log.info("createIntermediateRequest:: creating intermediate request in tenant {}",
        intermediateRequestTenantId);
      Request request = circulationClient.createRequest(intermediateRequest);
      log.info("createIntermediateRequest:: intermediate request {} created in tenant {}",
        request.getId(), intermediateRequestTenantId);

      updateCirculationItemOnRequestCreation(circItem, request);

      return new RequestWrapper(request, intermediateRequestTenantId);
    } catch (Exception e) {
      log.error("createIntermediateRequest:: failed to create intermediate request in tenant {}: {}",
        intermediateRequestTenantId, e.getMessage());
      log.debug("createIntermediateRequest:: ", e);
    }

    String errorMessage = format(
      "Failed to create intermediate request for instance %s, item %s, requester %s in tenant %s",
      intermediateRequest.getInstanceId(), intermediateRequest.getItemId(), intermediateRequest.getRequesterId(), intermediateRequestTenantId);
    log.error("createIntermediateRequest:: {}", errorMessage);
    throw new RequestCreatingException(errorMessage);
  }

  public CirculationItem createCirculationItem(Request request, String inventoryTenantId) {
    if (request == null) {
      log.warn("createCirculationItem:: request is null, skipping");
      return null;
    }
    if (inventoryTenantId == null) {
      log.warn("createCirculationItem:: inventory tenant ID is null, skipping");
      return null;
    }

    String itemId = request.getItemId();
    String instanceId = request.getInstanceId();
    String pickupLocation = request.getPickupServicePointId();

    log.info("createCirculationItem:: creating circulation item, params: itemId={}, instanceId={}, " +
      "pickupLocation={}, inventoryTenantId={}", itemId, instanceId, pickupLocation, inventoryTenantId);

    if (itemId == null || instanceId == null) {
      log.info("createCirculationItem:: item ID is {}, instance ID is {}, skipping", itemId, instanceId);
      return null;
    }

    var item = getItemFromStorage(itemId, inventoryTenantId);
    var itemStatus = item.getStatus().getName();
    var circulationItemStatus = defineCirculationItemStatus(itemStatus, request.getRequestType());
    log.info("createCirculationItem:: item status {}, calculated status: {}",
      itemStatus, circulationItemStatus);

    // Check if circulation item already exists in the tenant we want to create it in
    CirculationItem existingCirculationItem = circulationItemClient.getCirculationItem(itemId);
    if (existingCirculationItem != null) {
      var existingStatus = existingCirculationItem.getStatus() == null
        ? null
        : existingCirculationItem.getStatus().getName();
      log.info("createCirculationItem:: circulation item already exists in status {}",
        existingStatus);

      if (existingStatus == circulationItemStatus) {
        return existingCirculationItem;
      }
      log.info("createCirculationItem:: updating circulation item status to {}", circulationItemStatus);
      existingCirculationItem.setStatus(new CirculationItemStatus()
        .name(circulationItemStatus)
        .date(item.getStatus().getDate())
      );
      return circulationItemClient.updateCirculationItem(itemId, existingCirculationItem);
    }

    Instance instance = getInstanceFromStorage(instanceId, inventoryTenantId);
    var circulationItem = new CirculationItem()
      .id(UUID.fromString(itemId))
      .holdingsRecordId(UUID.fromString(HOLDINGS_RECORD_ID))
      .status(new CirculationItemStatus()
        .name(circulationItemStatus)
        .date(item.getStatus().getDate())
      )
      .dcbItem(true)
      .materialTypeId(item.getMaterialTypeId())
      .permanentLoanTypeId(item.getPermanentLoanTypeId())
      .instanceTitle(instance.getTitle())
      .barcode(item.getBarcode())
      .pickupLocation(pickupLocation)
      .effectiveLocationId(item.getEffectiveLocationId())
      .lendingLibraryCode("TEST_CODE");

    log.info("createCirculationItem:: creating circulation item {}", itemId);
    return circulationItemClient.createCirculationItem(itemId, circulationItem);
  }

  private CirculationItemStatus.NameEnum defineCirculationItemStatus(
    ItemStatus.NameEnum itemStatus, Request.RequestTypeEnum requestType) {

    return itemStatus == ItemStatus.NameEnum.PAGED && requestType == Request.RequestTypeEnum.PAGE
      ? CirculationItemStatus.NameEnum.AVAILABLE
      : CirculationItemStatus.NameEnum.fromValue(itemStatus.getValue());
  }

  @Override
  public CirculationItem updateCirculationItemOnRequestCreation(CirculationItem circulationItem,
    Request request) {

    if (circulationItem == null) {
      log.info("updateCirculationItemOnRequestCreation:: circulation item is null, skipping");
      return null;
    }
    log.info("updateCirculationItemOnRequestCreation:: updating circulation item {}",
      circulationItem.getId());

    if (request.getRequestType() == Request.RequestTypeEnum.PAGE) {
      log.info("updateCirculationItemOnRequestCreation:: request {} type is 'Page', " +
        "updating circulation item {} with status 'Paged'", request.getId(), circulationItem.getId());
      circulationItem.getStatus().setName(CirculationItemStatus.NameEnum.PAGED);
      circulationItemClient.updateCirculationItem(circulationItem.getId().toString(),
        circulationItem);
    }
    return circulationItem;
  }

  @Override
  public Item getItemFromStorage(String itemId, String tenantId) {
    log.info("getItemFromStorage:: Fetching item {} from tenant {}", itemId, tenantId);
    return systemUserScopedExecutionService.executeSystemUserScoped(tenantId,
      () -> itemClient.get(itemId));
  }

  @Override
  public Instance getInstanceFromStorage(String instanceId, String tenantId) {
    log.info("getInstanceFromStorage:: Fetching instance {} from tenant {}", instanceId, tenantId);
    return systemUserScopedExecutionService.executeSystemUserScoped(tenantId,
      () -> instanceClient.get(instanceId));
  }

  @Override
  public Request getRequestFromStorage(String requestId, String tenantId) {
    log.info("getRequestFromStorage:: getting request {} from storage in tenant {}", requestId, tenantId);
    return executionService.executeSystemUserScoped(tenantId, () -> getRequestFromStorage(requestId));
  }

  @Override
  public Request getRequestFromStorage(String requestId) {
    log.info("getRequestFromStorage:: getting request {} from storage", requestId);
    return requestStorageClient.getRequest(requestId);
  }

  @Override
  public Collection<Request> getRequestsFromStorage(CqlQuery query, String idIndex,
    Collection<String> ids) {

    log.info("getRequestsFromStorage:: searching requests by query and index: query={}, index={}, ids={}",
     query, idIndex, ids.size());
    log.debug("getRequestsFromStorage:: ids={}", ids);
    return BulkFetcher.fetch(requestStorageClient, query, idIndex, ids, Requests::getRequests);
  }

  @Override
  public Collection<Request> getRequestsFromStorage(CqlQuery query) {
    log.info("getRequestsFromStorage:: searching requests by query: {}", query);
    Collection<Request> requests = requestStorageClient.getByQuery(query).getRequests();
    log.info("getRequestsFromStorage:: found {} requests", requests::size);
    return requests;
  }

  @Override
  public Request updateRequestInStorage(Request request, String tenantId) {
    log.info("updateRequestInStorage:: updating request {} in storage in tenant {}", request::getId,
      () -> tenantId);
    log.debug("updateRequestInStorage:: {}", request);

    return executionService.executeSystemUserScoped(tenantId,
      () -> requestStorageClient.updateRequest(request.getId(), request));
  }

  @Override
  public List<Request> getRequestsQueueByInstanceId(String instanceId, String tenantId) {
    log.info("getRequestsQueueByInstanceId:: parameters instanceId: {}, tenantId: {}",
      instanceId, tenantId);

    return executionService.executeSystemUserScoped(tenantId,
      () -> requestCirculationClient.getRequestsQueueByInstanceId(instanceId).getRequests());
  }

  @Override
  public List<Request> getRequestsQueueByInstanceId(String instanceId) {
    log.info("getRequestsQueueByInstanceId:: parameters instanceId: {}", instanceId);

    return requestCirculationClient.getRequestsQueueByInstanceId(instanceId).getRequests();
  }

  @Override
  public List<Request> getRequestsQueueByItemId(String itemId) {
    log.info("getRequestsQueueByItemId:: parameters itemId: {}", itemId);

    return requestCirculationClient.getRequestsQueueByItemId(itemId).getRequests();
  }

  @Override
  public List<Request> getRequestsQueueByItemId(String itemId, String tenantId) {
    log.info("getRequestsQueueByItemId:: parameters itemId: {}, tenantId: {}",
      itemId, tenantId);

    return executionService.executeSystemUserScoped(tenantId,
      () -> requestCirculationClient.getRequestsQueueByItemId(itemId).getRequests());
  }

  @Override
  public List<Request> reorderRequestsQueueForInstance(String instanceId, String tenantId,
    ReorderQueue reorderQueue) {

    log.info("reorderRequestsQueueForInstance:: parameters instanceId: {}, tenantId: {}, " +
        "reorderQueue: {}", instanceId, tenantId, reorderQueue);

    return executionService.executeSystemUserScoped(tenantId,
      () -> requestCirculationClient.reorderRequestsQueueForInstanceId(instanceId, reorderQueue)
        .getRequests());
  }

  @Override
  public List<Request> reorderRequestsQueueForItem(String itemId, String tenantId,
    ReorderQueue reorderQueue) {

    log.info("reorderRequestsQueueForItem:: parameters itemId: {}, tenantId: {}, " +
      "reorderQueue: {}", itemId, tenantId, reorderQueue);

    return executionService.executeSystemUserScoped(tenantId,
      () -> requestCirculationClient.reorderRequestsQueueForItemId(itemId, reorderQueue)
        .getRequests());
  }

  private void cloneRequester(User primaryRequestRequester) {
    User requesterClone = userCloningService.clone(primaryRequestRequester);
    String patronGroup = primaryRequestRequester.getPatronGroup();

    if (patronGroup != null && !patronGroup.equals(requesterClone.getPatronGroup())) {
      log.info("cloneRequester:: updating requester's ({}) patron group in lending tenant to {}",
        requesterClone.getId(), patronGroup);
      requesterClone.setPatronGroup(patronGroup);
      userService.update(requesterClone);
    }
  }
}
