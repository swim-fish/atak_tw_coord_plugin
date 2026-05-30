package com.atakmap.android.twcoord.address;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Production {@link ShaCalculator} backed by {@code java.security.MessageDigest("SHA-256")}.
 * Hardware-accelerated on the reference Galaxy Tab S10+ via ARMv8 crypto extensions, so the digest
 * pass adds zero measurable wall-clock to the SAF stream copy in {@link
 * AddressBundleImporter#importFrom}.
 *
 * <p>The class is stateless ({@code MessageDigest} state lives in the per-call {@link Tap}); a
 * single instance can be shared across threads and across import sessions.
 */
public final class MessageDigestShaCalculator implements ShaCalculator {

  @Override
  public Tap tap(OutputStream sink) {
    Objects.requireNonNull(sink, "sink");
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is a stdlib MUST-have on every JVM since Java 1.5; if it is missing we
      // cannot proceed.
      throw new IllegalStateException("SHA-256 not available", e);
    }
    return new DigestingTap(sink, digest);
  }

  private static final class DigestingTap implements Tap {
    private final OutputStream sink;
    private final MessageDigest digest;
    private final OutputStream tapStream;
    private boolean closed;
    private String hexCache;

    DigestingTap(OutputStream sink, MessageDigest digest) {
      this.sink = sink;
      this.digest = digest;
      this.tapStream =
          new OutputStream() {
            @Override
            public void write(int b) throws IOException {
              sink.write(b);
              digest.update((byte) b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
              sink.write(b, off, len);
              digest.update(b, off, len);
            }

            @Override
            public void flush() throws IOException {
              sink.flush();
            }

            @Override
            public void close() throws IOException {
              DigestingTap.this.close();
            }
          };
    }

    @Override
    public OutputStream stream() {
      return tapStream;
    }

    @Override
    public String hex() {
      if (hexCache == null) {
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
          sb.append(Character.forDigit((b >> 4) & 0xF, 16));
          sb.append(Character.forDigit(b & 0xF, 16));
        }
        hexCache = sb.toString();
      }
      return hexCache;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      try {
        sink.close();
      } catch (IOException ignored) {
        // best-effort
      }
    }
  }
}
