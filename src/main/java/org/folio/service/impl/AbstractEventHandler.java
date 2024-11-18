package org.folio.service.impl;

import java.util.function.Consumer;

import org.folio.domain.dto.UserTenant;
import org.folio.service.ConsortiaService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.UserTenantsService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Log4j2
public abstract class AbstractEventHandler<T> implements KafkaEventHandler<T> {

  protected final UserTenantsService userTenantsService;
  protected final ConsortiaService consortiaService;
  protected final SystemUserScopedExecutionService systemUserScopedExecutionService;

  protected void processEvent(KafkaEvent<T> event, Consumer<T> eventConsumer) {
    log.debug("processEvent:: params: event={}", () -> event);
    log.info("processEvent:: params: event={}", () -> event.getData());
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    if (firstUserTenant == null) {
      log.info("processEvent: Failed to get user-tenants info");
      return;
    }
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    log.info("processEvent:: consortiumId: {}, centralTenantId: {}", consortiumId, centralTenantId);

    if (!centralTenantId.equals(event.getTenantIdHeaderValue())) {
      log.info("processEvent: Ignoring non-central tenant event");
      return;
    }
    processForAllDataTenants(consortiumId, () -> eventConsumer.accept(event.getData().getNewVersion()));
  }

  private void processForAllDataTenants(String consortiumId, Runnable action) {
    log.debug("processForAllDataTenants:: params: consortiumId={}", consortiumId);
    consortiaService.getAllConsortiumTenants(consortiumId).getTenants().stream()
      .filter(tenant -> !tenant.getIsCentral())
      .forEach(tenant -> systemUserScopedExecutionService.executeAsyncSystemUserScoped(tenant.getId(), action));
  }
}
