package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atakmap.android.twcoord.address.ActiveDatasetRegistry;
import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.AddressDataset;
import com.atakmap.android.twcoord.address.AddressRecord;
import com.atakmap.android.twcoord.address.CountyActiveDataset;
import com.atakmap.android.twcoord.address.GeneratorMetadata;
import com.atakmap.android.twcoord.address.ImportedManifest;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class DefaultAddressLookupServiceReverseTest {

  @Test
  public void foundRetainsExactQueryAndSeparateRecordWithBoundedRadiusAndProvenance() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    when(facade.nearestWithin(25.033, 121.565, 500d))
        .thenReturn(new AddressRecord(25.0332, 121.5653, "臺北市信義區市府路1號", "same"));
    CountyActiveDataset active = dataset("臺北市", facade);
    ActiveDatasetRegistry.ReadSession session = mock(ActiveDatasetRegistry.ReadSession.class);
    when(session.datasets()).thenReturn(Collections.singletonMap("臺北市", active));
    Wgs84 query = new Wgs84(25.033, 121.565, 9L, Wgs84.Source.COT_TARGET);
    ReverseAddressRequest request = request(query, 500d);

    ReverseAddressResult result =
        new DefaultAddressLookupService.RegistryQueryEngine(new TaiwanAddressParser())
            .reverse(request, session);

    assertThat(result.status()).isEqualTo(ReverseAddressResult.Status.FOUND);
    assertThat(result.queryPoint()).isSameAs(query);
    assertThat(result.candidate().recordPoint().latitudeDeg()).isEqualTo(25.0332);
    assertThat(result.candidate().recordPoint()).isNotSameAs(query);
    assertThat(result.candidate().datasetIdentity().county()).isEqualTo("臺北市");
    assertThat(result.candidate().datasetIdentity().source()).isEqualTo("fixture");
    verify(facade).nearestWithin(25.033, 121.565, 500d);
  }

  @Test
  public void noDataAndNoMatchRemainExplicit() {
    ActiveDatasetRegistry.ReadSession empty = mock(ActiveDatasetRegistry.ReadSession.class);
    when(empty.datasets()).thenReturn(Collections.emptyMap());
    Wgs84 query = new Wgs84(23.5, 121.0, 1L, Wgs84.Source.MAP_CENTRE);
    DefaultAddressLookupService.RegistryQueryEngine engine =
        new DefaultAddressLookupService.RegistryQueryEngine(new TaiwanAddressParser());

    assertThat(engine.reverse(request(query, 500d), empty).status())
        .isEqualTo(ReverseAddressResult.Status.NO_DATASET);

    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    ActiveDatasetRegistry.ReadSession installed = mock(ActiveDatasetRegistry.ReadSession.class);
    CountyActiveDataset installedDataset = dataset("花蓮縣", facade);
    when(installed.datasets()).thenReturn(Collections.singletonMap("花蓮縣", installedDataset));
    assertThat(engine.reverse(request(query, 321d), installed).status())
        .isEqualTo(ReverseAddressResult.Status.NO_MATCH);
    verify(facade).nearestWithin(23.5, 121.0, 321d);
  }

  @Test
  public void cancelledReverseSuppressesCompletion() throws Exception {
    ActiveDatasetRegistry registry = mock(ActiveDatasetRegistry.class);
    ActiveDatasetRegistry.ReadSession session = mock(ActiveDatasetRegistry.ReadSession.class);
    when(registry.snapshot()).thenReturn(Collections.emptyMap());
    when(registry.openReadSession()).thenReturn(session);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    DefaultAddressLookupService.QueryEngine engine =
        new DefaultAddressLookupService.QueryEngine() {
          @Override
          public ForwardAddressResult forward(
              ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession ignored) {
            return ForwardAddressResult.noMatch(request.identity());
          }

          @Override
          public ReverseAddressResult reverse(
              ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession ignored)
              throws Exception {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            finished.countDown();
            return ReverseAddressResult.noMatch(request.identity(), request.queryPoint());
          }
        };
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(registry, Runnable::run, engine, 4);
    AtomicInteger callbacks = new AtomicInteger();
    LookupHandle handle =
        service.reverse(
            request(new Wgs84(25, 121, 1L, Wgs84.Source.COT_TARGET), 500d),
            ignored -> callbacks.incrementAndGet());
    assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

    handle.cancel();
    release.countDown();
    assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();

    assertThat(callbacks).hasValue(0);
    service.close();
  }

  private static ReverseAddressRequest request(Wgs84 point, double radius) {
    return new ReverseAddressRequest(
        new LookupIdentity("reverse", 1L, 2L, 3L),
        "native",
        LookupPriority.NATIVE_INTERACTIVE,
        point,
        radius);
  }

  private static CountyActiveDataset dataset(String county, AddressDatabaseFacade facade) {
    GeneratorMetadata generator = mock(GeneratorMetadata.class);
    when(generator.dataDate()).thenReturn("115-07");
    when(generator.schemaVersion()).thenReturn(3);
    when(generator.source()).thenReturn("fixture");
    ImportedManifest imported = mock(ImportedManifest.class);
    when(imported.fileSha256()).thenReturn("fixture-sha");
    AddressDataset dataset = mock(AddressDataset.class);
    when(dataset.generator()).thenReturn(generator);
    when(dataset.imported()).thenReturn(imported);
    return new CountyActiveDataset(county, dataset, facade);
  }
}
