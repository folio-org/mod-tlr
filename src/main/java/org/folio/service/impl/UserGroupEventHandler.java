package org.folio.service.impl;

import org.folio.domain.dto.UserGroup;
import org.folio.service.ConsortiaService;
import org.folio.service.UserGroupService;
import org.folio.service.UserTenantsService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class UserGroupEventHandler extends AbstractCentralTenantEventHandler<UserGroup> {

  private final UserGroupService userGroupService;

  public UserGroupEventHandler(UserTenantsService userTenantsService,
    ConsortiaService consortiaService,
    SystemUserScopedExecutionService systemUserScopedExecutionService,
    UserGroupService userGroupService) {

    super(userTenantsService, consortiaService, systemUserScopedExecutionService);
    this.userGroupService = userGroupService;
  }

  @Override
  public void handle(KafkaEvent<UserGroup> event){
    log.info("handle:: Processing user group event: {}", () -> event);
    if (event.getType() == KafkaEvent.EventType.CREATED) {
      processEvent(event, userGroupService::create);
    } else if (event.getType() == KafkaEvent.EventType.UPDATED) {
      processEvent(event, userGroupService::update);
    }
  }
}
