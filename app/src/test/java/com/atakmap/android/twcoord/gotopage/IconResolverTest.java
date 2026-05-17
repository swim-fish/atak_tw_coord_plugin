package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link IconResolver}. Mocks the SDK seam {@link IconDatabaseFacade} so the tests
 * run on the JVM with no Android / ATAK SDK on the test classpath at runtime (compile-time imports
 * of {@code UserIcon} / {@code UserIconSet} are fine — the classes are abstract enough to mock).
 *
 * <p>Covers items 1–6 of [contracts/icon-resolver.md § Test contract].
 */
public final class IconResolverTest {

  private IconDatabaseFacade facade;
  private IconResolver resolver;

  @Before
  public void setUp() {
    facade = mock(IconDatabaseFacade.class);
    resolver = new IconResolver(facade);
  }

  /** 1. listIconsets sorts alphabetically regardless of insertion order. */
  @Test
  public void listIconsets_alphabeticalCaseInsensitive() {
    UserIconSet wildfire = mockSet("u1", "wildfire", 12);
    UserIconSet alpha = mockSet("u2", "Alpha", 3);
    when(facade.getIconSets(true, false)).thenReturn(Arrays.asList(wildfire, alpha));

    List<IconsetSummary> result = resolver.listIconsets();

    assertThat(result).extracting(IconsetSummary::name).containsExactly("Alpha", "wildfire");
  }

  /** 2. listIcons skips invalid rows (UserIcon.isValid() == false). */
  @Test
  public void listIcons_skipsInvalidRows() {
    UserIconSet set = mockSet("uid-X", "Set X", 0);
    List<UserIcon> rows = new ArrayList<>();
    rows.add(mockIcon(101, "uid-X", "g1", "alpha.png", /* valid */ true));
    rows.add(mockIcon(102, "uid-X", "g1", "broken.png", /* valid */ false));
    rows.add(mockIcon(103, "uid-X", "g2", "charlie.png", /* valid */ true));
    when(set.getIcons()).thenReturn(rows);
    when(facade.getIconSet("uid-X", true, false)).thenReturn(set);

    List<IconRow> result = resolver.listIcons("uid-X");

    // Sorted alphabetically by displayName (extension stripped): alpha, charlie.
    assertThat(result).hasSize(2).extracting(IconRow::id).containsExactly(101, 103);
  }

  /**
   * 3. loadBitmap returns null on a row with null/missing bitmap; returns Bitmap on a valid row.
   */
  @Test
  public void loadBitmap_returnsNullOnDecodeFailure() {
    when(facade.getIconBitmap(999)).thenReturn(null);
    Bitmap valid = mock(Bitmap.class);
    when(facade.getIconBitmap(1)).thenReturn(valid);

    assertThat(resolver.loadBitmap(999)).isNull();
    assertThat(resolver.loadBitmap(1)).isSameAs(valid);
  }

  /** 4. resolveSelection returns null for malformed paths and for unknown paths. */
  @Test
  public void resolveSelection_nullPaths() {
    assertThat(resolver.resolveSelection(null)).isNull();
    assertThat(resolver.resolveSelection("")).isNull();
    assertThat(resolver.resolveSelection("no-slashes")).isNull();
    assertThat(resolver.resolveSelection("only/two")).isNull();

    // Well-formed but DB returns null (icon doesn't exist):
    when(facade.getIcon(eq("u-missing"), eq("file.png"), anyBoolean())).thenReturn(null);
    assertThat(resolver.resolveSelection("u-missing/g/file.png")).isNull();
  }

  /** 4 (cont). resolveSelection returns populated IconSelection on a fully-valid path. */
  @Test
  public void resolveSelection_populatedOnValidPath() {
    UserIcon icon = mockIcon(42, "u-ok", "grp", "icon.png", true);
    when(icon.getIconsetPath()).thenReturn("u-ok/grp/icon.png");
    UserIconSet set = mockSet("u-ok", "OK Set", 1);
    when(facade.getIcon("u-ok", "icon.png", false)).thenReturn(icon);
    when(facade.getIconSet("u-ok", false, false)).thenReturn(set);

    IconSelection sel = resolver.resolveSelection("u-ok/grp/icon.png");

    assertThat(sel).isNotNull();
    assertThat(sel.iconId()).isEqualTo(42);
    assertThat(sel.iconsetUid()).isEqualTo("u-ok");
    assertThat(sel.iconsetName()).isEqualTo("OK Set");
    assertThat(sel.iconFileName()).isEqualTo("icon.png");
    assertThat(sel.iconsetPath()).isEqualTo("u-ok/grp/icon.png");
  }

