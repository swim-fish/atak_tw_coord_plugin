package com.atakmap.android.twcoord.address;

import java.io.Closeable;
import java.io.OutputStream;

/**
 * JVM-mockable seam over SHA-256 digest calculation. {@link AddressBundleImporter} obtains a fresh
 * {@link Tap} per import via {@link #tap(OutputStream)}; the tap wraps the underlying sink so bytes
 * are tee'd through both the on-disk write AND the digest accumulator (per {@code research.md §R9}
 * — no second-pass read of the staged file). After the tap is closed, {@link Tap#hex()} returns the
 * lowercase hex digest.
 *
 * <p>Production implementation ({@code MessageDigestShaCalculator}) wraps {@code
 * java.security.MessageDigest.getInstance("SHA-256")} (hardware-accelerated on the reference device
 * via ARMv8 crypto extensions). The same {@link ShaCalculator} instance can be reused across
 * imports — each {@link #tap(OutputStream)} call allocates an independent digest state.
 */
public interface ShaCalculator {

  /**
   * Begin a new digest computation. Returns a {@link Tap} that the caller writes to in place of the
   * original {@code sink}; bytes flow through to {@code sink} AND through the digest. Caller is
   * responsible for closing the tap (the tap's {@link Tap#close()} also closes {@code sink}).
   */
  Tap tap(OutputStream sink);

  /**
   * A single in-flight digest. Hand-off pattern: write to {@link #stream()} until done, then {@link
   * #close()}; afterwards {@link #hex()} returns the lowercase hex digest.
   */
  interface Tap extends Closeable {
    /**
     * The {@link OutputStream} the caller writes to. Bytes are forwarded to the underlying sink AND
     * fed into the digest. Closing this stream is equivalent to closing the {@link Tap}.
     */
    OutputStream stream();

    /**
     * The 64-character lowercase hex digest. Safe to call after {@link #close()}. Calling before
     * close is implementation-defined — production implementations return the digest-so-far; tests
     * may or may not.
     */
    String hex();

    @Override
    void close();
  }
}
