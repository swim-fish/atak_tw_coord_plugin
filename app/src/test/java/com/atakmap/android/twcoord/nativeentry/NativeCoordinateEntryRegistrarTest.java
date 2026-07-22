package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.view.View;
import com.atakmap.android.gui.coordinateentry.CoordinateEntryPane;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public final class NativeCoordinateEntryRegistrarTest {

  @Test
  public void startRegistersExactlyOnceOnDispatcher() {
    Fixture fixture = new Fixture();

    fixture.registrar.start();
    fixture.registrar.start();
    assertThat(fixture.gateway.registered).isZero();
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.registered).isOne();
    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.REGISTERED);
  }

  @Test
  public void stopUnregistersTheMatchingInstanceAndIsIdempotent() {
    Fixture fixture = new Fixture();
    fixture.registrar.start();
    fixture.dispatcher.runAll();

    fixture.registrar.stop();
    fixture.registrar.stop();
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.unregistered).isOne();
    assertThat(fixture.gateway.lastUnregistered).isSameAs(fixture.pane);
    verify(fixture.pane).dispose();
    verifyNoMoreInteractions(fixture.pane);
  }

  @Test
  public void uiThreadStopCompletesUnregisterAndDisposeBeforeReturning() {
    Fixture fixture = new Fixture();
    fixture.registrar.start();
    fixture.dispatcher.runAll();

    fixture.registrar.stopNowOnUiThread();

    assertThat(fixture.gateway.unregistered).isOne();
    assertThat(fixture.gateway.lastUnregistered).isSameAs(fixture.pane);
    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.STOPPED);
    verify(fixture.pane).dispose();
  }

  @Test
  public void staleQueuedStartCannotRegisterAfterStop() {
    Fixture fixture = new Fixture();

    fixture.registrar.start();
    fixture.registrar.stop();
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.registered).isZero();
    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.STOPPED);
  }

  @Test
  public void oneHundredCyclesNeverDuplicate() {
    Fixture fixture = new Fixture();
    for (int i = 0; i < 100; i++) {
      fixture.registrar.start();
      fixture.dispatcher.runAll();
      fixture.registrar.stop();
      fixture.dispatcher.runAll();
    }

    assertThat(fixture.gateway.maxLive).isOne();
    assertThat(fixture.gateway.live).isZero();
  }

  @Test
  public void registerFailureRollsBackDisposesAndNeverReportsSuccess() {
    Fixture fixture = new Fixture();
    fixture.gateway.registerFailure = new IllegalStateException("register failed after insert");

    fixture.registrar.start();
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.registered).isOne();
    assertThat(fixture.gateway.unregistered).isOne();
    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.FAILED);
    verify(fixture.pane).dispose();
  }

  @Test
  public void versionSkewAtRegistrationIsContained() {
    for (Error failure :
        new Error[] {
          new NoClassDefFoundError("missing capability"), new NoSuchMethodError("register")
        }) {
      Fixture fixture = new Fixture();
      fixture.gateway.registerFailure = failure;

      fixture.registrar.start();
      fixture.dispatcher.runAll();

      assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.FAILED);
      verify(fixture.pane).dispose();
    }
  }

  @Test
  public void unregisterFailureStillDisposesAndStops() {
    Fixture fixture = new Fixture();
    fixture.registrar.start();
    fixture.dispatcher.runAll();
    fixture.gateway.unregisterFailure = new IllegalStateException("unregister failed");

    fixture.registrar.stop();
    fixture.dispatcher.runAll();

    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.STOPPED);
    verify(fixture.pane).dispose();
  }

  @Test
  public void fatalRegistrationConditionsAreRethrown() {
    Fixture virtualMachineFixture = new Fixture();
    virtualMachineFixture.gateway.registerFailure = new TestVirtualMachineError();
    virtualMachineFixture.registrar.start();
    assertThatThrownBy(virtualMachineFixture.dispatcher::runAll)
        .isInstanceOf(TestVirtualMachineError.class);

    Fixture threadFixture = new Fixture();
    threadFixture.gateway.registerFailure = new ThreadDeath();
    threadFixture.registrar.start();
    assertThatThrownBy(threadFixture.dispatcher::runAll).isInstanceOf(ThreadDeath.class);
  }

  @Test
  public void fatalUnregisterIsRethrownAfterBestEffortDispose() {
    Fixture fixture = new Fixture();
    fixture.registrar.start();
    fixture.dispatcher.runAll();
    fixture.gateway.unregisterFailure = new ThreadDeath();

    fixture.registrar.stop();
    assertThatThrownBy(fixture.dispatcher::runAll).isInstanceOf(ThreadDeath.class);
    verify(fixture.pane).dispose();
  }

  @Test
  public void versionSkewDuringDisposeIsContained() {
    Fixture fixture = new Fixture();
    fixture.registrar.start();
    fixture.dispatcher.runAll();
    doThrow(new NoClassDefFoundError("dispose dependency")).when(fixture.pane).dispose();

    fixture.registrar.stop();
    fixture.dispatcher.runAll();

    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.STOPPED);
  }

  @Test
  public void detachedPaneRefreshesImmediatelyAndKeepsExactlyOneRegistration() {
    RefreshFixture fixture = new RefreshFixture(false);
    fixture.start();

    fixture.registrar.refreshLocale();
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.registered).isEqualTo(2);
    assertThat(fixture.gateway.unregistered).isOne();
    assertThat(fixture.gateway.maxLive).isOne();
    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.REGISTERED);
    verify(fixture.firstPane).dispose();
  }

  @Test
  public void attachedPaneDefersLocaleRefreshUntilDetach() {
    RefreshFixture fixture = new RefreshFixture(true);
    fixture.start();

    fixture.registrar.refreshLocale();
    fixture.dispatcher.runAll();
    assertThat(fixture.gateway.registered).isOne();
    assertThat(fixture.registrar.state())
        .isSameAs(NativeCoordinateEntryRegistrar.State.REFRESH_PENDING);

    fixture.attached.set(false);
    fixture.detachListener.onViewDetachedFromWindow(fixture.firstView);
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.registered).isEqualTo(2);
    assertThat(fixture.gateway.unregistered).isOne();
    assertThat(fixture.gateway.maxLive).isOne();
  }

  @Test
  public void stopInvalidatesQueuedLocaleRefreshGeneration() {
    RefreshFixture fixture = new RefreshFixture(false);
    fixture.start();

    fixture.registrar.refreshLocale();
    fixture.registrar.stop();
    fixture.dispatcher.runAll();

    assertThat(fixture.gateway.registered).isOne();
    assertThat(fixture.gateway.unregistered).isOne();
    assertThat(fixture.gateway.live).isZero();
    assertThat(fixture.registrar.state()).isSameAs(NativeCoordinateEntryRegistrar.State.STOPPED);
  }

  private static final class Fixture {
    final QueueDispatcher dispatcher = new QueueDispatcher();
    final FakeGateway gateway = new FakeGateway();
    final CoordinateEntryPane pane = mock(CoordinateEntryPane.class);
    final NativeCoordinateEntryRegistrar registrar =
        new NativeCoordinateEntryRegistrar(dispatcher, gateway, () -> pane);
  }

  private static final class QueueDispatcher
      implements NativeCoordinateEntryRegistrar.UiDispatcher {
    final Queue<Runnable> queue = new ArrayDeque<>();

    @Override
    public void post(Runnable runnable) {
      queue.add(runnable);
    }

    void runAll() {
      while (!queue.isEmpty()) queue.remove().run();
    }
  }

  private static final class FakeGateway implements NativeCoordinateEntryRegistrar.RegistryGateway {
    int registered;
    int unregistered;
    int live;
    int maxLive;
    CoordinateEntryPane lastUnregistered;
    Throwable registerFailure;
    Throwable unregisterFailure;

    @Override
    public void register(CoordinateEntryPane pane) {
      registered++;
      live++;
      maxLive = Math.max(maxLive, live);
      rethrow(registerFailure);
    }

    @Override
    public void unregister(CoordinateEntryPane pane) {
      unregistered++;
      live--;
      lastUnregistered = pane;
      rethrow(unregisterFailure);
    }

    private static void rethrow(Throwable failure) {
      if (failure instanceof RuntimeException) throw (RuntimeException) failure;
      if (failure instanceof Error) throw (Error) failure;
    }
  }

  private static final class TestVirtualMachineError extends VirtualMachineError {}

  private static final class RefreshFixture {
    final QueueDispatcher dispatcher = new QueueDispatcher();
    final FakeGateway gateway = new FakeGateway();
    final CoordinateEntryPane firstPane = mock(CoordinateEntryPane.class);
    final CoordinateEntryPane secondPane = mock(CoordinateEntryPane.class);
    final View firstView = mock(View.class);
    final View secondView = mock(View.class);
    final AtomicBoolean attached = new AtomicBoolean();
    View.OnAttachStateChangeListener detachListener;
    final NativeCoordinateEntryRegistrar registrar;

    RefreshFixture(boolean initiallyAttached) {
      attached.set(initiallyAttached);
      when(firstPane.getView()).thenReturn(firstView);
      when(secondPane.getView()).thenReturn(secondView);
      when(firstView.isAttachedToWindow()).thenAnswer(ignored -> attached.get());
      when(secondView.isAttachedToWindow()).thenReturn(false);
      org.mockito.Mockito.doAnswer(
              invocation -> {
                detachListener = invocation.getArgument(0);
                return null;
              })
          .when(firstView)
          .addOnAttachStateChangeListener(
              org.mockito.ArgumentMatchers.any(View.OnAttachStateChangeListener.class));
      Queue<CoordinateEntryPane> panes = new ArrayDeque<>();
      panes.add(firstPane);
      panes.add(secondPane);
      registrar = new NativeCoordinateEntryRegistrar(dispatcher, gateway, panes::remove);
    }

    void start() {
      registrar.start();
      dispatcher.runAll();
    }
  }
}
