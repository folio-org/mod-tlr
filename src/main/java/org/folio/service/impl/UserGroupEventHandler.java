package org.folio.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

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

    List<String> tenantIds = getHeaderValue(messageHeaders, XOkapiHeaders.TENANT, null);
    if (tenantIds.isEmpty()) {
      log.error("handleUserGroupCreatingEvent:: tenant ID not found in headers");
      return;
    }
    String requestedTenantId = tenantIds.get(0);
    KafkaEvent.EventType eventType = event.getType();
    if (eventType == KafkaEvent.EventType.CREATED) {
      processUserGroupCreatingEvent(event, requestedTenantId);
    }
    if (eventType == KafkaEvent.EventType.UPDATED) {
      processUserGroupUpdatingEvent(event, requestedTenantId);
    }
  }

  private void processUserGroupCreatingEvent(KafkaEvent<UserGroup> event, String requestedTenantId){
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    log.info("handleUserGroupCreatingEvent:: consortiumId: {}, centralTenantId: {}, requestedTenantId: {}",
      consortiumId, centralTenantId, requestedTenantId);

    if (!centralTenantId.equals(requestedTenantId)) {
      return;
    }
    log.info("handleUserGroupCreatingEvent: received event from centralTenant: {}", centralTenantId);
    processUserGroupForAllDataTenants(consortiumId, () -> userGroupService.create(
      event.getData().getNewVersion()));
  }

  private void processUserGroupUpdatingEvent(KafkaEvent<UserGroup> event, String requestedTenantId) {
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    log.info("handleUserGroupUpdatingEvent:: consortiumId: {}, centralTenantId: {}, requestedTenantId: {}",
      consortiumId, centralTenantId, requestedTenantId);

    if (!centralTenantId.equals(requestedTenantId)) {
      return;
    }
    log.info("handleUserGroupUpdatingEvent: received event from centralTenant: {}", centralTenantId);
    processUserGroupForAllDataTenants(consortiumId, () -> userGroupService.update(
      event.getData().getNewVersion()));
  }

  private void processUserGroupForAllDataTenants(String consortiumId,
    Runnable action) {

    consortiaService.getAllDataTenants(consortiumId).getTenants().stream()
      .filter(tenant -> !tenant.getIsCentral())
      .forEach(tenant -> systemUserScopedExecutionService.executeAsyncSystemUserScoped(
        tenant.getId(), action));
  }

  static List<String> getHeaderValue(MessageHeaders headers, String headerName, String defaultValue) {
    log.info("getHeaderValue:: headers: {}, headerName: {}, defaultValue: {}", () -> headers,
      () -> headerName, () -> defaultValue);
    var headerValue = headers.get(headerName);
    var value = headerValue == null
      ? defaultValue
      : new String((byte[]) headerValue, StandardCharsets.UTF_8);
    return value == null ? Collections.emptyList() : Collections.singletonList(value);
  }
}
