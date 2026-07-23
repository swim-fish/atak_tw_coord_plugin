package com.atakmap.android.twcoord.address.lookup;

import java.util.Arrays;
import java.util.List;

/**
 * Minimal ranking fixture extracted from the generator's Taichung address output on 2026-07-23.
 *
 * <p>The copied rows make tests deterministic and self-contained; tests never open or depend on the
 * generator repository or its SQLite artifacts.
 */
public final class TaichungAddressRankingFixture {

  private TaichungAddressRankingFixture() {}

  public static List<AddressCandidate> taiwanBoulevard(double anchorLat, double anchorLon) {
    return Arrays.asList(
        candidate(
            24.163090080161,
            120.649574057339,
            "台中市西屯區惠來里臺灣大道三段８號",
            "台中市西屯區惠來里臺灣大道三段8號",
            "臺灣大道三段",
            "８號",
            anchorLat,
            anchorLon),
        candidate(
            24.161989237766,
            120.647296501898,
            "台中市西屯區惠來里臺灣大道三段９９號",
            "台中市西屯區惠來里臺灣大道三段99號",
            "臺灣大道三段",
            "９９號",
            anchorLat,
            anchorLon),
        candidate(
            24.169252556059,
            120.640790224313,
            "台中市西屯區上安里臺灣大道三段５５６巷９號",
            "台中市西屯區上安里臺灣大道三段556巷9號",
            "臺灣大道三段",
            "９號",
            anchorLat,
            anchorLon),
        candidate(
            24.170257507951,
            120.636898779974,
            "台中市西屯區潮洋里臺灣大道三段６０９號",
            "台中市西屯區潮洋里臺灣大道三段609號",
            "臺灣大道三段",
            "６０９號",
            anchorLat,
            anchorLon),
        candidate(
            24.15794601798,
            120.657473865508,
            "台中市西屯區何南里臺灣大道二段６０７號",
            "台中市西屯區何南里臺灣大道二段607號",
            "臺灣大道二段",
            "６０７號",
            anchorLat,
            anchorLon));
  }

  private static AddressCandidate candidate(
      double lat,
      double lon,
      String display,
      String displayHalfwidth,
      String street,
      String number,
      double anchorLat,
      double anchorLon) {
    return new AddressCandidate(
        lat,
        lon,
        display,
        displayHalfwidth,
        street,
        number,
        StreetCandidateRanker.haversine(anchorLat, anchorLon, lat, lon));
  }
}
