package org.folio.client.feign;

import org.folio.domain.dto.Libraries;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "libraries", url = "location-units/libraries", configuration = FeignClientConfiguration.class)
public interface LocationLibraryClient extends GetByQueryClient<Libraries> {

}
