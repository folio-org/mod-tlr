package org.folio.service.impl;

import static java.util.Collections.emptyList;
import static java.util.Locale.getISOCountries;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.folio.domain.dto.AddressType;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.StaffSlip;
import org.folio.domain.dto.StaffSlipItem;
import org.folio.domain.dto.StaffSlipRequest;
import org.folio.domain.dto.StaffSlipRequester;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserPersonalAddressesInner;
import org.folio.service.AddressTypeService;
import org.folio.service.ConsortiaService;
import org.folio.service.DepartmentService;
import org.folio.service.ItemService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.service.StaffSlipsService;
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
  private final ItemService itemService;
  private final RequestService requestService;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService executionService;
  private final UserService userService;
  private final DepartmentService departmentService;
  private final AddressTypeService addressTypeService;

  @Override
  public Collection<StaffSlip> getStaffSlips(String servicePointId) {
    log.info("getStaffSlips:: building staff slips for service point {}", servicePointId);
    List<StaffSlip> staffSlips = getConsortiumTenants()
      .stream()
      .map(tenantId -> buildStaffSlips(servicePointId, tenantId))
      .flatMap(Collection::stream)
      .toList();

    log.info("buildStaffSlips:: successfully built {} staff slips", staffSlips::size);
    return staffSlips;
  }

  private Collection<StaffSlip> buildStaffSlips(String servicePointId, String tenantId) {
    log.info("buildStaffSlips:: building staff slips for tenant {}", tenantId);
    return executionService.executeSystemUserScoped(tenantId, () -> buildStaffSlips(servicePointId));
  }

  private Collection<StaffSlip> buildStaffSlips(String servicePointId) {
    Collection<Location> locations = findLocations(servicePointId);
    Collection<Item> items = findItems(locations);
    Collection<Request> requests = findRequests(items);
    Collection<User> requesters = findRequesters(requests);
    Collection<Department> departments = findDepartments(requesters);
    Collection<AddressType> addressTypes = findAddressTypes(requesters);

    Map<String, Location> locationsById = locations.stream()
      .collect(mapById(Location::getId));
    Map<String, Item> itemsById = items.stream()
      .collect(mapById(Item::getId));
    Map<String, User> requestersById = requesters.stream()
      .collect(mapById(User::getId));
    Map<String, Department> departmentsById = departments.stream()
      .collect(mapById(Department::getId));
    Map<String, AddressType> addressTypesById = addressTypes.stream()
      .collect(mapById(AddressType::getId));

    return requests.stream()
      .map(request -> {
        log.info("buildStaffSlips:: building staff slip for request {}", request::getId);
        Item item = itemsById.get(request.getItemId());
        User requester = requestersById.get(request.getRequesterId());

        Collection<Department> requesterDepartments = requester.getDepartments()
          .stream()
          .filter(Objects::nonNull)
          .map(departmentsById::get)
          .toList();

        AddressType primaryRequesterAddress = Optional.ofNullable(requester.getPersonal())
          .map(UserPersonal::getAddresses)
          .flatMap(addresses -> addresses.stream()
            .filter(UserPersonalAddressesInner::getPrimaryAddress)
            .findFirst()
            .map(UserPersonalAddressesInner::getId)
            .map(addressTypesById::get))
          .orElse(null);

        return new StaffSlipContext(
          request,
          item,
          locationsById.get(item.getEffectiveLocationId()),
          requester,
          requesterDepartments,
          primaryRequesterAddress,
          addressTypesById.get(request.getDeliveryAddressTypeId())
        );
      })
      .map(StaffSlipsServiceImpl::buildStaffSlip)
      .toList();
  }

  private Collection<String> getConsortiumTenants() {
    return consortiaService.getAllConsortiumTenants()
      .stream()
      .map(Tenant::getId)
      .toList();
  }

  private Collection<Location> findLocations(String servicePointId) {
    CqlQuery locationQuery = CqlQuery.exactMatch("primaryServicePoint", servicePointId);
    return locationService.findLocations(locationQuery);
  }

  private Collection<Item> findItems(Collection<Location> locations) {
    if (locations.isEmpty()) {
      log.info("findItems:: no locations to search items for, doing nothing");
      return emptyList();
    }

    List<String> locationIds = locations.stream()
      .map(Location::getId)
      .toList();

    List<String> itemStatusStrings = relevantItemStatuses.stream()
      .map(ItemStatus.NameEnum::getValue)
      .toList();

    CqlQuery query = CqlQuery.exactMatchAny("status.name", itemStatusStrings);

    return itemService.findItems(query, "effectiveLocationId", locationIds);
  }

  private Collection<Request> findRequests(Collection<Item> items) {
    if (items.isEmpty()) {
      log.info("findRequests:: no items to search requests for, doing nothing");
      return emptyList();
    }

    List<String> itemIds = items.stream()
      .map(Item::getId)
      .toList();

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

  private static StaffSlip buildStaffSlip(StaffSlipContext context) {
    return new StaffSlip()
      .currentDateTime(new Date())
      .item(buildStaffSlipItem(context))
      .request(buildStaffSlipRequest(context))
      .requester(buildStaffSlipRequester(context));
  }

  private static StaffSlipItem buildStaffSlipItem(StaffSlipContext context) {
    Item item = context.item();
    if (item == null) {
      log.warn("buildStaffSlipItem:: item is null, doing nothing");
      return null;
    }

    String yearCaptions = Optional.ofNullable(item.getYearCaption())
      .map(captions -> String.join("; ", captions))
      .orElse(null);

    String copyNumber = Optional.ofNullable(item.getCopyNumber())
//      .or(holdings.getCopyNumber())
      .orElse("");

    StaffSlipItem staffSlipItem = new StaffSlipItem()
      .title(null) // get from instance
      .primaryContributor(null) // get from instance
      .allContributors(null) // get from instance
      .barcode(item.getBarcode())
      .status(item.getStatus().getName().getValue())
      .enumeration(item.getEnumeration())
      .volume(item.getVolume())
      .chronology(item.getChronology())
      .yearCaption(yearCaptions)
      .materialType(null) // get from material type
      .loanType(null) // get from loan type
      .copy(copyNumber)
      .numberOfPieces(item.getNumberOfPieces())
      .displaySummary(item.getDisplaySummary())
      .descriptionOfPieces(item.getDescriptionOfPieces());

    Location location = context.location();
    if (location != null) {
      staffSlipItem
        .effectiveLocationSpecific(location.getName())
        .effectiveLocationLibrary(null) // get from library
        .effectiveLocationCampus(null) // get from library
        .effectiveLocationInstitution(null) // get from library or location
        .effectiveLocationDiscoveryDisplayName(location.getDiscoveryDisplayName());
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
    Request request = context.request();
    if (request == null) {
      log.warn("buildStaffSlipRequest:: request is null, doing nothing");
      return null;
    }

    String deliveryAddressType = Optional.ofNullable(context.deliveryAddressType())
      .map(AddressType::getAddressType)
      .orElse(null);

    return new StaffSlipRequest()
      .requestId(UUID.fromString(request.getId()))
      .servicePointPickup(null) // get name from pickup service point
      .requestDate(request.getRequestDate())
      .requestExpirationDate(request.getRequestExpirationDate())
      .holdShelfExpirationDate(request.getHoldShelfExpirationDate())
      .additionalInfo(request.getCancellationAdditionalInformation())
      .reasonForCancellation(null) // get from cancellation reason
      .deliveryAddressType(deliveryAddressType)
      .patronComments(request.getPatronComments());
  }

  private static StaffSlipRequester buildStaffSlipRequester(StaffSlipContext context) {
    User requester = context.requester();
    if (requester == null) {
      log.warn("buildStaffSlipRequester:: requester is null, doing nothing");
      return null;
    }

    String departments = context.departments()
      .stream()
      .map(Department::getName)
      .collect(Collectors.joining("; "));

    StaffSlipRequester staffSlipRequester = new StaffSlipRequester()
      .barcode(requester.getBarcode())
      .patronGroup(Optional.ofNullable(requester.getPatronGroup()).orElse(""))
      .departments(departments);

    UserPersonal personal = requester.getPersonal();
    if (personal != null) {
      String preferredFirstName = Optional.ofNullable(personal.getPreferredFirstName())
        .orElseGet(personal::getFirstName);

      String primaryAddressType = Optional.ofNullable(context.primaryAddressType())
        .map(AddressType::getAddressType)
        .orElse(null);

      String deliveryAddressType = Optional.ofNullable(context.deliveryAddressType())
        .map(AddressType::getAddressType)
        .orElse(null);

      staffSlipRequester
        .firstName(personal.getFirstName())
        .preferredFirstName(preferredFirstName)
        .lastName(personal.getLastName())
        .middleName(personal.getMiddleName());

      List<UserPersonalAddressesInner> addresses = personal.getAddresses();
      if (addresses != null) {
        String deliveryAddressTypeId = context.request().getDeliveryAddressTypeId();
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

  public static String getCountryName(String countryCode) {
    if (isBlank(countryCode) || !Arrays.asList(getISOCountries()).contains(countryCode)) {
      log.warn("getCountryName:: unknown country code: {}", countryCode);
      return null;
    }

    return new Locale("", countryCode).getDisplayName();
  }

  private record StaffSlipContext(Request request, Item item, Location location, User requester,
    Collection<Department> departments, AddressType primaryAddressType,
    AddressType deliveryAddressType) {}

}
