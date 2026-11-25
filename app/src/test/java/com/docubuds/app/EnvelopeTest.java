package com.docubuds.app;

import static org.junit.Assert.*;

import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class EnvelopeTest {
  private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");

  @Test
  public void originalBytesSurvive() throws Exception {
    byte[] p = new byte[65537];
    new java.security.SecureRandom().nextBytes(p);
    assertArrayEquals(p, Envelope.open(key, Envelope.seal(key, p, "file/1"), "file/1"));
  }

  @Test
  public void uniqueNonces() throws Exception {
    assertFalse(
        Arrays.equals(Envelope.seal(key, new byte[0], "x"), Envelope.seal(key, new byte[0], "x")));
  }

  @Test
  public void rejectsTampering() throws Exception {
    byte[] e = Envelope.seal(key, "private record".getBytes(), "x");
    e[e.length - 1] ^= 1;
    reject(key, e, "x");
  }

  @Test
  public void rejectsWrongKeyAndContext() throws Exception {
    byte[] e = Envelope.seal(key, new byte[] {1, 2, 3}, "file/1");
    reject(key, e, "file/2");
    byte[] other = new byte[32];
    other[0] = 1;
    reject(new SecretKeySpec(other, "AES"), e, "file/1");
  }

  @Test
  public void rejectsTruncationAndVersion() throws Exception {
    byte[] e = Envelope.seal(key, new byte[] {1, 2, 3}, "x");
    reject(key, Arrays.copyOf(e, e.length - 1), "x");
    e[0] = 2;
    reject(key, e, "x");
    reject(key, new byte[0], "x");
  }

  private void reject(SecretKeySpec k, byte[] e, String aad) throws Exception {
    try {
      Envelope.open(k, e, aad);
      fail("Unauthenticated content accepted");
    } catch (java.security.GeneralSecurityException | SecurityException expected) {
    }
  }
}
