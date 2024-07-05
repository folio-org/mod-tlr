package org.folio.service.impl;

import java.nio.charset.StandardCharsets;

import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserTenant;
import org.folio.service.ConsortiaService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.UserGroupService;
import org.folio.service.UserTenantsService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class UserGroupEventHandler implements KafkaEventHandler<UserGroup> {

  private final UserTenantsService userTenantsService;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final UserGroupService userGroupService;

  @Override
  public void handle(KafkaEvent<UserGroup> event, MessageHeaders messageHeaders) {
    log.info("handle:: processing request event: {}, messageHeaders: {}",
      () -> event, () -> messageHeaders);

    String tenantId = getHeaderValue(messageHeaders, XOkapiHeaders.TENANT, null);
    if (tenantId == null) {
      log.error("handleUserGroupCreatingEvent:: tenant ID not found in headers");
      return;
    }
    KafkaEvent.EventType eventType = event.getType();
    event.setTenantIdHeaderValue(tenantId);

    if (eventType == KafkaEvent.EventType.CREATED) {
      processUserGroupCreateEvent(event);
    }

    if (eventType == KafkaEvent.EventType.UPDATED) {
      processUserGroupUpdateEvent(event);
    }
  }

  private void processUserGroupCreateEvent(KafkaEvent<UserGroup> event){
    log.debug("processUserGroupCreateEvent:: params: event={}", () -> event);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    log.info("processUserGroupCreateEvent:: consortiumId: {}, centralTenantId: {}",
      consortiumId, centralTenantId);

    if (!centralTenantId.equals(event.getTenantIdHeaderValue())) {
      log.info("processUserGroupCreateEvent: ignoring central tenant event");
      return;
    }
    processUserGroupForAllDataTenants(consortiumId, () -> userGroupService.create(
      event.getData().getNewVersion()));
  }

  private void processUserGroupUpdateEvent(KafkaEvent<UserGroup> event) {
    log.debug("processUserGroupUpdateEvent:: params: event={}", () -> event);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    log.info("processUserGroupUpdateEvent:: consortiumId: {}, centralTenantId: {}",
      consortiumId, centralTenantId);

    if (!centralTenantId.equals(event.getTenantIdHeaderValue())) {
      log.info("processUserGroupUpdateEvent: ignoring central tenant event");
      return;
    }
    processUserGroupForAllDataTenants(consortiumId, () -> userGroupService.update(
      event.getData().getNewVersion()));
  }

  private void processUserGroupForAllDataTenants(String consortiumId, Runnable action) {
    log.debug("processUserGroupForAllDataTenants:: params: consortiumId={}", consortiumId);
    consortiaService.getAllDataTenants(consortiumId).getTenants().stream()
      .filter(tenant -> !tenant.getIsCentral())
      .forEach(tenant -> systemUserScopedExecutionService.executeAsyncSystemUserScoped(
        tenant.getId(), action));
  }

  static String getHeaderValue(MessageHeaders headers, String headerName, String defaultValue) {
    log.debug("getHeaderValue:: headers: {}, headerName: {}, defaultValue: {}", () -> headers,
      () -> headerName, () -> defaultValue);
    var headerValue = headers.get(headerName);
    var value = headerValue == null
      ? defaultValue
      : new String((byte[]) headerValue, StandardCharsets.UTF_8);
    log.info("getHeaderValue:: header {} value is {}", headerName, value);
    return value;
  }
}
