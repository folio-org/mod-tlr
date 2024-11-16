package org.folio.client.feign;

import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.folio.support.CqlQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "search", url = "search/instances", configuration = FeignClientConfiguration.class)
public interface SearchInstanceClient extends GetByQueryClient<SearchInstancesResponse> {

  @GetMapping
  SearchInstancesResponse searchInstances(@RequestParam("query") CqlQuery cql,
    @RequestParam("expandAll") Boolean expandAll);

  @GetMapping("?query=id=={instanceId}&expandAll=true")
  SearchInstancesResponse searchInstance(@PathVariable("instanceId") String instanceId);
}
