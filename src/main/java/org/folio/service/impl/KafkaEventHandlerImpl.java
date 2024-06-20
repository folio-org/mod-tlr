package org.folio.service.impl;

import static org.folio.support.KafkaEvent.EventType.UPDATED;
import static org.folio.support.KafkaEvent.ITEM_ID;
import static org.folio.support.KafkaEvent.getUUIDFromNode;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserTenant;
import org.folio.service.ConsortiaService;
import org.folio.service.EcsTlrService;
import org.folio.service.KafkaEventHandler;
import org.folio.service.UserGroupService;
import org.folio.service.UserTenantsService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class KafkaEventHandlerImpl implements KafkaEventHandler {

  private final EcsTlrService ecsTlrService;
  private final UserTenantsService userTenantsService;
  private final ConsortiaService consortiaService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final UserGroupService userGroupService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void handleRequestEvent(KafkaEvent event) {
    log.info("handleRequestEvent:: processing request event: {}", () -> event);
    if (event.getEventType() == UPDATED && event.hasNewNode() && event.getNewNode().has(ITEM_ID)) {
      log.info("handleRequestEvent:: handling request event: {}", () -> event);
      ecsTlrService.handleSecondaryRequestUpdate(getUUIDFromNode(event.getNewNode(), "id"),
        getUUIDFromNode(event.getNewNode(), ITEM_ID));
    } else {
      log.info("handleRequestEvent:: ignoring event: {}", () -> event);
    }
    log.info("handleRequestEvent:: request event processed: {}", () -> event);
  }

  @Override
  public void handleUserGroupCreatingEvent(KafkaEvent event, MessageHeaders messageHeaders) {
    log.info("handleUserGroupCreatingEvent:: processing request event: {}, messageHeaders: {}",
      () -> event, () -> messageHeaders);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    String requestedTenantId = getHeaderValue(messageHeaders, XOkapiHeaders.TENANT, null).get(0);
    log.info("handleUserGroupCreatingEvent:: consortiumId: {}, centralTenantId: {}, requestedTenantId: {}",
      consortiumId, centralTenantId, requestedTenantId);

    if (centralTenantId.equals(requestedTenantId)) {
      log.info("handleUserGroupCreatingEvent: received event from centralTenant: {}", centralTenantId);

      consortiaService.getAllDataTenants(consortiumId).getTenants().stream()
        .filter(tenant -> !tenant.getIsCentral())
        .forEach(tenant -> systemUserScopedExecutionService.executeAsyncSystemUserScoped(
          tenant.getId(), () -> userGroupService.create(convertJsonNodeToUserGroup(event.getNewNode()))));
    }
  }

  @Override
  public void handleUserGroupUpdatingEvent(KafkaEvent event, MessageHeaders messageHeaders) {
    log.info("handleUserGroupUpdatingEvent:: processing request event: {}, messageHeaders: {}",
      () -> event, () -> messageHeaders);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    String consortiumId = firstUserTenant.getConsortiumId();
    String centralTenantId = firstUserTenant.getCentralTenantId();
    String requestedTenantId = getHeaderValue(messageHeaders, XOkapiHeaders.TENANT, null).get(0);
    log.info("handleUserGroupUpdatingEvent:: consortiumId: {}, centralTenantId: {}, requestedTenantId: {}",
      consortiumId, centralTenantId, requestedTenantId);

    if (centralTenantId.equals(requestedTenantId)) {
      log.info("handleUserGroupUpdatingEvent: received event from centralTenant: {}", centralTenantId);

      consortiaService.getAllDataTenants(consortiumId).getTenants().stream()
        .filter(tenant -> !tenant.getIsCentral())
        .forEach(tenant -> systemUserScopedExecutionService.executeAsyncSystemUserScoped(
          tenant.getId(), () -> userGroupService.update(convertJsonNodeToUserGroup(event.getNewNode()))));
    }
  }

  private UserGroup convertJsonNodeToUserGroup(JsonNode jsonNode) {
    try {
      return objectMapper.treeToValue(jsonNode, UserGroup.class);
    } catch (JsonProcessingException e) {
      log.error("convertJsonNodeToUserGroup:: cannot convert jsonNode: {}", () -> jsonNode);
      throw new IllegalStateException("Cannot convert jsonNode from event to UserGroup");
    }
  }

  static List<String> getHeaderValue(MessageHeaders headers, String headerName, String defaultValue) {
    log.debug("getHeaderValue:: headers: {}, headerName: {}, defaultValue: {}", () -> headers,
      () -> headerName, () -> defaultValue);
    var headerValue = headers.get(headerName);
    var value = headerValue == null
      ? defaultValue
      : new String((byte[]) headerValue, StandardCharsets.UTF_8);
    return value == null ? Collections.emptyList() : Collections.singletonList(value);
  }
}
