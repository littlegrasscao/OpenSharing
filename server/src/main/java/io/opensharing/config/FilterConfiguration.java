package io.opensharing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.auth.AdminAuthenticationFilter;
import io.opensharing.principal.PrincipalStore;
import io.opensharing.recipient.RecipientAuthenticationFilter;
import io.opensharing.recipient.RecipientTokenService;
import io.opensharing.runtime.ProviderIdentityResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Mounts authentication in front of the two request surfaces: recipient bearer tokens on the
 * protocol endpoints, principal bearer tokens on the provider-admin endpoints. Activation URLs are
 * deliberately left unauthenticated — the nonce in the URL is the credential.
 */
@Configuration
public class FilterConfiguration {

  @Bean
  public FilterRegistrationBean<RecipientAuthenticationFilter> recipientAuthentication(
      RecipientTokenService tokenService,
      ObjectMapper objectMapper,
      OpenSharingProperties properties) {
    FilterRegistrationBean<RecipientAuthenticationFilter> registration =
        new FilterRegistrationBean<>(
            new RecipientAuthenticationFilter(tokenService, objectMapper));
    registration.addUrlPatterns(properties.getProtocolPrefix() + "/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<AdminAuthenticationFilter> adminAuthentication(
      PrincipalStore principals,
      ObjectMapper objectMapper,
      ObjectProvider<ProviderIdentityResolver> identityResolver,
      OpenSharingProperties properties) {
    FilterRegistrationBean<AdminAuthenticationFilter> registration =
        new FilterRegistrationBean<>(
            new AdminAuthenticationFilter(
                principals, objectMapper, identityResolver.getIfAvailable()));
    registration.addUrlPatterns(properties.getAdmin().getBasePath() + "/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
