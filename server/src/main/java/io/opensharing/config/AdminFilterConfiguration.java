package io.opensharing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.auth.AdminAuthenticationFilter;
import io.opensharing.principal.PrincipalStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Mounts provider-admin authentication in front of the admin API. */
@Configuration
public class AdminFilterConfiguration {

  @Bean
  public FilterRegistrationBean<AdminAuthenticationFilter> adminAuthentication(
      PrincipalStore principals, ObjectMapper objectMapper, OpenSharingProperties properties) {
    FilterRegistrationBean<AdminAuthenticationFilter> registration =
        new FilterRegistrationBean<>(new AdminAuthenticationFilter(principals, objectMapper));
    registration.addUrlPatterns(properties.getApi().getBasePath() + "/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
