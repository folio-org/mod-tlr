package org.folio.client.feign;

import org.folio.domain.dto.Campuses;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "campuses", url = "location-units/campuses", configuration = FeignClientConfiguration.class)
public interface LocationCampusClient extends GetByQueryClient<Campuses> {

}
