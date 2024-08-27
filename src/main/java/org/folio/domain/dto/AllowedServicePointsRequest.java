package org.folio.domain.dto;

import java.util.Optional;
import java.util.UUID;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class AllowedServicePointsRequest {
  private final RequestOperation operation;
  private final String requesterId;
  private final String instanceId;
  private final String requestId;

  public AllowedServicePointsRequest(String operation, UUID requesterId, UUID instanceId,
    UUID requestId) {

    this.operation = RequestOperation.from(operation);
    this.requesterId = asString(requesterId);
    this.instanceId = asString(instanceId);
    this.requestId = asString(requestId);
  }

  private static String asString(UUID uuid) {
    return Optional.ofNullable(uuid)
      .map(UUID::toString)
      .orElse(null);
  }

}
