package org.folio.service.impl;

import static java.util.Collections.emptyList;
import static java.util.Locale.getISOCountries;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.folio.domain.dto.Contributor;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.MaterialType;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.SearchHolding;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchItem;
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
import org.folio.service.SearchService;
import org.folio.service.ServicePointService;
import org.folio.service.StaffSlipsService;
import org.folio.service.UserGroupService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.CqlQuery;

import lombok.RequiredArgsConstructor;
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
  private final SearchService searchService;
  private final ServicePointService servicePointService;

  @Override
  public Collection<StaffSlip> getStaffSlips(String servicePointId) {
    log.info("getStaffSlips:: building staff slips for service point {}", servicePointId);

    Map<String, Collection<Location>> locationsByTenant = findLocations(servicePointId);
    Collection<String> locationIds = locationsByTenant.values()
      .stream()
      .flatMap(Collection::stream)
      .map(Location::getId)
      .collect(toSet());

    Collection<SearchInstance> instances = findInstances(locationIds);
    Collection<SearchItem> itemsInRelevantLocations = getItemsForLocations(instances, locationIds);
    Collection<Request> requests = findRequests(itemsInRelevantLocations);
    Collection<SearchItem> requestedItems = filterRequestedItems(itemsInRelevantLocations, requests);
    Collection<StaffSlipContext> staffSlipContexts = buildStaffSlipContexts(requests, requestedItems,
      instances, locationsByTenant);
    Collection<StaffSlip> staffSlips = buildStaffSlips(staffSlipContexts);

    log.info("getStaffSlips:: successfully built {} staff slips", staffSlips::size);
    return staffSlips;
  }

  private Map<String, Collection<Location>> findLocations(String servicePointId) {
    log.info("findLocations:: searching for locations in all consortium tenants");
    CqlQuery query = CqlQuery.exactMatch("primaryServicePoint", servicePointId);

    return getAllConsortiumTenants()
      .stream()
      .collect(toMap(identity(), tenantId -> findLocations(query, tenantId)));
  }

  private Collection<String> getAllConsortiumTenants() {
    return consortiaService.getAllConsortiumTenants()
      .stream()
      .map(Tenant::getId)
      .collect(toSet());
  }

  private Collection<Location> findLocations(CqlQuery query, String tenantId) {
    log.info("findLocations:: searching for locations in tenant {} by query: {}", tenantId, query);
    return executionService.executeSystemUserScoped(tenantId, () -> locationService.findLocations(query));
  }

  private Collection<SearchInstance> findInstances(Collection<String> locationIds) {
    log.info("findInstances:: searching for instances");
    if (locationIds.isEmpty()) {
      log.info("findItems:: no locations to search instances for, doing nothing");
      return emptyList();
    }

    List<String> itemStatusStrings = relevantItemStatuses.stream()
      .map(ItemStatus.NameEnum::getValue)
      .toList();

    CqlQuery query = CqlQuery.exactMatchAny("item.status.name", itemStatusStrings);

    return searchService.searchInstances(query, "item.effectiveLocationId", locationIds);
  }

  private static Collection<SearchItem> getItemsForLocations(Collection<SearchInstance> instances,
    Collection<String> locationIds) {

    log.info("getItemsForLocations:: searching for items in relevant locations");
    List<SearchItem> items = instances.stream()
      .map(SearchInstance::getItems)
      .flatMap(Collection::stream)
      .filter(item -> locationIds.contains(item.getEffectiveLocationId()))
      .toList();

    log.info("getItemsForLocations:: found {} items in relevant locations", items::size);
    return items;
  }

  private Collection<Request> findRequests(Collection<SearchItem> items) {
    log.info("findRequests:: searching for requests for relevant items");
    if (items.isEmpty()) {
      log.info("findRequests:: no items to search requests for, doing nothing");
      return emptyList();
    }

    Set<String> itemIds = items.stream()
      .map(SearchItem::getId)
      .collect(toSet());

    List<String> requestTypes = relevantRequestTypes.stream()
      .map(Request.RequestTypeEnum::getValue)
      .toList();

    List<String> requestStatuses = relevantRequestStatuses.stream()
      .map(Request.StatusEnum::getValue)
      .toList();

    CqlQuery query = CqlQuery.exactMatchAny("requestType", requestTypes)
      .and(CqlQuery.exactMatchAny("status", requestStatuses));

    return requestService.getRequestsFromStorage(query, "itemId", itemIds);
  }

  private static Collection<SearchItem> filterRequestedItems(Collection<SearchItem> items,
    Collection<Request> requests) {

    log.info("filterItemsByRequests:: filtering out non-requested items");
    Set<String> requestedItemIds = requests.stream()
      .map(Request::getItemId)
      .filter(Objects::nonNull)
      .collect(toSet());

    List<SearchItem> requestedItems = items.stream()
      .filter(item -> requestedItemIds.contains(item.getId()))
      .toList();

    log.info("filterItemsByRequests:: {} of {} relevant items are requested", requestedItems::size,
      items::size);
    return requestedItems;
  }

  private Collection<StaffSlipContext> buildStaffSlipContexts(Collection<Request> requests,
    Collection<SearchItem> requestedItems, Collection<SearchInstance> instances,
    Map<String, Collection<Location>> locationsByTenant) {

    if (requests.isEmpty()) {
      log.info("buildStaffSlipContexts:: no requests to build contexts for, doing nothing");
      return emptyList();
    }

    log.info("buildStaffSlipContexts:: building contexts for {} requests", requests::size);
    Map<String, ItemContext> itemContextsByItemId = buildItemContexts(requestedItems, instances,
      locationsByTenant);
    Map<String, RequesterContext> requesterContextsByRequestId = buildRequesterContexts(requests);
    Map<String, RequestContext> requestContextsByRequestId = buildRequestContexts(requests);

    Collection<StaffSlipContext> staffSlipContexts = requests.stream()
      .map(request -> new StaffSlipContext(
        itemContextsByItemId.get(request.getItemId()),
        requesterContextsByRequestId.get(request.getId()),
        requestContextsByRequestId.get(request.getId())))
      .toList();

    log.info("getStaffSlips:: successfully built contexts for {} requests", requests::size);
    return staffSlipContexts;
  }

  private Map<String, ItemContext> buildItemContexts(Collection<SearchItem> requestedItems,
    Collection<SearchInstance> instances, Map<String, Collection<Location>> locationsByTenant) {

    log.info("buildItemContexts:: building contexts for {} items", requestedItems::size);

    Map<String, Set<String>> requestedItemIdsByTenant = requestedItems.stream()
      .collect(groupingBy(SearchItem::getTenantId, mapping(SearchItem::getId, toSet())));

    Map<String, SearchInstance> itemIdToInstance = instances.stream()
      .flatMap(searchInstance -> searchInstance.getItems().stream()
        .map(item -> new AbstractMap.SimpleEntry<>(item.getId(), searchInstance)))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

    return requestedItemIdsByTenant.entrySet()
      .stream()
      .map(entry -> buildItemContexts(entry.getKey(), entry.getValue(), locationsByTenant, itemIdToInstance))
      .flatMap(Collection::stream)
      .collect(toMap(context -> context.item().getId(), identity()));
  }

  private Collection<ItemContext> buildItemContexts(String tenantId, Collection<String> itemIds,
    Map<String, Collection<Location>> locationsByTenant, Map<String, SearchInstance> itemIdToInstance) {

    log.info("buildItemContexts:: building item contexts for {} items in tenant {}", itemIds.size(), tenantId);
    return executionService.executeSystemUserScoped(tenantId,
      () -> buildItemContexts(itemIds, itemIdToInstance, locationsByTenant.get(tenantId)));
  }

  private Collection<ItemContext> buildItemContexts(Collection<String> itemIds,
    Map<String, SearchInstance> itemIdToInstance, Collection<Location> locations) {

    Collection<Item> items = inventoryService.findItems(itemIds);

    Map<String, MaterialType> materialTypesById = findMaterialTypes(items)
      .stream()
      .collect(mapById(MaterialType::getId));

    Map<String, LoanType> loanTypesById = findLoanTypes(items)
      .stream()
      .collect(mapById(LoanType::getId));

    Set<String> locationIdsOfRequestedItems = items.stream()
      .map(Item::getEffectiveLocationId)
      .collect(toSet());

    Map<String, Location> locationsById = locations.stream()
      .filter(location -> locationIdsOfRequestedItems.contains(location.getId()))
      .toList().stream()
      .collect(mapById(Location::getId));

    Collection<Location> locationsOfRequestedItems = locationsById.values();

    Map<String, Library> librariesById = findLibraries(locationsOfRequestedItems)
      .stream()
      .collect(mapById(Library::getId));

    Map<String, Campus> campusesById = findCampuses(locationsOfRequestedItems)
      .stream()
      .collect(mapById(Campus::getId));

    Map<String, Institution> institutionsById = findInstitutions(locationsOfRequestedItems)
      .stream()
      .collect(mapById(Institution::getId));

    Map<String, ServicePoint> servicePointsById = findServicePointsForLocations(locationsOfRequestedItems)
      .stream()
      .collect(mapById(ServicePoint::getId));

    List<ItemContext> itemContexts = new ArrayList<>(items.size());
    for (Item item : items) {
      SearchInstance instance = itemIdToInstance.get(item.getId());
      Location location = locationsById.get(item.getEffectiveLocationId());
      ServicePoint primaryServicePoint = Optional.ofNullable(location.getPrimaryServicePoint())
        .map(UUID::toString)
        .map(servicePointsById::get)
        .orElse(null);
      SearchHolding holding = instance.getHoldings()
        .stream()
        .filter(h -> item.getHoldingsRecordId().equals(h.getId()))
        .findFirst()
        .orElse(null);

      ItemContext itemContext = new ItemContext(item, instance, holding, location,
        materialTypesById.get(item.getMaterialTypeId()),
        loanTypesById.get(getEffectiveLoanTypeId(item)),
        institutionsById.get(location.getInstitutionId()),
        campusesById.get(location.getCampusId()),
        librariesById.get(location.getLibraryId()),
        primaryServicePoint);

      itemContexts.add(itemContext);
    }

    return itemContexts;
  }

  private Map<String, RequesterContext> buildRequesterContexts(Collection<Request> requests) {
    log.info("buildRequesterContexts:: building requester contexts for {} requests", requests::size);
    Collection<User> requesters = findRequesters(requests);
    Collection<UserGroup> userGroups = findUserGroups(requesters);
    Collection<Department> departments = findDepartments(requesters);
    Collection<AddressType> addressTypes = findAddressTypes(requesters);

    Map<String, User> requestersById = requesters.stream()
      .collect(mapById(User::getId));
    Map<String, UserGroup> userGroupsById = userGroups.stream()
      .collect(mapById(UserGroup::getId));
    Map<String, Department> departmentsById = departments.stream()
      .collect(mapById(Department::getId));
    Map<String, AddressType> addressTypesById = addressTypes.stream()
      .collect(mapById(AddressType::getId));

    Map<String, RequesterContext> requesterContexts = new HashMap<>(requests.size());
    for (Request request : requests) {
      User requester = requestersById.get(request.getRequesterId());
      UserGroup userGroup = userGroupsById.get(requester.getPatronGroup());

      Collection<Department> requesterDepartments = requester.getDepartments()
        .stream()
        .filter(Objects::nonNull)
        .map(departmentsById::get)
        .toList();

      AddressType primaryRequesterAddressType = Optional.ofNullable(requester.getPersonal())
        .map(UserPersonal::getAddresses)
        .flatMap(addresses -> addresses.stream()
          .filter(UserPersonalAddressesInner::getPrimaryAddress)
          .findFirst()
          .map(UserPersonalAddressesInner::getAddressTypeId)
          .map(addressTypesById::get))
        .orElse(null);

      AddressType deliveryAddressType = addressTypesById.get(request.getDeliveryAddressTypeId());

      RequesterContext requesterContext = new RequesterContext(requester, userGroup,
        requesterDepartments, primaryRequesterAddressType, deliveryAddressType);
      requesterContexts.put(request.getId(), requesterContext);
    }

    return requesterContexts;
  }

  private Map<String, RequestContext> buildRequestContexts(Collection<Request> requests) {
    log.info("buildRequesterContexts:: building request contexts for {} requests", requests::size);
    Collection<ServicePoint> servicePoints = findServicePointsForRequests(requests);
    Map<String, ServicePoint> servicePointsById = servicePoints.stream()
      .collect(mapById(ServicePoint::getId));

    Map<String, RequestContext> requestContexts = new HashMap<>(requests.size());
    for (Request request : requests) {
      ServicePoint pickupServicePoint = servicePointsById.get(request.getPickupServicePointId());
      RequestContext requestContext = new RequestContext(request, pickupServicePoint);
      requestContexts.put(request.getId(), requestContext);
    }

    return requestContexts;
  }

  private Collection<User> findRequesters(Collection<Request> requests) {
    if (requests.isEmpty()) {
      log.info("findRequesters:: no requests to search requesters for, doing nothing");
      return emptyList();
    }

    Set<String> requesterIds = requests.stream()
      .map(Request::getRequesterId)
      .collect(toSet());

    return userService.find(requesterIds);
  }

  private Collection<UserGroup> findUserGroups(Collection<User> requesters) {
    if (requesters.isEmpty()) {
      log.info("findUserGroups:: no requesters to search user groups for, doing nothing");
      return emptyList();
    }

    Set<String> userGroupIds = requesters.stream()
      .map(User::getPatronGroup)
      .filter(Objects::nonNull)
      .collect(toSet());

    return userGroupService.find(userGroupIds);
  }

  private Collection<Department> findDepartments(Collection<User> requesters) {
    if (requesters.isEmpty()) {
      log.info("findDepartments:: no requesters to search departments for, doing nothing");
      return emptyList();
    }

    Set<String> departmentIds = requesters.stream()
      .map(User::getDepartments)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .collect(toSet());

    return departmentService.findDepartments(departmentIds);
  }

  private Collection<AddressType> findAddressTypes(Collection<User> requesters) {
    if (requesters.isEmpty()) {
      log.info("findAddressTypes:: no requesters to search address types for, doing nothing");
      return emptyList();
    }

    Set<String> addressTypeIds = requesters.stream()
      .map(User::getPersonal)
      .filter(Objects::nonNull)
      .map(UserPersonal::getAddresses)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .map(UserPersonalAddressesInner::getAddressTypeId)
      .collect(toSet());

    return addressTypeService.findAddressTypes(addressTypeIds);
  }

  private Collection<ServicePoint> findServicePointsForLocations(Collection<Location> locations) {
    return findServicePoints(
      locations.stream()
        .map(Location::getPrimaryServicePoint)
        .filter(Objects::nonNull)
        .map(UUID::toString)
        .collect(toSet())
    );
  }

  private Collection<ServicePoint> findServicePointsForRequests(Collection<Request> requests) {
    return findServicePoints(
      requests.stream()
        .map(Request::getPickupServicePointId)
        .filter(Objects::nonNull)
        .collect(toSet())
    );
  }

  private Collection<ServicePoint> findServicePoints(Collection<String> servicePointIds) {
    if (servicePointIds.isEmpty()) {
      log.info("findServicePoints:: no IDs to search service points by, doing nothing");
      return emptyList();
    }

    return servicePointService.find(servicePointIds);
  }

  private Collection<MaterialType> findMaterialTypes(Collection<Item> items) {
    if (items.isEmpty()) {
      log.info("findMaterialTypes:: no items to search material types for, doing nothing");
      return emptyList();
    }

    Set<String> materialTypeIds = items.stream()
      .map(Item::getMaterialTypeId)
      .collect(toSet());

    return inventoryService.findMaterialTypes(materialTypeIds);
  }

  private Collection<LoanType> findLoanTypes(Collection<Item> items) {
    if (items.isEmpty()) {
      log.info("findLoanTypes:: no items to search loan types for, doing nothing");
      return emptyList();
    }

    Set<String> loanTypeIds = items.stream()
      .map(StaffSlipsServiceImpl::getEffectiveLoanTypeId)
      .collect(toSet());

    return inventoryService.findLoanTypes(loanTypeIds);
  }

  private Collection<Library> findLibraries(Collection<Location> locations) {
    if (locations.isEmpty()) {
      log.info("findLibraries:: no locations to search libraries for, doing nothing");
      return emptyList();
    }

    Set<String> libraryIds = locations.stream()
      .map(Location::getLibraryId)
      .collect(toSet());

    return inventoryService.findLibraries(libraryIds);
  }

  private Collection<Campus> findCampuses(Collection<Location> locations) {
    if (locations.isEmpty()) {
      log.info("findCampuses:: no locations to search campuses for, doing nothing");
      return emptyList();
    }

    Set<String> campusIds = locations.stream()
      .map(Location::getCampusId)
      .collect(toSet());

    return inventoryService.findCampuses(campusIds);
  }

  private Collection<Institution> findInstitutions(Collection<Location> locations) {
    if (locations.isEmpty()) {
      log.info("findCampuses:: no locations to search institutions for, doing nothing");
      return emptyList();
    }

    Set<String> institutionIds = locations.stream()
      .map(Location::getInstitutionId)
      .collect(toSet());

    return inventoryService.findInstitutions(institutionIds);
  }

  private static Collection<StaffSlip> buildStaffSlips(Collection<StaffSlipContext> contexts) {
    log.info("buildStaffSlips:: building staff slips for {} contexts", contexts::size);
    return contexts.stream()
      .map(StaffSlipsServiceImpl::buildStaffSlip)
      .toList();
  }

  private static StaffSlip buildStaffSlip(StaffSlipContext context) {
    log.info("buildStaffSlip:: building staff slip for request {}",
      context.requestContext.request().getId());

    return new StaffSlip()
      .currentDateTime(new Date())
      .item(buildStaffSlipItem(context))
      .request(buildStaffSlipRequest(context))
      .requester(buildStaffSlipRequester(context));
  }

  private static StaffSlipItem buildStaffSlipItem(StaffSlipContext context) {
    log.debug("buildStaffSlipItem:: building staff slip item");
    ItemContext itemContext = context.itemContext();
    Item item = itemContext.item();
    if (item == null) {
      log.warn("buildStaffSlipItem:: item is null, doing nothing");
      return null;
    }

    String yearCaptions = Optional.ofNullable(item.getYearCaption())
      .map(captions -> String.join("; ", captions))
      .orElse(null);

    String copyNumber = Optional.ofNullable(item.getCopyNumber())
      .or(() -> Optional.ofNullable(itemContext.holding().getCopyNumber()))
      .orElse("");

    String materialType = Optional.ofNullable(itemContext.materialType)
      .map(MaterialType::getName)
      .orElse(null);

    String loanType = Optional.ofNullable(itemContext.loanType())
      .map(LoanType::getName)
      .orElse(null);

    StaffSlipItem staffSlipItem = new StaffSlipItem()
      .barcode(item.getBarcode())
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

    SearchInstance instance = itemContext.instance();
    if (instance != null) {
      staffSlipItem.title(instance.getTitle());

      List<Contributor> contributors = instance.getContributors();
      if (contributors != null && !contributors.isEmpty()) {
        String primaryContributor = contributors.stream()
          .filter(Contributor::getPrimary)
          .findFirst()
          .map(Contributor::getName)
          .orElse(null);

        String allContributors = contributors.stream()
          .map(Contributor::getName)
          .collect(joining("; "));

        staffSlipItem
          .title(instance.getTitle())
          .primaryContributor(primaryContributor)
          .allContributors(allContributors);
      }
    }

    Location location = itemContext.location();
    if (location != null) {
      staffSlipItem
        .effectiveLocationSpecific(location.getName())
        .effectiveLocationDiscoveryDisplayName(location.getDiscoveryDisplayName());

      Optional.ofNullable(itemContext.library())
        .map(Library::getName)
        .ifPresent(staffSlipItem::effectiveLocationLibrary);

      Optional.ofNullable(itemContext.campus())
        .map(Campus::getName)
        .ifPresent(staffSlipItem::effectiveLocationCampus);

      Optional.ofNullable(itemContext.institution())
        .map(Institution::getName)
        .ifPresent(staffSlipItem::effectiveLocationInstitution);

      Optional.ofNullable(itemContext.primaryServicePoint())
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

  private static StaffSlipRequest buildStaffSlipRequest(StaffSlipContext context) {
    log.debug("buildStaffSlipItem:: building staff slip request");
    RequestContext requestContext = context.requestContext();
    Request request = requestContext.request();
    if (request == null) {
      log.warn("buildStaffSlipRequest:: request is null, doing nothing");
      return null;
    }

    String deliveryAddressType = Optional.ofNullable(context.requesterContext.deliveryAddressType())
      .map(AddressType::getAddressType)
      .orElse(null);

    String pickupServicePoint = Optional.ofNullable(requestContext.pickupServicePoint())
      .map(ServicePoint::getName)
      .orElse(null);

    return new StaffSlipRequest()
      .requestId(UUID.fromString(request.getId()))
      .servicePointPickup(pickupServicePoint)
      .requestDate(request.getRequestDate())
      .requestExpirationDate(request.getRequestExpirationDate())
      .holdShelfExpirationDate(request.getHoldShelfExpirationDate())
      .additionalInfo(request.getCancellationAdditionalInformation())
      .deliveryAddressType(deliveryAddressType)
      .patronComments(request.getPatronComments());
  }

  private static StaffSlipRequester buildStaffSlipRequester(StaffSlipContext context) {
    log.debug("buildStaffSlipItem:: building staff slip requester");
    RequesterContext requesterContext = context.requesterContext();
    User requester = requesterContext.requester();
    if (requester == null) {
      log.warn("buildStaffSlipRequester:: requester is null, doing nothing");
      return null;
    }

    String departments = requesterContext.departments()
      .stream()
      .map(Department::getName)
      .collect(joining("; "));

    String patronGroup = Optional.ofNullable(requesterContext.userGroup())
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

      String primaryAddressType = Optional.ofNullable(requesterContext.primaryAddressType())
        .map(AddressType::getAddressType)
        .orElse(null);

      String deliveryAddressType = Optional.ofNullable(requesterContext.deliveryAddressType())
        .map(AddressType::getAddressType)
        .orElse(null);

      staffSlipRequester
        .firstName(personal.getFirstName())
        .preferredFirstName(preferredFirstName)
        .lastName(personal.getLastName())
        .middleName(personal.getMiddleName());

      List<UserPersonalAddressesInner> addresses = personal.getAddresses();
      if (addresses != null) {
        String deliveryAddressTypeId =  context.requestContext().request().getDeliveryAddressTypeId();
        if (deliveryAddressTypeId != null) {
          personal.getAddresses()
            .stream()
            .filter(address -> deliveryAddressTypeId.equals(address.getAddressTypeId()))
            .findFirst()
            .ifPresent(deliveryAddress -> staffSlipRequester
              .addressLine1(deliveryAddress.getAddressLine1())
              .addressLine2(deliveryAddress.getAddressLine2())
              .city(deliveryAddress.getCity())
              .region(deliveryAddress.getRegion())
              .postalCode(deliveryAddress.getPostalCode())
              .countryId(deliveryAddress.getCountryId())
              .addressType(deliveryAddressType)
            );
        }

        personal.getAddresses()
          .stream()
          .filter(UserPersonalAddressesInner::getPrimaryAddress)
          .findFirst()
          .ifPresent(primaryAddress -> staffSlipRequester
            .primaryAddressLine1(primaryAddress.getAddressLine1())
            .primaryAddressLine2(primaryAddress.getAddressLine2())
            .primaryCity(primaryAddress.getCity())
            .primaryStateProvRegion(primaryAddress.getRegion())
            .primaryZipPostalCode(primaryAddress.getPostalCode())
            .primaryCountry(getCountryName(primaryAddress.getCountryId()))
            .primaryDeliveryAddressType(primaryAddressType)
          );
      }
    }

    return  staffSlipRequester;
  }

  private static <T> Collector<T, ?, Map<String, T>> mapById(Function<T, String> keyMapper) {
    return toMap(keyMapper, identity());
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

  private record ItemContext(Item item, SearchInstance instance, SearchHolding holding,
    Location location, MaterialType materialType, LoanType loanType, Institution institution,
    Campus campus, Library library, ServicePoint primaryServicePoint) {}

  private record RequesterContext(User requester, UserGroup userGroup,
    Collection<Department> departments, AddressType primaryAddressType,
    AddressType deliveryAddressType) {}

  private record RequestContext(Request request, ServicePoint pickupServicePoint) { }

  private record StaffSlipContext(ItemContext itemContext, RequesterContext requesterContext,
    RequestContext requestContext) {}

}
