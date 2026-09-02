package io.opensharing;

import io.opensharing.config.OpenSharingProperties;
import io.opensharing.runtime.OpenSharing;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenSharingProperties.class)
public class OpenSharingServer {

  public static void main(String[] args) {
    OpenSharing.runStandalone(args);
  }
}
