package io.opensharing.principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opensharing.auth.CatalogAuthType;
import io.opensharing.config.OpenSharingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

class PrincipalProvisionerTest {

  private static final String KEY = "c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=";

  @Test
  void provisionsConfiguredPrincipalsWithDefaults() {
    PrincipalStore principals = mock(PrincipalStore.class);
    OpenSharingProperties properties =
        propertiesWith(
            principal(
                "alice@example.com", "11111111-1111-1111-1111-111111111111", null, null, "alice-secret"),
            principal("data-team", null, PrincipalType.GROUP, CatalogAuthType.TOKEN, "team-secret"));

    new PrincipalProvisioner(principals, properties)
        .run(new DefaultApplicationArguments(new String[0]));

    ArgumentCaptor<PrincipalType> type = ArgumentCaptor.forClass(PrincipalType.class);
    ArgumentCaptor<CatalogAuthType> authType = ArgumentCaptor.forClass(CatalogAuthType.class);
    verify(principals)
        .provision(
            eq("11111111-1111-1111-1111-111111111111"),
            type.capture(),
            authType.capture(),
            eq("alice@example.com"),
            eq("alice-secret"));
    assertEquals(PrincipalType.USER, type.getValue());
    assertEquals(CatalogAuthType.TOKEN, authType.getValue());

    verify(principals)
        .provision(
            eq(null), eq(PrincipalType.GROUP), eq(CatalogAuthType.TOKEN), eq("data-team"), eq("team-secret"));
  }

  @Test
  void skipsProvisioningWhenConfigurationIsEmpty() {
    PrincipalStore principals = mock(PrincipalStore.class);
    OpenSharingProperties properties = new OpenSharingProperties();
    properties.getSecurity().setCredentialEncryptionKey(KEY);

    new PrincipalProvisioner(principals, properties)
        .run(new DefaultApplicationArguments(new String[0]));

    verifyNoInteractions(principals);
  }

  @Test
  void rejectsDuplicateNames() {
    PrincipalStore principals = mock(PrincipalStore.class);
    OpenSharingProperties properties =
        propertiesWith(
            principal("alice@example.com", null, null, null, "alice-secret"),
            principal("Alice@Example.com", null, null, null, "other-secret"));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PrincipalProvisioner(principals, properties)
                    .run(new DefaultApplicationArguments(new String[0])));
    assertTrue(failure.getMessage().contains("more than once"));
    verify(principals)
        .provision(eq(null), eq(PrincipalType.USER), eq(CatalogAuthType.TOKEN), eq("alice@example.com"), eq("alice-secret"));
  }

  @Test
  void rejectsDuplicateIds() {
    PrincipalStore principals = mock(PrincipalStore.class);
    OpenSharingProperties properties =
        propertiesWith(
            principal(
                "alice@example.com", "11111111-1111-1111-1111-111111111111", null, null, "alice-secret"),
            principal(
                "bob@example.com", "11111111-1111-1111-1111-111111111111", null, null, "bob-secret"));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PrincipalProvisioner(principals, properties)
                    .run(new DefaultApplicationArguments(new String[0])));
    assertTrue(failure.getMessage().contains("id '11111111-1111-1111-1111-111111111111'"));
    verify(principals)
        .provision(
            eq("11111111-1111-1111-1111-111111111111"),
            eq(PrincipalType.USER),
            eq(CatalogAuthType.TOKEN),
            eq("alice@example.com"),
            eq("alice-secret"));
  }

  @Test
  void rejectsBlankNamesBlankTokensAndOidc() {
    PrincipalStore principals = mock(PrincipalStore.class);

    OpenSharingProperties blankName = propertiesWith(principal(" ", null, null, null, "secret"));
    assertThrows(
        IllegalStateException.class,
        () ->
            new PrincipalProvisioner(principals, blankName)
                .run(new DefaultApplicationArguments(new String[0])));

    OpenSharingProperties blankToken =
        propertiesWith(principal("alice@example.com", null, null, CatalogAuthType.TOKEN, " "));
    IllegalStateException tokenFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PrincipalProvisioner(principals, blankToken)
                    .run(new DefaultApplicationArguments(new String[0])));
    assertTrue(tokenFailure.getMessage().contains("blank bearer token"));

    OpenSharingProperties oidc =
        propertiesWith(principal("alice@example.com", null, null, CatalogAuthType.OIDC, "secret"));
    IllegalStateException oidcFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PrincipalProvisioner(principals, oidc)
                    .run(new DefaultApplicationArguments(new String[0])));
    assertTrue(oidcFailure.getMessage().contains("OIDC"));
    verifyNoInteractions(principals);
  }

  private static OpenSharingProperties propertiesWith(OpenSharingProperties.Principal... entries) {
    OpenSharingProperties properties = new OpenSharingProperties();
    properties.getSecurity().setCredentialEncryptionKey(KEY);
    properties.setPrincipals(List.of(entries));
    return properties;
  }

  private static OpenSharingProperties.Principal principal(
      String name,
      String id,
      PrincipalType type,
      CatalogAuthType authType,
      String bearerToken) {
    OpenSharingProperties.Principal principal = new OpenSharingProperties.Principal();
    principal.setName(name);
    principal.setId(id);
    principal.setType(type);
    principal.setAuthType(authType);
    principal.setBearerToken(bearerToken);
    return principal;
  }
}
