package org.folio.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ResourceNotFoundException extends RuntimeException {
  private static final String MESSAGE_TEMPLATE = "%s with ID %s was not found";
  private final ResourceType resourceType;
  private final String resourceId;

  public ResourceNotFoundException(ResourceType resourceType, String resourceId, Throwable cause) {
    super(cause);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  @Override
  public String getMessage() {
    return String.format(MESSAGE_TEMPLATE, resourceType.getValue(), resourceId);
  }
}
