package org.folio.domain;

import org.folio.domain.dto.Request;

public record RequestWrapper(Request request, String tenantId) {
}
