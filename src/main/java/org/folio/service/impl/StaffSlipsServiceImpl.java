package org.folio.service.impl;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Locale.getISOCountries;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.domain.dto.Request.RequestLevelEnum.TITLE;
import static org.folio.domain.dto.Request.RequestTypeEnum.HOLD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;

import org.folio.domain.dto.AddressType;
import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.HoldingsRecord;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.InstanceContributorsInner;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.MaterialType;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.StaffSlip;
import org.folio.domain.dto.StaffSlipItem;
import org.folio.domain.dto.StaffSlipRequest;
import org.folio.domain.dto.StaffSlipRequester;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserPersonalAddressesInner;
import org.folio.service.AddressTypeService;
import org.folio.service.ConsortiaService;
import org.folio.service.DepartmentService;
import org.folio.service.InventoryService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.service.StaffSlipsService;
import org.folio.service.UserGroupService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.CqlQuery;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class StaffSlipsServiceImpl implements StaffSlipsService {

  private final EnumSet<ItemStatus.NameEnum> relevantItemStatuses;
  private final EnumSet<Request.StatusEnum> relevantRequestStatuses;
  private final EnumSet<Request.RequestTypeEnum> relevantRequestTypes;

  private final LocationService locationService;
  private final InventoryService inventoryService;
  private final RequestService requestService;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService executionService;
  private final UserService userService;
  private final UserGroupService userGroupService;
  private final DepartmentService departmentService;
  private final AddressTypeService addressTypeService;
  private final ServicePointService servicePointService;

  @Override
  public Collection<StaffSlip> getStaffSlips(String servicePointId) {
    log.info("getStaffSlips:: building staff slips for service point {}", servicePointId);
    StaffSlipsContext context = new StaffSlipsContext();
    findLocationsAndItems(servicePointId, context);
    if (context.getLocationsByTenant().isEmpty()) {
      log.info("getStaffSlips:: found no location for service point {}, doing nothing", servicePointId);
      return emptyList();
    }
    findRequests(context);
    if (context.getRequests().isEmpty()) {
      log.info("getStaffSlips:: found no requests to build staff slips for, doing nothing");
      return emptyList();
    }
    discardNonRequestedItems(context);
    findInstances(context);
    findRequesters(context);
    findUserGroups(context);
    findDepartments(context);
    findAddressTypes(context);
    findPickupServicePoints(context);
    fetchDataFromLendingTenants(context);

    Collection<StaffSlip> staffSlips = buildStaffSlips(context);
    log.info("getStaffSlips:: successfully built {} staff slips", staffSlips::size);
    return staffSlips;
  }

  private void findHoldRequestsWithoutItems(StaffSlipsContext context) {
    if (!relevantRequestTypes.contains(HOLD)) {
      log.info("findHoldRequestsWithoutItems:: 'Hold' is not a relevant request type, doing nothing");
      return;
    }

    Collection<Request> holdRequestsWithoutItems = findTitleLevelHoldsWithoutItems();
    Collection<Instance> instances = findInstancesForRequests(holdRequestsWithoutItems);
    Map<String, Collection<HoldingsRecord>> holdings = findHoldingsForHolds(instances, context);

    Set<String> relevantInstanceIds = holdings.values()
      .stream()
      .flatMap(Collection::stream)
      .map(HoldingsRecord::getInstanceId)
      .collect(toSet());

    List<Request> requestsForRelevantInstances = holdRequestsWithoutItems.stream()
      .filter(request -> relevantInstanceIds.contains(request.getInstanceId()))
      .toList();

    log.info("getStaffSlips:: {} of {} hold requests are placed on relevant instances",
      requestsForRelevantInstances::size, holdRequestsWithoutItems::size);

    context.getRequests().addAll(requestsForRelevantInstances);
    context.getInstanceCache().addAll(instances);
  }

  private void findLocationsAndItems(String servicePointId, StaffSlipsContext staffSlipsContext) {
    CqlQuery locationsQuery = CqlQuery.exactMatch("primaryServicePoint", servicePointId);

    getAllConsortiumTenants()
      .forEach(tenantId -> executionService.executeSystemUserScoped(tenantId, () -> {
        log.info("getStaffSlips:: searching for relevant locations and items in tenant {}", tenantId);
        Collection<Location> locations = locationService.findLocations(locationsQuery);
        Map<String, Location> locationsById = toMapById(locations, Location::getId);

        Collection<Item> items = findItems(locations);
        Collection<ItemContext> itemContexts = items.stream()
          .map(item -> new ItemContext(item.getId(), item,
            locationsById.get(item.getEffectiveLocationId())))
          .collect(toList());

        staffSlipsContext.getLocationsByTenant().put(tenantId, locations);
        staffSlipsContext.getItemContextsByTenant().put(tenantId, itemContexts);
        return null;
      }));
  }

  private Collection<String> getAllConsortiumTenants() {
    return consortiaService.getAllConsortiumTenants()
      .stream()
      .map(Tenant::getId)
      .collect(toSet());
  }

  private Collection<Item> findItems(Collection<Location> locations) {
    if (locations.isEmpty()) {
      log.info("findItems:: no locations to search items for, doing nothing");
      return emptyList();
    }

    Set<String> locationIds = locations.stream()
      .map(Location::getId)
      .collect(toSet());

    Set<String> itemStatuses = relevantItemStatuses.stream()
      .map(ItemStatus.NameEnum::getValue)
      .collect(toSet());

    CqlQuery query = CqlQuery.exactMatchAny("status.name", itemStatuses);

    return inventoryService.findItems(query, "effectiveLocationId", locationIds);
  }

  private void findRequests(StaffSlipsContext context) {
    log.info("findRequestsForItems:: searching for requests for relevant items");

    List<String > itemIds = context.getItemContextsByTenant()
      .values()
      .stream()
      .flatMap(Collection::stream)
      .map(ItemContext::getItem)
      .map(Item::getId)
      .toList();

    if (itemIds.isEmpty()) {
      log.info("findRequestsForItems:: no items to search requests for, doing nothing");
      return;
    }

    List<String> requestTypes = relevantRequestTypes.stream()
      .map(Request.RequestTypeEnum::getValue)
      .collect(toList());

    List<String> requestStatuses = relevantRequestStatuses.stream()
      .map(Request.StatusEnum::getValue)
      .collect(toList());

    CqlQuery query = CqlQuery.exactMatchAny("requestType", requestTypes)
      .and(CqlQuery.exactMatchAny("status", requestStatuses));

    Collection<Request> requests = requestService.getRequestsFromStorage(query, "itemId", itemIds);
    context.getRequests().addAll(requests);
    findHoldRequestsWithoutItems(context);
  }

  private Collection<Request> findTitleLevelHoldsWithoutItems() {
    log.info("findHoldRequestsWithoutItem:: searching for open hold requests without itemId");
    List<String> requestStatuses = relevantRequestStatuses.stream()
      .map(Request.StatusEnum::getValue)
      .collect(toList());

    CqlQuery query = CqlQuery.exactMatch("requestType", HOLD.getValue())
      .and(CqlQuery.exactMatch("requestLevel", TITLE.getValue()))
      .and(CqlQuery.exactMatchAny("status", requestStatuses))
      .not(CqlQuery.match("itemId", ""));

    return requestService.getRequestsFromStorage(query);
  }

  private Map<String, Collection<HoldingsRecord>> findHoldingsForHolds(Collection<Instance> instances,
    StaffSlipsContext context) {

    log.info("findHoldingsForHolds:: searching holdings for instances");

    if (instances.isEmpty()) {
      log.info("findHoldingsForHolds:: no instances to search holdings for, doing nothing");
      return emptyMap();
    }

    Set<String> instanceIds = instances.stream()
      .map(Instance::getId)
      .collect(toSet());

    return context.getLocationsByTenant()
      .keySet()
      .stream()
      .collect(toMap(identity(), tenantId -> executionService.executeSystemUserScoped(tenantId,
        () -> findHoldingsForHolds(instanceIds, context, tenantId))));
  }

  private Collection<HoldingsRecord> findHoldingsForHolds(Collection<String> instanceIds,
    StaffSlipsContext context, String tenantId) {

    log.info("findHoldings:: searching holdings for relevant locations and instances");

    Set<String> relevantLocationIds = context.getLocationsByTenant()
      .get(tenantId)
      .stream()
      .map(Location::getId)
      .collect(toSet());

    if (relevantLocationIds.isEmpty()) {
      log.info("findHoldings:: no location to search holdings for, doing nothing");
      return emptyList();
    }

    if (instanceIds.isEmpty()) {
      log.info("findHoldings:: no instances to search holdings for, doing nothing");
      return emptyList();
    }

    Collection<HoldingsRecord> holdingsForInstances = inventoryService.findHoldings(CqlQuery.empty(),
      "instanceId", instanceIds);

    log.info("findHoldingsForHolds:: caching {} holdings", holdingsForInstances::size);
    context.getHoldingsByIdCache().put(tenantId, holdingsForInstances);

    List<HoldingsRecord> holdingsInRelevantLocations = holdingsForInstances.stream()
      .filter(holding -> relevantLocationIds.contains(holding.getEffectiveLocationId()))
      .collect(toList());

    log.info("findHoldings:: {} of {} holdings are in relevant locations",
      holdingsInRelevantLocations::size, holdingsForInstances::size);

    return holdingsInRelevantLocations;
  }

  private void findHoldings(StaffSlipsContext context, String tenantId) {
    log.info("findHoldings:: searching holdings");

    Collection<ItemContext> itemContexts = context.getItemContextsByTenant().get(tenantId);
    Set<String> requestedHoldingIds = itemContexts.stream()
      .map(ItemContext::getItem)
      .map(Item::getHoldingsRecordId)
      .collect(toSet());

    Map<String, HoldingsRecord> cachedHoldingsById = context.getHoldingsByIdCache()
      .getOrDefault(tenantId, new ArrayList<>())
      .stream()
      .collect(mapById(HoldingsRecord::getId));

    Set<String> missingHoldingIds = new HashSet<>(requestedHoldingIds);
    missingHoldingIds.removeAll(cachedHoldingsById.keySet());

    log.info("findHoldings:: cache hit for {} of {} requested holdings",
      requestedHoldingIds.size() - missingHoldingIds.size(), requestedHoldingIds.size());

    Map<String, HoldingsRecord> fetchedHoldingsById = inventoryService.findHoldings(missingHoldingIds)
      .stream()
      .collect(mapById(HoldingsRecord::getId));

    itemContexts.forEach(itemContext -> {
      String holdingsRecordId = itemContext.getItem().getHoldingsRecordId();
      Optional.ofNullable(cachedHoldingsById.get(holdingsRecordId))
        .or(() -> Optional.ofNullable(fetchedHoldingsById.get(holdingsRecordId)))
        .ifPresent(itemContext::setHolding);
    });

    context.getInstanceCache().clear();
  }

  private Collection<Instance> findInstancesForRequests(Collection<Request> requests) {
    log.info("findInstances:: searching instances for requests");
    if (requests.isEmpty()) {
      log.info("findInstances:: no requests to search instances for, doing nothing");
      return emptyList();
    }

    Set<String> instanceIds = requests.stream()
      .map(Request::getInstanceId)
      .collect(toSet());

    return inventoryService.findInstances(instanceIds);
  }

  private void findInstances(StaffSlipsContext context) {
    log.info("findInstances:: searching instances");
    Set<String> requestedInstanceIds = context.getRequests()
      .stream()
      .map(Request::getInstanceId)
      .collect(toSet());

    Map<String, Instance> cachedRequestedInstancesById = context.getInstanceCache()
      .stream()
      .filter(instance -> requestedInstanceIds.contains(instance.getId()))
      .collect(mapById(Instance::getId));

    Set<String> missingInstanceIds = new HashSet<>(requestedInstanceIds);
    missingInstanceIds.removeAll(cachedRequestedInstancesById.keySet());

    log.info("findInstances:: cache hit for {} of {} requested instances",
      requestedInstanceIds.size() - missingInstanceIds.size(), requestedInstanceIds.size());

    Map<String, Instance> fetchedInstancesById = inventoryService.findInstances(missingInstanceIds)
      .stream()
      .collect(mapById(Instance::getId));

    context.getInstancesById().putAll(fetchedInstancesById);
    context.getInstancesById().putAll(cachedRequestedInstancesById);
    context.getInstanceCache().clear();
  }

  private void fetchDataFromLendingTenants(StaffSlipsContext context) {
    context.getItemContextsByTenant()
      .keySet()
      .forEach(tenantId -> executionService.executeSystemUserScoped(tenantId,
        () -> fetchDataFromLendingTenant(context, tenantId)));
  }

  private StaffSlipsContext fetchDataFromLendingTenant(StaffSlipsContext context, String tenantId) {
    log.info("fetchDataFromLendingTenant:: fetching item-related data from tenant {}", tenantId);
    Collection<ItemContext> itemContexts = context.getItemContextsByTenant().get(tenantId);
    findHoldings(context, tenantId);
    findMaterialTypes(itemContexts);
    findLoanTypes(itemContexts);
    findLibraries(itemContexts);
    findCampuses(itemContexts);
    findInstitutions(itemContexts);
    findPrimaryServicePoints(itemContexts);
    return context;
  }

  private void findRequesters(StaffSlipsContext context) {
    if (context.getRequests().isEmpty()) {
      log.info("findRequesters:: no requests to search requesters for, doing nothing");
      return;
    }

    Set<String> requesterIds = context.getRequests().stream()
      .map(Request::getRequesterId)
      .collect(toSet());

    Collection<User> users = userService.find(requesterIds);
    context.getRequestersById().putAll(toMapById(users, User::getId));
  }

  private void findUserGroups(StaffSlipsContext context) {
    if (context.getRequestersById().isEmpty()) {
      log.info("findUserGroups:: no requesters to search user groups for, doing nothing");
      return;
    }

    Set<String> userGroupIds = context.getRequestersById().values()
      .stream()
      .map(User::getPatronGroup)
      .filter(Objects::nonNull)
      .collect(toSet());

    Collection<UserGroup> userGroups = userGroupService.find(userGroupIds);
    context.getUserGroupsById().putAll(toMapById(userGroups, UserGroup::getId));
  }

  private void findDepartments(StaffSlipsContext context) {
    if (context.getRequestersById().isEmpty()) {
      log.info("findDepartments:: no requesters to search departments for, doing nothing");
      return;
    }

    Set<String> departmentIds = context.getRequestersById().values()
      .stream()
      .map(User::getDepartments)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .collect(toSet());

    Collection<Department> departments = departmentService.findDepartments(departmentIds);
    context.getDepartmentsById().putAll(toMapById(departments, Department::getId));
  }

  private void findAddressTypes(StaffSlipsContext context) {
    if (context.getRequestersById().isEmpty()) {
      log.info("findAddressTypes:: no requesters to search address types for, doing nothing");
      return;
    }

    Set<String> addressTypeIds = context.getRequestersById().values()
      .stream()
      .map(User::getPersonal)
      .filter(Objects::nonNull)
      .map(UserPersonal::getAddresses)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .map(UserPersonalAddressesInner::getAddressTypeId)
      .collect(toSet());

    Collection<AddressType> addressTypes = addressTypeService.findAddressTypes(addressTypeIds);
    context.getAddressTypesById().putAll(toMapById(addressTypes, AddressType::getId));
  }

  private void findPickupServicePoints(StaffSlipsContext context) {
    if ( context.getRequests().isEmpty()) {
      log.info("findPickupServicePoints:: no requests to search service points for, doing nothing");
      return;
    }

    Set<String> pickupServicePointIds = context.getRequests()
      .stream()
      .map(Request::getPickupServicePointId)
      .filter(Objects::nonNull)
      .collect(toSet());

    Collection<ServicePoint> pickupServicePoints = findServicePoints(pickupServicePointIds);
    context.getPickupServicePointsById().putAll(toMapById(pickupServicePoints, ServicePoint::getId));
  }

  private Collection<ServicePoint> findServicePoints(Collection<String> servicePointIds) {
    if (servicePointIds.isEmpty()) {
      log.info("findServicePoints:: no IDs to search service points by, doing nothing");
      return emptyList();
    }

    return servicePointService.find(servicePointIds);
  }

  private void findMaterialTypes(Collection<ItemContext> itemContexts) {
    if (itemContexts.isEmpty()) {
      log.info("findMaterialTypes:: no items to search material types for, doing nothing");
      return;
    }

    Map<String, List<ItemContext>> contextsByMaterialTypeId = itemContexts.stream()
      .collect(groupingBy(context -> context.getItem().getMaterialTypeId()));

    inventoryService.findMaterialTypes(contextsByMaterialTypeId.keySet())
      .forEach(materialType -> contextsByMaterialTypeId.get(materialType.getId())
        .forEach(context -> context.setMaterialType(materialType)));
  }

  private void findLoanTypes(Collection<ItemContext> itemContexts) {
    if (itemContexts.isEmpty()) {
      log.info("findLoanTypes:: no items to search loan types for, doing nothing");
      return;
    }

    Map<String, List<ItemContext>> contextsByLoanTypeId = itemContexts.stream()
      .collect(groupingBy(context -> getEffectiveLoanTypeId(context.getItem())));

    inventoryService.findLoanTypes(contextsByLoanTypeId.keySet())
      .forEach(loanType -> contextsByLoanTypeId.get(loanType.getId())
        .forEach(context -> context.setLoanType(loanType)));
  }

  private void findLibraries(Collection<ItemContext> itemContexts) {
    if (itemContexts.isEmpty()) {
      log.info("findLibraries:: no items to search libraries for, doing nothing");
      return;
    }

    Map<String, List<ItemContext>> contextsByLibraryId = itemContexts.stream()
      .collect(groupingBy(context -> context.getLocation().getLibraryId()));

    inventoryService.findLibraries(contextsByLibraryId.keySet())
      .forEach(library -> contextsByLibraryId.get(library.getId())
        .forEach(context -> context.setLibrary(library)));
  }

  private void findCampuses(Collection<ItemContext> itemContexts) {
    if (itemContexts.isEmpty()) {
      log.info("findCampuses:: no items to search campuses for, doing nothing");
      return;
    }

    Map<String, List<ItemContext>> contextsByCampusId = itemContexts.stream()
      .collect(groupingBy(context -> context.getLocation().getCampusId()));

    inventoryService.findCampuses(contextsByCampusId.keySet())
      .forEach(campus -> contextsByCampusId.get(campus.getId())
        .forEach(context -> context.setCampus(campus)));
  }

  private void findInstitutions(Collection<ItemContext> itemContexts) {
    if (itemContexts.isEmpty()) {
      log.info("findInstitutions:: no items to search institutions for, doing nothing");
      return;
    }

    Map<String, List<ItemContext>> contextsByInstitutionId = itemContexts.stream()
      .collect(groupingBy(context -> context.getLocation().getInstitutionId()));

    inventoryService.findInstitutions(contextsByInstitutionId.keySet())
      .forEach(institution -> contextsByInstitutionId.get(institution.getId())
        .forEach(context -> context.setInstitution(institution)));
  }

  private void findPrimaryServicePoints(Collection<ItemContext> itemContexts) {
    if (itemContexts.isEmpty()) {
      log.info("findPrimaryServicePoints:: no items to search institutions for, doing nothing");
      return;
    }

    Map<String, List<ItemContext>> contextsByPrimaryServicePointId = itemContexts.stream()
      .collect(groupingBy(context -> context.getLocation().getPrimaryServicePoint().toString()));

    findServicePoints(contextsByPrimaryServicePointId.keySet())
      .forEach(servicePoint -> contextsByPrimaryServicePointId.get(servicePoint.getId())
        .forEach(context -> context.setPrimaryServicePoint(servicePoint)));
  }


  private static Collection<StaffSlip> buildStaffSlips(StaffSlipsContext context) {
    return context.getRequests()
      .stream()
      .map(request -> buildStaffSlip(request, context))
      .toList();
  }

  private static StaffSlip buildStaffSlip(Request request, StaffSlipsContext context) {
    log.info("buildStaffSlip:: building staff slip for request {}", request.getId());

    return new StaffSlip()
      .currentDateTime(new Date())
      .item(buildStaffSlipItem(request, context))
      .request(buildStaffSlipRequest(request, context))
      .requester(buildStaffSlipRequester(request, context));
  }

  private static StaffSlipItem buildStaffSlipItem(Request request, StaffSlipsContext context) {
    log.debug("buildStaffSlipItem:: building staff slip item");
    Instance instance = context.getInstancesById().get(request.getInstanceId());
    StaffSlipItem staffSlipItem = new StaffSlipItem();
    if (instance != null) {
      staffSlipItem.title(instance.getTitle());
      List<InstanceContributorsInner> contributors = instance.getContributors();
      if (contributors != null && !contributors.isEmpty()) {
        String primaryContributor = contributors.stream()
                .filter(InstanceContributorsInner::getPrimary)
                .findFirst()
                .map(InstanceContributorsInner::getName)
                .orElse(null);

        String allContributors = contributors.stream()
                .map(InstanceContributorsInner::getName)
                .collect(joining("; "));

        staffSlipItem
                .primaryContributor(primaryContributor)
                .allContributors(allContributors);
      }
    }

    String itemId = request.getItemId();
    if (itemId == null) {
      log.info("buildStaffSlipItem:: request is not linked to an item, return");
      return staffSlipItem;
    }

    ItemContext itemContext = context.getItemContextsByTenant()
      .values()
      .stream()
      .flatMap(Collection::stream)
      .filter(ctx -> itemId.equals(ctx.getItemId()))
      .findFirst()
      .orElse(null);

    if (itemContext == null) {
      log.warn("buildStaffSlipItem:: item context for request {} was not found, doing nothing",
        request.getId());
      return null;
    }

    Item item = itemContext.getItem();

    String yearCaptions = Optional.ofNullable(item.getYearCaption())
      .map(captions -> String.join("; ", captions))
      .orElse(null);

    String copyNumber = Optional.ofNullable(item.getCopyNumber())
      .or(() -> Optional.ofNullable(itemContext.getHolding())
        .map(HoldingsRecord::getCopyNumber))
      .orElse("");

    String materialType = Optional.ofNullable(itemContext.getMaterialType())
      .map(MaterialType::getName)
      .orElse(null);

    String loanType = Optional.ofNullable(itemContext.getLoanType())
      .map(LoanType::getName)
      .orElse(null);

    staffSlipItem.barcode(item.getBarcode())
      .status(item.getStatus().getName().getValue())
      .materialType(materialType)
      .loanType(loanType)
      .enumeration(item.getEnumeration())
      .volume(item.getVolume())
      .chronology(item.getChronology())
      .yearCaption(yearCaptions)
      .copy(copyNumber)
      .numberOfPieces(item.getNumberOfPieces())
      .displaySummary(item.getDisplaySummary())
      .descriptionOfPieces(item.getDescriptionOfPieces());

    Location location = itemContext.getLocation();
    if (location != null) {
      staffSlipItem
        .effectiveLocationSpecific(location.getName())
        .effectiveLocationDiscoveryDisplayName(location.getDiscoveryDisplayName());

      Optional.ofNullable(itemContext.getLibrary())
        .map(Library::getName)
        .ifPresent(staffSlipItem::effectiveLocationLibrary);

      Optional.ofNullable(itemContext.getCampus())
        .map(Campus::getName)
        .ifPresent(staffSlipItem::effectiveLocationCampus);

      Optional.ofNullable(itemContext.getInstitution())
        .map(Institution::getName)
        .ifPresent(staffSlipItem::effectiveLocationInstitution);

      Optional.ofNullable(itemContext.getPrimaryServicePoint())
        .map(ServicePoint::getName)
        .ifPresent(staffSlipItem::effectiveLocationPrimaryServicePointName);
    }

    ItemEffectiveCallNumberComponents callNumberComponents = item.getEffectiveCallNumberComponents();
    if (callNumberComponents != null) {
      staffSlipItem.callNumber(callNumberComponents.getCallNumber())
        .callNumberPrefix(callNumberComponents.getPrefix())
        .callNumberSuffix(callNumberComponents.getSuffix());
    }

    return staffSlipItem;
  }

  private static StaffSlipRequest buildStaffSlipRequest(Request request, StaffSlipsContext context) {
    log.debug("buildStaffSlipItem:: building staff slip request");
    if (request == null) {
      log.warn("buildStaffSlipRequest:: request is null, doing nothing");
      return null;
    }

    String deliveryAddressType = Optional.ofNullable(request.getDeliveryAddressTypeId())
      .map(context.getAddressTypesById()::get)
      .map(AddressType::getAddressType)
      .orElse(null);

    String pickupServicePoint = Optional.ofNullable(request.getPickupServicePointId())
      .map(context.getPickupServicePointsById()::get)
      .map(ServicePoint::getName)
      .orElse(null);

    return new StaffSlipRequest()
      .requestID(UUID.fromString(request.getId()))
      .servicePointPickup(pickupServicePoint)
      .requestDate(request.getRequestDate())
      .requestExpirationDate(request.getRequestExpirationDate())
      .holdShelfExpirationDate(request.getHoldShelfExpirationDate())
      .additionalInfo(request.getCancellationAdditionalInformation())
      .deliveryAddressType(deliveryAddressType)
      .patronComments(request.getPatronComments());
  }

  private static StaffSlipRequester buildStaffSlipRequester(Request request, StaffSlipsContext context) {
    log.debug("buildStaffSlipItem:: building staff slip requester");
    User requester = context.getRequestersById().get(request.getRequesterId());
    if (requester == null) {
      log.warn("buildStaffSlipRequester:: requester is null, doing nothing");
      return null;
    }

    String departments = requester.getDepartments()
      .stream()
      .filter(Objects::nonNull)
      .map(context.getDepartmentsById()::get)
      .filter(Objects::nonNull)
      .map(Department::getName)
      .collect(joining("; "));

    String patronGroup = Optional.ofNullable(context.getUserGroupsById().get(requester.getPatronGroup()))
      .map(UserGroup::getGroup)
      .orElse("");

    StaffSlipRequester staffSlipRequester = new StaffSlipRequester()
      .barcode(requester.getBarcode())
      .patronGroup(patronGroup)
      .departments(departments);

    UserPersonal personal = requester.getPersonal();
    if (personal != null) {
      String preferredFirstName = Optional.ofNullable(personal.getPreferredFirstName())
        .orElseGet(personal::getFirstName);

      staffSlipRequester
        .firstName(personal.getFirstName())
        .preferredFirstName(preferredFirstName)
        .lastName(personal.getLastName())
        .middleName(personal.getMiddleName());

      List<UserPersonalAddressesInner> addresses = personal.getAddresses();
      if (addresses != null) {
        addresses.stream()
          .filter(address -> TRUE.equals(address.getPrimaryAddress()))
          .findFirst()
          .ifPresent(primaryAddress -> staffSlipRequester
            .primaryAddressLine1(primaryAddress.getAddressLine1())
            .primaryAddressLine2(primaryAddress.getAddressLine2())
            .primaryCity(primaryAddress.getCity())
            .primaryStateProvRegion(primaryAddress.getRegion())
            .primaryZipPostalCode(primaryAddress.getPostalCode())
            .primaryCountry(getCountryName(primaryAddress.getCountryId()))
            .primaryDeliveryAddressType(
              Optional.ofNullable(context.getAddressTypesById().get(primaryAddress.getAddressTypeId()))
                .map(AddressType::getAddressType)
                .orElse(null)
            ));

        String deliveryAddressTypeId = request.getDeliveryAddressTypeId();
        if (deliveryAddressTypeId != null) {
          addresses.stream()
            .filter(address -> deliveryAddressTypeId.equals(address.getAddressTypeId()))
            .findFirst()
            .ifPresent(deliveryAddress -> staffSlipRequester
              .addressLine1(deliveryAddress.getAddressLine1())
              .addressLine2(deliveryAddress.getAddressLine2())
              .city(deliveryAddress.getCity())
              .region(deliveryAddress.getRegion())
              .postalCode(deliveryAddress.getPostalCode())
              .countryId(deliveryAddress.getCountryId())
              .addressType(
                Optional.ofNullable(context.getAddressTypesById().get(deliveryAddressTypeId))
                .map(AddressType::getAddressType)
                .orElse(null)
              ));
        }
      }
    }

    return  staffSlipRequester;
  }

  private static <T> Map<String, T> toMapById(Collection<T> collection, Function<T, String> idExtractor) {
    return collection.stream()
      .collect(mapById(idExtractor));
  }

  private static <T> Collector<T, ?, Map<String, T>> mapById(Function<T, String> idExtractor) {
    return toMap(idExtractor, identity());
  }

  private static String getCountryName(String countryCode) {
    if (isBlank(countryCode) || !Arrays.asList(getISOCountries()).contains(countryCode)) {
      log.warn("getCountryName:: unknown country code: {}", countryCode);
      return null;
    }

    return new Locale("", countryCode).getDisplayName();
  }

  private static String getEffectiveLoanTypeId(Item item) {
    return firstNonBlank(item.getTemporaryLoanTypeId(), item.getPermanentLoanTypeId());
  }

  private static void discardNonRequestedItems(StaffSlipsContext context) {
    log.info("discardNonRequestedItems:: discarding non-requested items");

    Set<String> requestedItemIds = context.getRequests()
      .stream()
      .map(Request::getItemId)
      .filter(Objects::nonNull)
      .collect(toSet());

    context.getItemContextsByTenant()
      .values()
      .forEach(itemContexts -> itemContexts.removeIf(
        itemContext -> !requestedItemIds.contains(itemContext.getItemId())));

    context.getItemContextsByTenant()
      .entrySet()
      .removeIf(entry -> entry.getValue().isEmpty());
  }

  @Getter
  private static class StaffSlipsContext {
    private final Collection<Request> requests = new ArrayList<>();
    private final Map<String, Instance> instancesById = new HashMap<>();
    private final Map<String, User> requestersById = new HashMap<>();
    private final Map<String, UserGroup> userGroupsById = new HashMap<>();
    private final Map<String, Department> departmentsById = new HashMap<>();
    private final Map<String, AddressType> addressTypesById = new HashMap<>();
    private final Map<String, ServicePoint> pickupServicePointsById = new HashMap<>();
    private final Map<String, Collection<ItemContext>> itemContextsByTenant = new HashMap<>();
    private final Map<String, Collection<Location>> locationsByTenant = new HashMap<>();
    private final Map<String, Collection<HoldingsRecord>> holdingsByIdCache = new HashMap<>();
    private final Collection<Instance> instanceCache = new ArrayList<>();
  }

  @RequiredArgsConstructor
  @Getter
  @Setter
  private static class ItemContext {
    private final String itemId;
    private final Item item;
    private final Location location;
    private HoldingsRecord holding;
    private MaterialType materialType;
    private LoanType loanType;
    private Library library;
    private Campus campus;
    private Institution institution;
    private ServicePoint primaryServicePoint;
  }

}
