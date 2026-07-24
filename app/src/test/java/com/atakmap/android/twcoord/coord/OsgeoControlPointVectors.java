package com.atakmap.android.twcoord.coord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Test-only loader for the OSGeo Taiwan datum control/common-point fixture. */
final class OsgeoControlPointVectors {

  enum Region {
    MAIN_ISLAND,
    PENGHU,
    KINMEN,
    MATSU
  }

  static final class Point {
    final String id;
    final Region region;
    final double latDeg;
    final double lonDeg;
    final int zone;
    final double twd97E;
    final double twd97N;
    final double twd67E;
    final double twd67N;
    final boolean twd67Observed;

    Point(
        String id,
        Region region,
        double latDeg,
        double lonDeg,
        int zone,
        double twd97E,
        double twd97N,
        double twd67E,
        double twd67N,
        boolean twd67Observed) {
      this.id = id;
      this.region = region;
      this.latDeg = latDeg;
      this.lonDeg = lonDeg;
      this.zone = zone;
      this.twd97E = twd97E;
      this.twd97N = twd97N;
      this.twd67E = twd67E;
      this.twd67N = twd67N;
      this.twd67Observed = twd67Observed;
    }
  }

  static final List<Point> ALL = load();
  static final List<Point> MAIN_ISLAND = select(Region.MAIN_ISLAND);
  static final List<Point> PENGHU = select(Region.PENGHU);
  static final List<Point> KINMEN = select(Region.KINMEN);
  static final List<Point> MATSU = select(Region.MATSU);

  private OsgeoControlPointVectors() {}

  private static List<Point> load() {
    InputStream stream =
        OsgeoControlPointVectors.class
            .getClassLoader()
            .getResourceAsStream("coord/osgeo-taiwan-control-points.csv");
    if (stream == null) {
      throw new IllegalStateException("missing OSGeo coordinate test fixture");
    }

    List<Point> points = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#") || line.startsWith("id,")) {
          continue;
        }
        String[] columns = line.split(",", -1);
        if (columns.length != 10) {
          throw new IllegalStateException("invalid fixture row: " + line);
        }
        points.add(
            new Point(
                columns[0],
                Region.valueOf(columns[1]),
                Double.parseDouble(columns[2]),
                Double.parseDouble(columns[3]),
                Integer.parseInt(columns[4]),
                Double.parseDouble(columns[5]),
                Double.parseDouble(columns[6]),
                Double.parseDouble(columns[7]),
                Double.parseDouble(columns[8]),
                Boolean.parseBoolean(columns[9])));
      }
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }

    if (points.size() != 88) {
      throw new IllegalStateException("expected 88 coordinate fixtures, found " + points.size());
    }
    return List.copyOf(points);
  }

  private static List<Point> select(Region region) {
    List<Point> selected = new ArrayList<>();
    for (Point point : ALL) {
      if (point.region == region) {
        selected.add(point);
      }
    }
    return List.copyOf(selected);
  }
}
