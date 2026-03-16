package org.folio.client;

import org.folio.domain.dto.CheckOutDryRunRequest;
import org.folio.domain.dto.CheckOutDryRunResponse;
import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange(url = "circulation")
public interface CheckOutClient {

  @PostExchange("/check-out-by-barcode")
  CheckOutResponse checkOut(@RequestBody CheckOutRequest request);

  @PostExchange("/check-out-by-barcode-dry-run")
  CheckOutDryRunResponse checkOutDryRun(@RequestBody CheckOutDryRunRequest request, @RequestHeader Map<String, String> headers);
}
