package io.opensharing.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.opensharing.catalog.AssetAccessDeniedException;
import io.opensharing.catalog.AssetAction;
import io.opensharing.catalog.AssetLookup;
import io.opensharing.catalog.AssetNotFoundException;
import io.opensharing.catalog.AssetType;
import io.opensharing.catalog.CatalogCaller;
import io.opensharing.catalog.CatalogConnector;
import io.opensharing.catalog.CatalogException;
import io.opensharing.catalog.CloudProvider;
import io.opensharing.catalog.CredentialRequest;
import io.opensharing.catalog.ResolvedAsset;
import io.opensharing.catalog.StorageCredentialKeys;
import io.opensharing.catalog.StorageCredentials;
import io.opensharing.catalog.StorageOperation;
import io.opensharing.catalog.TableFormat;
import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * What happens to a shared object when the catalog stops handing it over. The status is what keeps a
 * broken object out of recipient responses instead of failing each request forever.
 */
class AssetResolutionServiceTest {

  private static final String NAME = "main.sales.orders";

  @Test
  void recordsThatASourceHasGoneMissing() {
    SharedDataObjectEntity object = sharedObject();
    AssetResolutionService resolution =
        serviceThat(
            lookup -> {
              throw new AssetNotFoundException(lookup);
            });

    ApiException failure =
        assertThrows(ApiException.class, () -> resolution.resolveForServing(object));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatus());
    assertEquals(SharedObjectStatus.SOURCE_NOT_FOUND, object.getStatus());
  }

  @Test
  void recordsThatTheServerMayNoLongerReadTheSource() {
    SharedDataObjectEntity object = sharedObject();
    AssetResolutionService resolution =
        serviceThat(
            lookup -> {
              throw new AssetAccessDeniedException(lookup, CatalogCaller.server());
            });

    ApiException failure =
        assertThrows(ApiException.class, () -> resolution.resolveForServing(object));

    assertEquals(HttpStatus.FORBIDDEN, failure.getStatus());
    assertEquals(SharedObjectStatus.PERMISSION_DENIED, object.getStatus());
  }

  @Test
  void revivesAnObjectWhoseSourceComesBack() {
    SharedDataObjectEntity object = sharedObject();
    object.setStatus(SharedObjectStatus.SOURCE_NOT_FOUND);
    AssetResolutionService resolution =
        serviceThat(
            lookup ->
                ResolvedAsset.builder(AssetType.TABLE, lookup.identifier())
                    .storageLocation("s3://lake/sales/orders/")
                    .format(TableFormat.DELTA)
                    .build());

    resolution.resolveForServing(object);

    assertEquals(SharedObjectStatus.ACTIVE, object.getStatus());
    assertEquals("s3://lake/sales/orders/", object.getStorageLocation());
  }

  private static SharedDataObjectEntity sharedObject() {
    SharedDataObjectEntity object = new SharedDataObjectEntity();
    object.setName(NAME);
    object.setType(AssetType.TABLE);
    object.setSharedAs("sales.orders");
    return object;
  }

  @Test
  void picksTheNarrowestVendedPrefixCoveringTheLocation() {
    AssetResolutionService resolution =
        serviceVending(
            List.of(
                credentialsFor("s3://lake/"),
                credentialsFor("s3://lake/sales/orders/"),
                credentialsFor("s3://other/")));

    assertEquals(
        "s3://lake/sales/orders/",
        resolution.vendCredentials(requestFor("s3://lake/sales/orders/data/")).prefix());
  }

  @Test
  void refusesCredentialsThatDoNotCoverTheLocation() {
    AssetResolutionService resolution = serviceVending(List.of(credentialsFor("s3://other/")));

    CatalogException failure =
        assertThrows(
            CatalogException.class,
            () -> resolution.vendCredentials(requestFor("s3://lake/sales/orders/")));
    assertTrue(failure.getMessage().contains("none covering"));
  }

  @Test
  void refusesAnEmptyVend() {
    AssetResolutionService resolution = serviceVending(List.of());

    assertThrows(
        CatalogException.class,
        () -> resolution.vendCredentials(requestFor("s3://lake/sales/orders/")));
  }

  private static StorageCredentials credentialsFor(String prefix) {
    return new StorageCredentials(
        prefix, CloudProvider.AWS, Map.of(StorageCredentialKeys.ACCESS_KEY_ID, "AKIA"), null);
  }

  private static CredentialRequest requestFor(String location) {
    return new CredentialRequest(
        AssetType.TABLE, NAME, NAME, location, StorageOperation.READ, Duration.ofMinutes(5));
  }

  private static AssetResolutionService serviceThat(
      Function<AssetLookup, ResolvedAsset> resolveAsset) {
    return service(resolveAsset, request -> List.of());
  }

  private static AssetResolutionService serviceVending(List<StorageCredentials> minted) {
    return service(
        lookup -> ResolvedAsset.builder(AssetType.TABLE, lookup.identifier()).build(),
        request -> minted);
  }

  private static AssetResolutionService service(
      Function<AssetLookup, ResolvedAsset> resolveAsset,
      Function<CredentialRequest, List<StorageCredentials>> vend) {
    CatalogConnector catalog =
        new CatalogConnector() {
          @Override
          public String name() {
            return "stub";
          }

          @Override
          public ResolvedAsset resolveAsset(
              AssetLookup lookup, CatalogCaller caller, AssetAction intent) {
            return resolveAsset.apply(lookup);
          }

          @Override
          public List<StorageCredentials> getStorageCredentials(CredentialRequest request) {
            return vend.apply(request);
          }
        };
    return new AssetResolutionService(
        catalog, mock(SharedDataObjectStore.class), new OpenSharingProperties());
  }
}
