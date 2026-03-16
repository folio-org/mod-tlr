package org.folio.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.NotFoundRestClientAdapterDecorator;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
public class HttpClientConfiguration {

  /**
   * A dedicated HttpServiceProxyFactory with VALUES_ONLY URI encoding mode.
   * Marked @Primary so it is preferred when multiple HttpServiceProxyFactory beans exist.
   * VALUES_ONLY mode prevents Spring's RestClient from percent-encoding CQL operators
   * (e.g. == → %3D%3D) when they appear as @RequestParam values.
   * Applies NotFoundRestClientAdapterDecorator so 404 responses return null instead of throwing.
   */
  @Bean
  @Primary
  public HttpServiceProxyFactory tlrHttpServiceProxyFactory(
      @Qualifier("restClientBuilder") RestClient.Builder restClientBuilder) {
    var uriBuilderFactory = new DefaultUriBuilderFactory();
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
    // Clone the shared builder so we do not mutate the singleton instance
    var restClient = restClientBuilder.clone()
      .uriBuilderFactory(uriBuilderFactory)
      .build();
    return HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(restClient))
      .exchangeAdapterDecorator(NotFoundRestClientAdapterDecorator::new)
      .build();
  }

  /**
   * A second HttpServiceProxyFactory without NotFoundRestClientAdapterDecorator.
   * Used exclusively for CirculationErrorForwardingClient, which must propagate all
   * HTTP error codes (including 404) directly to the caller instead of returning null.
   */
  @Bean
  public HttpServiceProxyFactory tlrHttpServiceProxyFactoryNoNotFoundHandling(
      @Qualifier("restClientBuilder") RestClient.Builder restClientBuilder) {
    var uriBuilderFactory = new DefaultUriBuilderFactory();
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
    var restClient = restClientBuilder.clone()
      .uriBuilderFactory(uriBuilderFactory)
      .build();
    return HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(restClient))
      .build();
  }

  @Bean
  public AddressTypeClient addressTypeClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(AddressTypeClient.class);
  }

  @Bean
  public CheckOutClient checkOutClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CheckOutClient.class);
  }

  @Bean
  public CirculationClient circulationClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationClient.class);
  }

  @Bean
  public CirculationErrorForwardingClient circulationErrorForwardingClient(
      @Qualifier("tlrHttpServiceProxyFactoryNoNotFoundHandling") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationErrorForwardingClient.class);
  }

  @Bean
  public CirculationItemClient circulationItemClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(CirculationItemClient.class);
  }

  @Bean
  public ConsortiaClient consortiaClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiaClient.class);
  }

  @Bean
  public ConsortiaConfigurationClient consortiaConfigurationClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiaConfigurationClient.class);
  }

  @Bean
  public ConsortiumSearchClient consortiumSearchClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiumSearchClient.class);
  }

  @Bean
  public DcbEcsTransactionClient dcbEcsTransactionClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(DcbEcsTransactionClient.class);
  }

  @Bean
  public DcbTransactionClient dcbTransactionClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(DcbTransactionClient.class);
  }

  @Bean
  public DepartmentClient departmentClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(DepartmentClient.class);
  }

  @Bean
  public HoldingClient holdingClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(HoldingClient.class);
  }

  @Bean
  public InstanceClient instanceClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(InstanceClient.class);
  }

  @Bean
  public ItemClient itemClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ItemClient.class);
  }

  @Bean
  public LoanPolicyClient loanPolicyClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LoanPolicyClient.class);
  }

  @Bean
  public LoanStorageClient loanStorageClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LoanStorageClient.class);
  }

  @Bean
  public LoanTypeClient loanTypeClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LoanTypeClient.class);
  }

  @Bean
  public LocationCampusClient locationCampusClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationCampusClient.class);
  }

  @Bean
  public LocationClient locationClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationClient.class);
  }

  @Bean
  public LocationInstitutionClient locationInstitutionClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationInstitutionClient.class);
  }

  @Bean
  public LocationLibraryClient locationLibraryClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(LocationLibraryClient.class);
  }

  @Bean
  public MaterialTypeClient materialTypeClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(MaterialTypeClient.class);
  }

  @Bean
  public RequestCirculationClient requestCirculationClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(RequestCirculationClient.class);
  }

  @Bean
  public RequestStorageClient requestStorageClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(RequestStorageClient.class);
  }

  @Bean
  public SearchInstanceClient searchInstanceClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(SearchInstanceClient.class);
  }

  @Bean
  public ServicePointClient servicePointClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(ServicePointClient.class);
  }

  @Bean
  public UserClient userClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(UserClient.class);
  }

  @Bean
  public UserGroupClient userGroupClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(UserGroupClient.class);
  }

  @Bean
  public UserTenantsClient userTenantsClient(
      @Qualifier("tlrHttpServiceProxyFactory") HttpServiceProxyFactory factory) {
    return factory.createClient(UserTenantsClient.class);
  }
}
