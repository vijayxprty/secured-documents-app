package com.docubuds.app;

import static org.junit.Assert.*;

import org.junit.Test;

public class CredentialSealTest {
  @Test
  public void roundTripAndRandomSalt() throws Exception {
    byte[] raw = new byte[32];
    new java.security.SecureRandom().nextBytes(raw);
    byte[] a = CredentialSeal.seal("483927".toCharArray(), raw),
        b = CredentialSeal.seal("483927".toCharArray(), raw);
    assertFalse(java.util.Arrays.equals(a, b));
    assertArrayEquals(raw, CredentialSeal.open("483927".toCharArray(), a));
  }

  @Test
  public void incorrectCredentialAndTamperingRejected() throws Exception {
    byte[] a = CredentialSeal.seal("018765".toCharArray(), new byte[32]);
    try {
      CredentialSeal.open("018764".toCharArray(), a);
      fail();
    } catch (javax.crypto.AEADBadTagException expected) {
    }
    a[a.length - 1] ^= 1;
    try {
      CredentialSeal.open("018765".toCharArray(), a);
      fail();
    } catch (javax.crypto.AEADBadTagException expected) {
    }
  }

  @Test
  public void credentialPolicyIsEnforced() {
    CredentialSeal.validate("PIN", "483927".toCharArray());
    CredentialSeal.validate("Pattern", "012543".toCharArray());
    for (String[] invalid :
        new String[][] {
          {"PIN", "123"},
          {"PIN", "123abc"},
          {"Pattern", "012301"},
          {"Pattern", "12345"},
          {"Pattern", "012349"}
        }) {
      try {
        CredentialSeal.validate(invalid[0], invalid[1].toCharArray());
        fail("Weak or invalid credential accepted");
      } catch (IllegalArgumentException expected) {
      }
    }
  }
}
