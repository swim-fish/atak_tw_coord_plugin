package com.atakmap.android.twcoord.address;

import java.io.OutputStream;

/**
 * JVM-mockable seam over SHA-256 digest calculation. {@link AddressBundleImporter} wraps the
 * staging-file {@link OutputStream} with {@link #tapping(OutputStream)} so the digest is computed
 * incrementally as bytes pass through during the SAF stream copy (per {@code research.md §R9} — no
 * second-pass read of the staged file). After the copy completes, {@link #finalDigestHex()} returns
 * the lowercase hex digest for storage in the plugin-side {@code imported.manifest.txt}.
 *
 * <p>Production implementation ({@code MessageDigestShaCalculator}) wraps {@code
 * java.security.MessageDigest.getInstance("SHA-256")} (hardware-accelerated on the reference device
 * via ARMv8 crypto extensions). Tests can inject a deterministic stub.
 *
 * <p>One {@link ShaCalculator} instance is single-use: after {@link #finalDigestHex()} returns, the
 * implementation MAY reject further reads / writes.
 */
public interface ShaCalculator {

  /**
   * Return an {@link OutputStream} that writes through to {@code sink} while incrementally updating
   * the digest. Closing the returned stream closes {@code sink}.
   */
  OutputStream tapping(OutputStream sink);

  /**
   * Return the 64-character lowercase hex digest of all bytes that passed through the wrapper
   * returned by {@link #tapping(OutputStream)}. Calling this before any bytes were written returns
   * the SHA-256 of the empty input ({@code
   * e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855}).
   */
  String finalDigestHex();
}
