package com.atakmap.android.twcoord.gotopage;

import java.util.Objects;

/**
 * Sealed-ish discriminated union of what the picker preview area renders at any moment. Mirrors the
 * {@link ParseResult} pattern from feature 002.
 *
 * <p>Three variants per [data-model.md §1.3]:
 *
 * <ul>
 *   <li>{@link Empty} — FR-002(a) "Pick an icon" empty-state.
 *   <li>{@link FallbackHint} — FR-002(b) one-shot "Selected icon no longer installed."
 *   <li>{@link Populated} — FR-002(c) thumbnail + label for a currently-selected icon.
 * </ul>
 *
 * <p>Computed from the {@code (markerMode, currentSelection, pendingFallbackHint)} triple in {@code
 * TwCoordGotoView.renderCustomIconPreview()}.
 */
public abstract class PickerPreviewState {

  private PickerPreviewState() {}

  public static Empty empty() {
    return Empty.INSTANCE;
  }

  public static FallbackHint fallbackHint() {
    return FallbackHint.INSTANCE;
  }

  public static Populated populated(IconSelection selection) {
    return new Populated(selection);
  }

  public boolean isEmpty() {
    return this instanceof Empty;
  }

  public boolean isFallbackHint() {
    return this instanceof FallbackHint;
  }

  public boolean isPopulated() {
    return this instanceof Populated;
  }

  public static final class Empty extends PickerPreviewState {
    private static final Empty INSTANCE = new Empty();

    private Empty() {}
  }

  public static final class FallbackHint extends PickerPreviewState {
    private static final FallbackHint INSTANCE = new FallbackHint();

    private FallbackHint() {}
  }

  public static final class Populated extends PickerPreviewState {
    private final IconSelection selection;

    private Populated(IconSelection selection) {
      this.selection = Objects.requireNonNull(selection, "selection");
    }

    public IconSelection selection() {
      return selection;
    }
  }
}
