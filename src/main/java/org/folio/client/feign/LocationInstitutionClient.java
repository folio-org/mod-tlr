package org.folio.client.feign;

import org.folio.domain.dto.Institutions;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "institutions", url = "location-units/institutions", configuration = FeignClientConfiguration.class)
public interface LocationInstitutionClient extends GetByQueryClient<Institutions> {

}
