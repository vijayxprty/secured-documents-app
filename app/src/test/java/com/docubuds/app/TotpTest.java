package com.docubuds.app;

import static org.junit.Assert.*;

import org.junit.Test;

public class TotpTest {
  @Test
  public void standardSha1Vectors() throws Exception {
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    assertEquals("287082", MainActivity.totp(secret, 59000));
    assertEquals("081804", MainActivity.totp(secret, 1111111109000L));
    assertEquals("050471", MainActivity.totp(secret, 1111111111000L));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidSecretRejected() throws Exception {
    MainActivity.totp("not a valid secret!", 59000);
  }
}
