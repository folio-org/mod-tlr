package org.folio.client.feign;

import org.folio.domain.dto.LoanTypes;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "loan-types", url = "loan-types", configuration = FeignClientConfiguration.class)
public interface LoanTypeClient extends GetByQueryClient<LoanTypes> {

}
