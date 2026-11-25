package com.docubuds.app;

import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned, salted credential protection; device binding is provided separately by Keystore. */
public final class CredentialSeal {
  public static final int ITERATIONS = 600000;

  public static void validate(String type, char[] value) {
    if (type.equals("PIN")) {
      if (value.length < 6 || value.length > 12)
        throw new IllegalArgumentException("Use 6–12 digits");
      for (char c : value)
        if (c < '0' || c > '9') throw new IllegalArgumentException("PIN must contain digits only");
    } else if (type.equals("Pattern")) {
      if (value.length < 6 || value.length > 9)
        throw new IllegalArgumentException("Connect six or more dots");
      int used = 0;
      for (char c : value) {
        if (c < '0' || c > '8' || (used & (1 << (c - '0'))) != 0)
          throw new IllegalArgumentException("Pattern dots must be unique");
        used |= 1 << (c - '0');
      }
    } else throw new IllegalArgumentException("Choose PIN or Pattern");
  }

  private static SecretKeySpec derive(char[] secret, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(secret, salt, ITERATIONS, 256);
    byte[] raw = null;
    try {
      raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
      return new SecretKeySpec(raw, "AES");
    } finally {
      spec.clearPassword();
      if (raw != null) Arrays.fill(raw, (byte) 0);
    }
  }

  public static byte[] seal(char[] secret, byte[] data) throws Exception {
    byte[] salt = new byte[32];
    new SecureRandom().nextBytes(salt);
    byte[] sealed = Envelope.seal(derive(secret, salt), data, "DocuBuds/app-credential/v1");
    byte[] result = new byte[33 + sealed.length];
    result[0] = 1;
    System.arraycopy(salt, 0, result, 1, 32);
    System.arraycopy(sealed, 0, result, 33, sealed.length);
    return result;
  }

  public static byte[] open(char[] secret, byte[] data) throws Exception {
    if (data.length < 63 || data[0] != 1)
      throw new SecurityException("Invalid app credential envelope");
    return Envelope.open(
        derive(secret, Arrays.copyOfRange(data, 1, 33)),
        Arrays.copyOfRange(data, 33, data.length),
        "DocuBuds/app-credential/v1");
  }
}
