package com.atakmap.android.twcoord.coord;

import java.util.Locale;

/**
 * Feature 007 US3 — format an on-disk byte count as a short human-readable string using binary
 * (1024) units, matching how Android storage UIs report sizes. Pure; no Android dependency.
 *
 * <ul>
 *   <li>below 1 KiB → whole number of bytes, e.g. {@code 0 B}, {@code 1023 B}
 *   <li>1 KiB and above → one decimal place, e.g. {@code 1.0 KB}, {@code 12.3 MB}, {@code 1.2 GB}
 * </ul>
 *
 * Negative inputs are clamped to {@code 0 B}. Unit labels use the common KB/MB/GB spelling (binary
 * magnitude) for operator familiarity.
 */
public final class ByteCountFormatter {

  private static final long KB = 1024L;
  private static final long MB = KB * 1024L;
  private static final long GB = MB * 1024L;
  private static final long TB = GB * 1024L;

  private ByteCountFormatter() {}

  public static String format(long bytes) {
    if (bytes < 0) bytes = 0;
    if (bytes < KB) return bytes + " B";
    if (bytes < MB) return oneDecimal(bytes, KB) + " KB";
    if (bytes < GB) return oneDecimal(bytes, MB) + " MB";
    if (bytes < TB) return oneDecimal(bytes, GB) + " GB";
    return oneDecimal(bytes, TB) + " TB";
  }

  private static String oneDecimal(long bytes, long unit) {
    return String.format(Locale.US, "%.1f", (double) bytes / (double) unit);
  }
}
