package org.folio.service.impl;

import org.folio.domain.dto.User;
import org.folio.service.ConsortiaService;
import org.folio.service.UserService;
import org.folio.service.UserTenantsService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.kafka.EventType;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class UserEventHandler extends AbstractCentralTenantEventHandler<User> {

  private final UserService userService;

  public UserEventHandler(UserTenantsService userTenantsService,
    ConsortiaService consortiaService,
    SystemUserScopedExecutionService systemUserScopedExecutionService,
    UserService userService) {

    super(userTenantsService, consortiaService, systemUserScopedExecutionService);
    this.userService = userService;
  }

  @Override
  public void handle(KafkaEvent<User> event) {
    log.info("handle:: Processing user event: {}", () -> event);
    if (event.getGenericType() == EventType.UPDATE) {
      processEvent(event, userService::update);
    }
  }
}

