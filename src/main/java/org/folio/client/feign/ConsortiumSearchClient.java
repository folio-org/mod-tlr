package org.folio.client.feign;

import org.folio.domain.dto.BatchIds;
import org.folio.domain.dto.ConsortiumItems;
import org.folio.domain.dto.SearchItemResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "consortium-search", url = "search/consortium",
  configuration = FeignClientConfiguration.class)
public interface ConsortiumSearchClient {

  @GetMapping("/item/{itemId}")
  SearchItemResponse searchItem(@PathVariable("itemId") String itemId);

  @PostMapping("/batch/items")
  ConsortiumItems searchItems(@RequestBody BatchIds batchIds);

}
