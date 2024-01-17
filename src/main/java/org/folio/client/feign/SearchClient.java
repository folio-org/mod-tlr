package org.folio.client.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.folio.model.ResultList;
import org.folio.spring.config.FeignClientConfiguration;
import org.folio.support.CqlQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "search", configuration = FeignClientConfiguration.class)
public interface SearchClient {

  @GetMapping("/instances")
  ResultList<Instance> searchInstances(@RequestParam("query") CqlQuery cql, @RequestParam("expandAll") Boolean expandAll);

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  class Instance {
    private String id;
    private String tenantId;
  }
}
