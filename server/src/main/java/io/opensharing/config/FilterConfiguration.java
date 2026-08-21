package io.opensharing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.auth.AdminAuthenticationFilter;
import io.opensharing.auth.Secrets;
import io.opensharing.principal.PrincipalStore;
import io.opensharing.recipient.RecipientAuthenticationFilter;
import io.opensharing.recipient.RecipientTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(FilterConfiguration.class);

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
      PrincipalStore principals, ObjectMapper objectMapper, OpenSharingProperties properties) {
    FilterRegistrationBean<AdminAuthenticationFilter> registration =
        new FilterRegistrationBean<>(
            new AdminAuthenticationFilter(
                principals,
                resolveBootstrapToken(properties),
                properties.getAdmin().getBasePath(),
                objectMapper));
    registration.addUrlPatterns(properties.getAdmin().getBasePath() + "/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  private String resolveBootstrapToken(OpenSharingProperties properties) {
    String configured = properties.getAdmin().getBootstrapToken();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    String generated = Secrets.newToken();
    properties.getAdmin().setBootstrapToken(generated);
    log.warn(
        "No opensharing.admin.bootstrap-token configured. Generated one for this run:\n  {}\nIt is "
            + "the only credential that may POST /principals, so register a principal with it and "
            + "authenticate as that principal thereafter. Set opensharing.admin.bootstrap-token (or "
            + "OPENSHARING_ADMIN_BOOTSTRAP_TOKEN) to keep it stable across restarts.",
        generated);
    return generated;
  }
}
