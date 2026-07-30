package com.atakmap.android.twcoord.coord;

import java.util.List;

/** Shared, provenance-backed Taipower fixtures for Feature 014 tests. */
public final class Feature014TaipowerFixtures {

  public static final String TEN_METRE_CODE = "H7509 DB40";
  public static final String ONE_METRE_CODE = "H7509 DB4016";

  public static final List<String> ONE_METRE_RAW_VARIANTS =
      List.of(
          "H7509 DB4016",
          "H7509DB4016",
          "h7509 db4016",
          "h7509 Db4016",
          "  H7509 DB4016  ",
          "(H7509 DB4016)",
          "H7509  DB4016",
          "H7509\r\n DB4016");

  public static final List<String> REPRESENTABLE_PARTIAL_RAW =
      List.of("", "H", "H7", "H7509", "H7509D", "H7509DB", "H7509DB4", "H7509DB40");

  public static final String VALID_EAST_WEST_LETTERS = "ABCDEFGH";
  public static final String VALID_NORTH_SOUTH_LETTERS = "ABCDE";
  public static final String INVALID_EAST_WEST_LETTERS = "IJ";
  public static final String INVALID_NORTH_SOUTH_LETTERS = "FGHIJ";

  public static final List<DecodedVector> PROVENANCE_VECTORS =
      List.of(
          new DecodedVector("G8150 HD7812", 235_571, 2_675_382),
          new DecodedVector("W9999 HE9999", 329_999, 2_449_999));

  public static final List<EncodedVector> ENCODER_WRAP_VECTORS =
      List.of(
          new EncodedVector(258_000, 2_655_000, "H1010 AA0000"),
          new EncodedVector(258_799, 2_655_499, "H1010 HE9999"),
          new EncodedVector(258_800, 2_655_000, "H1110 AA0000"),
          new EncodedVector(258_000, 2_655_500, "H1011 AA0000"));

  private Feature014TaipowerFixtures() {}

  public record DecodedVector(String code, int expectedEasting, int expectedNorthing) {}

  public record EncodedVector(int easting, int northing, String expectedCode) {}
}
