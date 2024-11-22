package org.folio.client.feign;

import org.folio.domain.dto.MaterialTypes;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "material-types", url = "material-types", configuration = FeignClientConfiguration.class)
public interface MaterialTypeClient extends GetByQueryClient<MaterialTypes> {

}
