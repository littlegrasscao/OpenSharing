package io.opensharing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.auth.AdminAuthenticationFilter;
import io.opensharing.auth.Secrets;
import io.opensharing.principal.PrincipalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Mounts provider-admin authentication in front of the admin API. */
@Configuration
public class AdminFilterConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AdminFilterConfiguration.class);

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
    registration.addUrlPatterns(pattern(properties.getAdmin().getBasePath()));
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

  private static String pattern(String basePath) {
    String normalized =
        basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    return normalized + "/*";
  }
}
