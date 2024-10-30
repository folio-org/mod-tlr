package org.folio.client.feign;

import org.folio.domain.dto.InventoryItem;
import org.folio.domain.dto.Items;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "items", url = "item-storage/items", configuration = FeignClientConfiguration.class)
public interface ItemClient extends GetByQueryClient<Items> {

  @GetMapping("/{id}")
  InventoryItem get(@PathVariable String id);
}
