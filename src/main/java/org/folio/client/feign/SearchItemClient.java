package org.folio.client.feign;

import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItemResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "search-item", url = "search/consortium/item",
  configuration = FeignClientConfiguration.class)
public interface SearchItemClient extends GetByQueryClient<SearchInstancesResponse> {

  @GetMapping("/{itemId}")
  SearchItemResponse searchItem(@PathVariable("itemId") String itemId);

}
