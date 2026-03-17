package org.folio.client.config;

import org.folio.client.AddressTypeClient;
import org.folio.client.CheckOutClient;
import org.folio.client.CirculationClient;
import org.folio.client.CirculationErrorForwardingClient;
import org.folio.client.CirculationItemClient;
import org.folio.client.ConsortiaClient;
import org.folio.client.ConsortiaConfigurationClient;
import org.folio.client.ConsortiumSearchClient;
import org.folio.client.DcbEcsTransactionClient;
import org.folio.client.DcbTransactionClient;
import org.folio.client.DepartmentClient;
import org.folio.client.HoldingClient;
import org.folio.client.InstanceClient;
import org.folio.client.ItemClient;
import org.folio.client.LoanPolicyClient;
import org.folio.client.LoanStorageClient;
import org.folio.client.LoanTypeClient;
import org.folio.client.LocationCampusClient;
import org.folio.client.LocationClient;
import org.folio.client.LocationInstitutionClient;
import org.folio.client.LocationLibraryClient;
import org.folio.client.MaterialTypeClient;
import org.folio.client.RequestCirculationClient;
import org.folio.client.RequestStorageClient;
import org.folio.client.SearchInstanceClient;
import org.folio.client.ServicePointClient;
import org.folio.client.UserClient;
import org.folio.client.UserGroupClient;
import org.folio.client.UserTenantsClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfiguration {

  // same as default factory provided by folio-spring-support, but does not swallow 404s
  @Bean
  @Fallback
  public HttpServiceProxyFactory errorForwardingHttpServiceProxyFactory(RestClient.Builder builder) {
    return HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(builder.build()))
      .build();
  }

  @Bean
  public AddressTypeClient addressTypeClient(HttpServiceProxyFactory factory) {
    return factory.createClient(AddressTypeClient.class);
  }

  @Bean
  public CheckOutClient checkOutClient(
      @Qualifier("errorForwardingHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CheckOutClient.class);
  }

  @Bean
  public CirculationClient circulationClient(HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationClient.class);
  }

  @Bean
  public CirculationErrorForwardingClient circulationErrorForwardingClient(
      @Qualifier("errorForwardingHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationErrorForwardingClient.class);
  }

  @Bean
  public CirculationItemClient circulationItemClient(HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationItemClient.class);
  }

  @Bean
  public ConsortiaClient consortiaClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiaClient.class);
  }

  @Bean
  public ConsortiaConfigurationClient consortiaConfigurationClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiaConfigurationClient.class);
  }

  @Bean
  public ConsortiumSearchClient consortiumSearchClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiumSearchClient.class);
  }

  @Bean
  public DcbEcsTransactionClient dcbEcsTransactionClient(HttpServiceProxyFactory factory) {
    return factory.createClient(DcbEcsTransactionClient.class);
  }

  @Bean
  public DcbTransactionClient dcbTransactionClient(HttpServiceProxyFactory factory) {
    return factory.createClient(DcbTransactionClient.class);
  }

  @Bean
  public DepartmentClient departmentClient(HttpServiceProxyFactory factory) {
    return factory.createClient(DepartmentClient.class);
  }

  @Bean
  public HoldingClient holdingClient(HttpServiceProxyFactory factory) {
    return factory.createClient(HoldingClient.class);
  }

  @Bean
  public InstanceClient instanceClient(HttpServiceProxyFactory factory) {
    return factory.createClient(InstanceClient.class);
  }

  @Bean
  public ItemClient itemClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ItemClient.class);
  }

  @Bean
  public LoanPolicyClient loanPolicyClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LoanPolicyClient.class);
  }

  @Bean
  public LoanStorageClient loanStorageClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LoanStorageClient.class);
  }

  @Bean
  public LoanTypeClient loanTypeClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LoanTypeClient.class);
  }

  @Bean
  public LocationCampusClient locationCampusClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LocationCampusClient.class);
  }

  @Bean
  public LocationClient locationClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LocationClient.class);
  }

  @Bean
  public LocationInstitutionClient locationInstitutionClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LocationInstitutionClient.class);
  }

  @Bean
  public LocationLibraryClient locationLibraryClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LocationLibraryClient.class);
  }

  @Bean
  public MaterialTypeClient materialTypeClient(HttpServiceProxyFactory factory) {
    return factory.createClient(MaterialTypeClient.class);
  }

  @Bean
  public RequestCirculationClient requestCirculationClient(HttpServiceProxyFactory factory) {
    return factory.createClient(RequestCirculationClient.class);
  }

  @Bean
  public RequestStorageClient requestStorageClient(HttpServiceProxyFactory factory) {
    return factory.createClient(RequestStorageClient.class);
  }

  @Bean
  public SearchInstanceClient searchInstanceClient(HttpServiceProxyFactory factory) {
    return factory.createClient(SearchInstanceClient.class);
  }

  @Bean
  public ServicePointClient servicePointClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ServicePointClient.class);
  }

  @Bean
  public UserClient userClient(HttpServiceProxyFactory factory) {
    return factory.createClient(UserClient.class);
  }

  @Bean
  public UserGroupClient userGroupClient(HttpServiceProxyFactory factory) {
    return factory.createClient(UserGroupClient.class);
  }

  @Bean
  public UserTenantsClient userTenantsClient(HttpServiceProxyFactory factory) {
    return factory.createClient(UserTenantsClient.class);
  }
}
