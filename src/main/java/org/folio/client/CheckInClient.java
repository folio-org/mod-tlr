package org.folio.client;

import org.folio.domain.dto.CheckInRequest;
import org.folio.domain.dto.CheckInResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "circulation")
public interface CheckInClient {

  @PostExchange("/check-in-by-barcode")
  CheckInResponse checkIn(@RequestBody CheckInRequest request);
}
