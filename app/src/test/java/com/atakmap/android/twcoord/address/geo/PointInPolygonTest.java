package com.atakmap.android.twcoord.address.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Feature 006 T007 — ray-cast point-in-polygon via the public {@link BoundaryGeometry#covers}
 * surface (the {@link PointInPolygon} helper is package-private). Uses hand-built geometries so the
 * geometry behaviour is verified independently of the WKB parser.
 */
public class PointInPolygonTest {

  /** A unit square (0,0)-(10,10) as a single-polygon BoundaryGeometry, optionally with a hole. */
  private static BoundaryGeometry square(boolean withHole) {
    double[] extLon = {0, 10, 10, 0, 0};
    double[] extLat = {0, 0, 10, 10, 0};
    double[][] holeLon;
    double[][] holeLat;
    if (withHole) {
      holeLon = new double[][] {{4, 6, 6, 4, 4}};
      holeLat = new double[][] {{4, 4, 6, 6, 4}};
    } else {
      holeLon = new double[0][];
      holeLat = new double[0][];
    }
    BoundaryGeometry.Builder b = new BoundaryGeometry.Builder();
    b.addPolygon(extLon, extLat, holeLon, holeLat);
    return b.build();
  }

  @Test
  public void pointInsideSquareIsCovered() {
    assertThat(square(false).covers(5, 5)).isTrue();
  }

  @Test
  public void pointOutsideSquareIsNotCovered() {
    assertThat(square(false).covers(50, 50)).isFalse();
    assertThat(square(false).covers(-1, 5)).isFalse();
  }

  @Test
  public void pointInHoleIsNotCovered() {
    assertThat(square(true).covers(5, 5)).isFalse(); // (5,5) is inside the 4-6 hole
  }

  @Test
  public void pointInsideButOutsideHoleIsCovered() {
    assertThat(square(true).covers(1, 1)).isTrue(); // inside exterior, outside the hole
  }

  @Test
  public void bboxRejectFastPath() {
    // Far outside the cached bounds → covers returns false via the bbox check.
    BoundaryGeometry g = square(false);
    assertThat(g.covers(1000, 1000)).isFalse();
    assertThat(g.minLat()).isEqualTo(0.0);
    assertThat(g.maxLat()).isEqualTo(10.0);
  }

  @Test
  public void sharedEdgeIsDeterministic() {
    // A point exactly on a shared vertical edge resolves consistently across repeated calls.
    BoundaryGeometry g = square(false);
    boolean first = g.covers(5, 0); // on the left edge (lon=0)
    boolean second = g.covers(5, 0);
    assertThat(first).isEqualTo(second);
  }
}