  /** 5. isValidIconsetPath matches resolveSelection != null. */
  @Test
  public void isValidIconsetPath_matchesResolveSelection() {
    UserIcon icon = mockIcon(1, "u", "g", "f.png", true);
    when(icon.getIconsetPath()).thenReturn("u/g/f.png");
    UserIconSet set = mockSet("u", "S", 1);
    when(facade.getIcon("u", "f.png", false)).thenReturn(icon);
    when(facade.getIconSet("u", false, false)).thenReturn(set);

    assertThat(resolver.isValidIconsetPath("u/g/f.png")).isTrue();
    assertThat(resolver.isValidIconsetPath("u/g/missing.png")).isFalse();
    assertThat(resolver.isValidIconsetPath(null)).isFalse();
    assertThat(resolver.isValidIconsetPath("bad")).isFalse();
  }

  /** 6. Every public method swallows SDK exceptions and returns the safe default. */
  @Test
  public void publicMethods_swallowSdkExceptions() {
    when(facade.getIconSets(anyBoolean(), anyBoolean())).thenThrow(new RuntimeException("boom"));
    when(facade.getIconSet(eq("any"), anyBoolean(), anyBoolean()))
        .thenThrow(new RuntimeException("boom"));
    when(facade.getIconBitmap(anyInt())).thenThrow(new RuntimeException("boom"));
    when(facade.getIcon(eq("u"), eq("f"), anyBoolean())).thenThrow(new RuntimeException("boom"));

    assertThat(resolver.listIconsets()).isEmpty();
    assertThat(resolver.listIcons("any")).isEmpty();
    assertThat(resolver.loadBitmap(1)).isNull();
    assertThat(resolver.resolveSelection("u/g/f")).isNull();
    assertThat(resolver.isValidIconsetPath("u/g/f")).isFalse();
  }

  /** Cache: listIconsets is cached until invalidateCaches() is called. */
  @Test
  public void listIconsets_isCachedUntilInvalidated() {
    UserIconSet a = mockSet("u1", "A", 1);
    UserIconSet b = mockSet("u2", "B", 1);
    when(facade.getIconSets(true, false))
        .thenReturn(Collections.singletonList(a))
        .thenReturn(Collections.singletonList(b));

    List<IconsetSummary> first = resolver.listIconsets();
    List<IconsetSummary> second = resolver.listIconsets(); // cached
    resolver.invalidateCaches();
    List<IconsetSummary> third = resolver.listIconsets(); // re-queried

    assertThat(first).extracting(IconsetSummary::name).containsExactly("A");
    assertThat(second).extracting(IconsetSummary::name).containsExactly("A");
    assertThat(third).extracting(IconsetSummary::name).containsExactly("B");
  }

  // ---------------- helpers ----------------

  private static int anyInt() {
    return org.mockito.ArgumentMatchers.anyInt();
  }

  private static UserIconSet mockSet(String uid, String name, int iconCount) {
    UserIconSet s = mock(UserIconSet.class);
    when(s.getUid()).thenReturn(uid);
    when(s.getName()).thenReturn(name);
    // listIconsets uses .getIcons().size() for iconCount; default to a list of iconCount nulls
    // if no explicit setIcons() — tests that need specific rows call when(s.getIcons()).then...
    List<UserIcon> stubIcons = new LinkedList<>();
    for (int i = 0; i < iconCount; i++) {
      stubIcons.add(mockIcon(i + 1, uid, "g", "icon" + i + ".png", true));
    }
    when(s.getIcons()).thenReturn(stubIcons);
    return s;
  }

  private static UserIcon mockIcon(
      int id, String iconsetUid, String group, String fileName, boolean valid) {
    UserIcon i = mock(UserIcon.class);
    when(i.getId()).thenReturn(id);
    when(i.getIconsetUid()).thenReturn(iconsetUid);
    when(i.getGroup()).thenReturn(group);
    when(i.getFileName()).thenReturn(fileName);
    when(i.isValid()).thenReturn(valid);
    return i;
  }
}
