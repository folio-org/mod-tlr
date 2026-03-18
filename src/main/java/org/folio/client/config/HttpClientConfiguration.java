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
  public AddressTypeClient addressTypeClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(AddressTypeClient.class);
  }

  @Bean
  public CheckOutClient checkOutClient(
      @Qualifier("errorForwardingHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CheckOutClient.class);
  }

  @Bean
  public CirculationClient circulationClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationClient.class);
  }

  @Bean
  public CirculationErrorForwardingClient circulationErrorForwardingClient(
      @Qualifier("errorForwardingHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationErrorForwardingClient.class);
  }

  @Bean
  public CirculationItemClient circulationItemClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationItemClient.class);
  }

  @Bean
  public ConsortiaClient consortiaClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiaClient.class);
  }

  @Bean
  public ConsortiaConfigurationClient consortiaConfigurationClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiaConfigurationClient.class);
  }

  @Bean
  public ConsortiumSearchClient consortiumSearchClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiumSearchClient.class);
  }

  @Bean
  public DcbEcsTransactionClient dcbEcsTransactionClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(DcbEcsTransactionClient.class);
  }

  @Bean
  public DcbTransactionClient dcbTransactionClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(DcbTransactionClient.class);
  }

  @Bean
  public DepartmentClient departmentClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(DepartmentClient.class);
  }

  @Bean
  public HoldingClient holdingClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(HoldingClient.class);
  }

  @Bean
  public InstanceClient instanceClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(InstanceClient.class);
  }

  @Bean
  public ItemClient itemClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ItemClient.class);
  }

  @Bean
  public LoanPolicyClient loanPolicyClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LoanPolicyClient.class);
  }

  @Bean
  public LoanStorageClient loanStorageClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LoanStorageClient.class);
  }

  @Bean
  public LoanTypeClient loanTypeClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LoanTypeClient.class);
  }

  @Bean
  public LocationCampusClient locationCampusClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationCampusClient.class);
  }

  @Bean
  public LocationClient locationClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationClient.class);
  }

  @Bean
  public LocationInstitutionClient locationInstitutionClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationInstitutionClient.class);
  }

  @Bean
  public LocationLibraryClient locationLibraryClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationLibraryClient.class);
  }

  @Bean
  public MaterialTypeClient materialTypeClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(MaterialTypeClient.class);
  }

  @Bean
  public RequestCirculationClient requestCirculationClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(RequestCirculationClient.class);
  }

  @Bean
  public RequestStorageClient requestStorageClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(RequestStorageClient.class);
  }

  @Bean
  public SearchInstanceClient searchInstanceClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(SearchInstanceClient.class);
  }

  @Bean
  public ServicePointClient servicePointClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ServicePointClient.class);
  }

  @Bean
  public UserClient userClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(UserClient.class);
  }

  @Bean
  public UserGroupClient userGroupClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(UserGroupClient.class);
  }

  @Bean
  public UserTenantsClient userTenantsClient(
    @Qualifier("httpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(UserTenantsClient.class);
  }
}
