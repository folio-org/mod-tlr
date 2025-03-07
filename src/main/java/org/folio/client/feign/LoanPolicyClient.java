package org.folio.client.feign;

import org.folio.client.feign.config.ErrorForwardingFeignClientConfiguration;
import org.folio.domain.dto.LoanPolicy;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "loan-policies", url = "loan-policy-storage/loan-policies",
  configuration = { FeignClientConfiguration.class, ErrorForwardingFeignClientConfiguration.class })
public interface LoanPolicyClient extends GetByQueryClient<LoanPolicy> {

  @GetMapping("/{id}")
  LoanPolicy get(@PathVariable String id);

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  LoanPolicy post(@RequestBody LoanPolicy loanPolicy);
}
