package org.folio.service.impl;

import java.util.function.Consumer;

import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserTenant;
import org.folio.service.ConsortiaService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.UserGroupService;
import org.folio.service.UserTenantsService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
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
  public void handle(KafkaEvent<UserGroup> event) {
    log.info("handle:: Processing user group event: {}", () -> event);

    KafkaEvent.EventType eventType = event.getType();
    if (eventType == KafkaEvent.EventType.CREATED) {
      processUserGroupCreateEvent(event);
    }
    if (eventType == KafkaEvent.EventType.UPDATED) {
      processUserGroupUpdateEvent(event);
    }
  }

  private void processUserGroupCreateEvent(KafkaEvent<UserGroup> event){
    log.debug("processUserGroupCreateEvent:: params: event={}", () -> event);
    processUserGroupEvent(event, userGroupService::create);
  }

  private void processUserGroupUpdateEvent(KafkaEvent<UserGroup> event) {
    log.debug("processUserGroupUpdateEvent:: params: event={}", () -> event);
    processUserGroupEvent(event, userGroupService::update);
  }

  private void processUserGroupEvent(KafkaEvent<UserGroup> event,
    Consumer<UserGroup> userGroupConsumer) {

    log.debug("processUserGroupEvent:: params: event={}", () -> event);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    if (firstUserTenant == null) {
      log.info("processUserGroupEvent: Failed to get user-tenants info");
      return;
    }
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    log.info("processUserGroupEvent:: consortiumId: {}, centralTenantId: {}",
      consortiumId, centralTenantId);

    if (!centralTenantId.equals(event.getTenantIdHeaderValue())) {
      log.info("processUserGroupEvent: Ignoring non-central tenant event");
      return;
    }
    processUserGroupForAllDataTenants(consortiumId,
      () -> userGroupConsumer.accept(event.getData().getNewVersion()));
  }

  private void processUserGroupForAllDataTenants(String consortiumId, Runnable action) {
    log.debug("processUserGroupForAllDataTenants:: params: consortiumId={}", consortiumId);
    consortiaService.getAllConsortiumTenants(consortiumId).getTenants().stream()
      .filter(tenant -> !tenant.getIsCentral())
      .forEach(tenant -> systemUserScopedExecutionService.executeAsyncSystemUserScoped(
        tenant.getId(), action));
  }
}
