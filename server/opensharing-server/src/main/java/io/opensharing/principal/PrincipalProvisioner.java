package io.opensharing.principal;

import io.opensharing.config.OpenSharingProperties;
import io.opensharing.runtime.ConditionalOnHostingMode;
import io.opensharing.runtime.HostingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registers the provider principals named in configuration when the server starts.
 *
 * <p>There is no admin API to create them: configuration is the source of truth, and a restart with an
 * updated token is how a credential is rotated.
 */
@Component
@ConditionalOnHostingMode(HostingMode.STANDALONE)
public class PrincipalProvisioner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PrincipalProvisioner.class);

  private final PrincipalStore principals;
  private final List<OpenSharingProperties.Admin.Principal> configured;

  public PrincipalProvisioner(PrincipalStore principals, OpenSharingProperties properties) {
    this.principals = principals;
    this.configured = properties.getAdmin().getPrincipals();
  }

  @Override
  public void run(ApplicationArguments args) {
    if (configured.isEmpty()) {
      log.warn(
          "No opensharing.admin.principals configured; no provider principals were registered");
      return;
    }
    Set<String> seen = new HashSet<>();
    for (OpenSharingProperties.Admin.Principal entry : configured) {
      String name = entry.getName();
      if (name == null || name.isBlank()) {
        throw new IllegalStateException(
            "opensharing.admin.principals contains an entry with a blank name");
      }
      String normalized = name.trim().toLowerCase();
      if (!seen.add(normalized)) {
        throw new IllegalStateException(
            "opensharing.admin.principals lists '" + name + "' more than once");
      }
      PrincipalType type = entry.getType() == null ? PrincipalType.USER : entry.getType();
      principals.provision(type, name.trim(), entry.getBearerToken());
      log.info("Provisioned provider principal '{}'", name.trim());
    }
  }
}
