package org.folio.service.impl;

import org.folio.domain.dto.UserGroup;
import org.folio.service.ConsortiaService;
import org.folio.service.ConsortiumService;
import org.folio.service.UserGroupService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.kafka.EventType;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class UserGroupEventHandler extends AbstractCentralTenantEventHandler<UserGroup> {

  private final UserGroupService userGroupService;

  public UserGroupEventHandler(ConsortiumService consortiumService,
    ConsortiaService consortiaService,
    SystemUserScopedExecutionService systemUserScopedExecutionService,
    UserGroupService userGroupService) {

    super(consortiumService, consortiaService, systemUserScopedExecutionService);
    this.userGroupService = userGroupService;
  }

  @Override
  public void handle(KafkaEvent<UserGroup> event){
    log.info("handle:: Processing user group event: {}", () -> event);
    if (event.getGenericType() == EventType.CREATE) {
      processEvent(event, userGroupService::create);
    } else if (event.getGenericType() == EventType.UPDATE) {
      processEvent(event, userGroupService::update);
    }
  }
}
