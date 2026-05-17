package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * JVM-level coverage for [contracts/custom-icon-picker.md § Test contract item 12]: rows whose
 * bitmap fails to decode are silently filtered out at picker bind time, never reach {@code
 * getView()}, and add no placeholder.
 *
 * <p>The remaining dialog-state tests (items 1–8, 11) live in {@code CustomIconPickerDialogTest}
 * which requires Robolectric — not yet added to the project's build classpath. Those are deferred
 * to the Robolectric/Espresso pass per Phase 7. This filter test stands alone because the FR-010a
 * filter logic is extracted into a static helper for exactly this purpose.
 */
public final class CustomIconPickerFilterTest {

  @Test
  public void filterRenderable_skipsRowsWhereLoadBitmapReturnsNull() {
    IconResolver resolver = mock(IconResolver.class);
    Bitmap good = mock(Bitmap.class);

    IconRow r1 = new IconRow(1, "u", "g", "a.png");
    IconRow r2 = new IconRow(2, "u", "g", "broken1.png");
    IconRow r3 = new IconRow(3, "u", "g", "b.png");
    IconRow r4 = new IconRow(4, "u", "g", "broken2.png");
    IconRow r5 = new IconRow(5, "u", "g", "c.png");

    when(resolver.loadBitmap(1)).thenReturn(good);
    when(resolver.loadBitmap(2)).thenReturn(null);
    when(resolver.loadBitmap(3)).thenReturn(good);
    when(resolver.loadBitmap(4)).thenReturn(null);
    when(resolver.loadBitmap(5)).thenReturn(good);

    List<IconRow> result =
        CustomIconPickerDialog.filterRenderable(Arrays.asList(r1, r2, r3, r4, r5), resolver);

    // Adapter MUST see exactly the 3 renderable rows; the 2 corrupt rows are gone, no placeholder.
    assertThat(result).hasSize(3).extracting(IconRow::id).containsExactly(1, 3, 5);
  }

  @Test
  public void filterRenderable_nullInputReturnsEmpty() {
    assertThat(CustomIconPickerDialog.filterRenderable(null, mock(IconResolver.class))).isEmpty();
  }

  @Test
  public void filterRenderable_emptyInputReturnsEmpty() {
    assertThat(
            CustomIconPickerDialog.filterRenderable(
                Collections.emptyList(), mock(IconResolver.class)))
        .isEmpty();
  }

  @Test
  public void filterRenderable_allCorruptReturnsEmpty() {
    IconResolver resolver = mock(IconResolver.class);
    when(resolver.loadBitmap(anyInt())).thenReturn(null);
    IconRow r1 = new IconRow(1, "u", "g", "a.png");
    IconRow r2 = new IconRow(2, "u", "g", "b.png");

    List<IconRow> result = CustomIconPickerDialog.filterRenderable(Arrays.asList(r1, r2), resolver);

    assertThat(result).isEmpty();
  }

  private static int anyInt() {
    return org.mockito.ArgumentMatchers.anyInt();
  }
}
