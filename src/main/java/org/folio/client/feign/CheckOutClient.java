package org.folio.client.feign;

import org.folio.client.feign.config.ErrorForwardingFeignClientConfiguration;
import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "check-out", url = "circulation",
  configuration = { FeignClientConfiguration.class, ErrorForwardingFeignClientConfiguration.class })
public interface CheckOutClient {

  @PostMapping("/check-out-by-barcode")
  CheckOutResponse checkOut(@RequestBody CheckOutRequest request);
}
