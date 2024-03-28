package org.folio.service.impl;

import static java.lang.String.format;

import java.util.Collection;
import java.util.function.Function;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.folio.exception.RequestCreatingException;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class RequestServiceImpl implements RequestService {
  private static final String SECONDARY_REQUEST_PICKUP_SERVICE_POINT_NAME_PREFIX = "DCB_";
  private final SystemUserScopedExecutionService executionService;
  private final CirculationClient circulationClient;
  private final UserService userService;
  private final ServicePointService servicePointService;

  @Override
  public RequestWrapper createPrimaryRequest(Request request, String borrowingTenantId) {
    final String requestId = request.getId();
    log.info("createPrimaryRequest:: creating primary request {} in borrowing tenant ({})",
      requestId, borrowingTenantId);
    Request primaryRequest = executionService.executeSystemUserScoped(borrowingTenantId,
      () -> circulationClient.createRequest(request));
    log.info("createPrimaryRequest:: primary request {} created in borrowing tenant ({})",
      requestId, borrowingTenantId);
    log.debug("createPrimaryRequest:: primary request: {}", () -> primaryRequest);

    return new RequestWrapper(primaryRequest, borrowingTenantId);
  }

  @Override
  public RequestWrapper createSecondaryRequest(Request request, String borrowingTenantId,
    Collection<String> lendingTenantIds) {

    final String requestId = request.getId();
    final String requesterId = request.getRequesterId();
    final String pickupServicePointId = request.getPickupServicePointId();

    log.info("createSecondaryRequest:: creating secondary request {} in one of potential " +
      "lending tenants: {}", requestId, lendingTenantIds);

    User secondaryRequestRequester = buildSecondaryRequestRequester(requesterId, borrowingTenantId);
    ServicePoint secondaryRequestPickupServicePoint = buildSecondaryRequestPickupServicePoint(
      pickupServicePointId, borrowingTenantId);

    for (String lendingTenantId : lendingTenantIds) {
      try {
        return executionService.executeSystemUserScoped(lendingTenantId, () -> {
          log.info("createSecondaryRequest:: creating requester {} in lending tenant ({})",
            requesterId, lendingTenantId);
          getOrCreateUser(secondaryRequestRequester);

          log.info("createSecondaryRequest:: creating pickup service point {} in lending tenant ({})",
            pickupServicePointId, lendingTenantId);
          getOrCreateServicePoint(secondaryRequestPickupServicePoint);

          log.info("createSecondaryRequest:: creating secondary request {} in lending tenant ({})",
            requestId, lendingTenantId);
          Request secondaryRequest = circulationClient.createInstanceRequest(request);
          log.info("createSecondaryRequest:: secondary request {} created in lending tenant ({})",
            requestId, lendingTenantId);
          log.debug("createSecondaryRequest:: secondary request: {}", () -> secondaryRequest);

          return new RequestWrapper(secondaryRequest, lendingTenantId);
        });
      } catch (Exception e) {
        log.error("createSecondaryRequest:: failed to create secondary request in lending tenant ({}): {}",
          lendingTenantId, e.getMessage());
        log.debug("createSecondaryRequest:: ", e);
      }
    }

    String errorMessage = format(
      "Failed to create secondary request for instance %s in all potential lending tenants: %s",
      request.getInstanceId(), lendingTenantIds);
    log.error("createSecondaryRequest:: {}", errorMessage);
    throw new RequestCreatingException(errorMessage);
  }

  public User getOrCreateUser(User user) {
    return getOrCreate(user.getId(), user, userService::find, userService::create);
  }

  public ServicePoint getOrCreateServicePoint(ServicePoint servicePoint) {
    return getOrCreate(servicePoint.getId(), servicePoint, servicePointService::find,
      servicePointService::create);
  }

  public <T> T getOrCreate(String objectId, T object, Function<String, T> finder,
    Function<T, T> creator) {

    final String objectType = object.getClass().getSimpleName();
    log.info("getOrCreate:: looking for existing {} {}", objectType, objectId);
    try {
      T existingObject = finder.apply(objectId);
      log.info("getOrCreate:: {} {} already exists, attempting to reuse it", objectType, objectId);
      return existingObject;
    } catch (FeignException.NotFound e) {
      log.info("getOrCreate:: failed to find {} {}, attempting to create it", objectType, objectId);
      T createdObject = creator.apply(object);
      log.info("getOrCreate:: {} {} created", objectType, objectId);
      return createdObject;
    }
  }

  private User buildSecondaryRequestRequester(String requesterId, String borrowingTenantId) {
    log.info("buildSecondaryRequestRequester:: looking for requester {} in borrowing tenant ({})",
      requesterId, borrowingTenantId);
    User primaryRequestRequester = executionService.executeSystemUserScoped(borrowingTenantId,
      () -> userService.find(requesterId));
    log.info("buildSecondaryRequestRequester:: requester {} found in borrowing tenant ({})",
      requesterId, borrowingTenantId);

    return buildSecondaryRequestRequester(primaryRequestRequester);
  }

  private ServicePoint buildSecondaryRequestPickupServicePoint(String pickupServicePointId,
    String borrowingTenantId) {

    log.info("buildSecondaryRequestPickupServicePoint:: looking for service point {} in borrowing " +
        "tenant ({})", pickupServicePointId, borrowingTenantId);
    ServicePoint primaryRequestPickupServicePoint = executionService.executeSystemUserScoped(
      borrowingTenantId, () -> servicePointService.find(pickupServicePointId));
    log.info("buildSecondaryRequestPickupServicePoint:: service point {} found  in borrowing " +
        "tenant ({})", pickupServicePointId, borrowingTenantId);

    return buildSecondaryRequestPickupServicePoint(primaryRequestPickupServicePoint);
  }

  private static User buildSecondaryRequestRequester(User primaryRequestRequester) {
    User secondaryRequestRequester = new User()
      .id(primaryRequestRequester.getId())
      .username(primaryRequestRequester.getUsername())
      .patronGroup(primaryRequestRequester.getPatronGroup())
      .type(UserType.SHADOW.getValue())
      .active(true);

    UserPersonal personal = primaryRequestRequester.getPersonal();
    if (personal != null) {
      secondaryRequestRequester.setPersonal(new UserPersonal()
        .firstName(personal.getFirstName())
        .lastName(personal.getLastName())
      );
    }
    log.debug("buildShadowUser:: result: {}", () -> secondaryRequestRequester);
    return secondaryRequestRequester;
  }

  private static ServicePoint buildSecondaryRequestPickupServicePoint(
    ServicePoint primaryRequestPickupServicePoint) {

    ServicePoint servicePoint = new ServicePoint()
      .id(primaryRequestPickupServicePoint.getId())
      .name(SECONDARY_REQUEST_PICKUP_SERVICE_POINT_NAME_PREFIX + primaryRequestPickupServicePoint.getName());

    log.debug("buildSecondaryRequestPickupServicePoint:: result: {}", () -> servicePoint);
    return servicePoint;
  }

}
