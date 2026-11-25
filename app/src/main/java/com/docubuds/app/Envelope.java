package com.docubuds.app;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class Envelope {
  private Envelope() {}

  public static byte[] seal(SecretKey key, byte[] plain, String context) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key);
    c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
    byte[] encrypted = c.doFinal(plain),
        iv = c.getIV(),
        out = new byte[2 + iv.length + encrypted.length];
    out[0] = 1;
    out[1] = (byte) iv.length;
    System.arraycopy(iv, 0, out, 2, iv.length);
    System.arraycopy(encrypted, 0, out, 2 + iv.length, encrypted.length);
    return out;
  }

  public static byte[] open(SecretKey key, byte[] envelope, String context) throws Exception {
    if (envelope.length < 30 || envelope[0] != 1 || envelope[1] != 12)
      throw new SecurityException("Invalid encrypted record");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(envelope, 2, 14)));
    c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
    return c.doFinal(envelope, 14, envelope.length - 14);
  }
}
