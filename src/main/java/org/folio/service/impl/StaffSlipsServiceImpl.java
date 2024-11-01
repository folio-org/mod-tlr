package org.folio.service.impl;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;

import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.StaffSlip;
import org.folio.domain.dto.StaffSlipItem;
import org.folio.domain.dto.StaffSlipRequest;
import org.folio.domain.dto.Tenant;
import org.folio.service.ConsortiaService;
import org.folio.service.ItemService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.service.StaffSlipsService;
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

  @Override
  public Collection<StaffSlip> getStaffSlips(String servicePointId) {
    log.info("getStaffSlips:: building staff slips for service point {}", servicePointId);
    return getConsortiumTenants()
      .stream()
      .map(tenantId -> buildStaffSlips(servicePointId, tenantId))
      .flatMap(Collection::stream)
      .toList();
  }

  private Collection<StaffSlip> buildStaffSlips(String servicePointId, String tenantId) {
    log.info("buildStaffSlips:: building staff slips for tenant {}", tenantId);
    return executionService.executeSystemUserScoped(tenantId, () -> buildStaffSlips(servicePointId));
  }

  private Collection<StaffSlip> buildStaffSlips(String servicePointId) {
    Collection<Location> locations = findLocations(servicePointId);
    Collection<Item> items = findItems(locations);
    Collection<Request> requests = findRequests(items);

    Map<String, Location> locationsById = locations.stream()
      .collect(mapById(Location::getId));
    Map<String, Item> itemsById = items.stream()
      .collect(mapById(Item::getId));

    return requests.stream()
      .map(request -> {
        Item item = itemsById.get(request.getItemId());
        return new StaffSlipContext(request, item, locationsById.get(item.getEffectiveLocationId()));
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

  private static StaffSlip buildStaffSlip(StaffSlipContext context) {
    return new StaffSlip()
      .currentDateTime(new Date())
      .item(buildStaffSlipItem(context))
      .request(buildStaffSlipRequest(context));
  }

  private static StaffSlipItem buildStaffSlipItem(StaffSlipContext context) {
    Item item = context.item();
    if (item == null) {
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
      return null;
    }

    return new StaffSlipRequest()
      .requestId(UUID.fromString(request.getId()))
      .servicePointPickup(null) // get name from pickup service point
      .requestDate(request.getRequestDate())
      .requestExpirationDate(request.getRequestExpirationDate())
      .holdShelfExpirationDate(request.getHoldShelfExpirationDate())
      .additionalInfo(request.getCancellationAdditionalInformation())
      .reasonForCancellation(null) // get from cancellation reason
      .deliveryAddressType(null) // get from delivery address type
      .patronComments(request.getPatronComments());
  }

  private static <T> Collector<T, ?, Map<String, T>> mapById(Function<T, String> keyMapper) {
    return toMap(keyMapper, identity());
  }

  private record StaffSlipContext(Request request, Item item, Location location) {}

}
