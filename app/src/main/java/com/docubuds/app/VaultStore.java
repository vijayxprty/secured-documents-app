package com.docubuds.app;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import org.json.*;

public final class VaultStore {
  public static final String ALIAS = "docubuds.device.wrap.v1";
  public static final int MAX_FILE = 32 * 1024 * 1024;
  private final File dir;
  private final android.content.SharedPreferences authPrefs;
  private byte[] sessionKey;
  private JSONObject data;

  public VaultStore(Context c) {
    authPrefs = c.getSharedPreferences("app_auth", 0);
    dir = new File(c.getNoBackupFilesDir(), "vault");
    dir.mkdirs();
  }

  public synchronized boolean exists() {
    return new File(dir, "key.bin").exists()
        || new File(dir, "key.bin.bak").exists()
        || new File(dir, "records.bin").exists()
        || new File(dir, "custom.bin").exists();
  }

  private SecretKey wrappingKey() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
    if (exists())
      throw new SecurityException("The device key is unavailable. This vault cannot be recovered.");
    KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    g.init(
        new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                60, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .setInvalidatedByBiometricEnrollment(false)
            .build());
    return g.generateKey();
  }

  public synchronized void unlock() throws Exception {
    boolean existing = exists();
    SecretKey wrap = wrappingKey();
    byte[] raw;
    if (exists())
      raw = Envelope.open(wrap, readAtomic(new File(dir, "key.bin"), 1024), "DocuBuds/key/v1");
    else {
      raw = new byte[32];
      new SecureRandom().nextBytes(raw);
      atomic(new File(dir, "key.bin"), Envelope.seal(wrap, raw, "DocuBuds/key/v1"));
    }
    loadMetadata(raw, existing);
  }

  private void loadMetadata(byte[] raw, boolean existing) throws Exception {
    if (sessionKey != null) Arrays.fill(sessionKey, (byte) 0);
    data = null;
    sessionKey = raw;
    File meta = new File(dir, "records.bin");
    if (existing && !meta.exists() && !new File(dir, "records.bin.bak").exists())
      throw new SecurityException("Vault metadata is missing; refusing to replace it");
    if (meta.exists() || new File(dir, "records.bin.bak").exists())
      data =
          new JSONObject(
              new String(
                  Envelope.open(key(), readAtomic(meta, MAX_FILE), "DocuBuds/metadata/v1"),
                  StandardCharsets.UTF_8));
    else {
      data = new JSONObject();
      data.put("version", 1)
          .put("name", "My vault")
          .put("spaces", new JSONArray())
          .put("records", new JSONArray())
          .put("screenshots", true)
          .put("recent", true)
          .put("dark", false);
      String[] defaults = {
        "Personal IDs",
        "Banking",
        "Property",
        "Vehicles",
        "Work",
        "Resumes",
        "Health",
        "Insurance",
        "Education",
        "Secure notes"
      };
      for (String n : defaults) addSpace(n, "", n.equals("Secure notes") ? "notes" : "space");
      save();
    }
  }

  private SecretKey customDeviceKey(boolean create) throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    String alias = ALIAS + ".custom";
    if (ks.containsAlias(alias)) return (SecretKey) ks.getKey(alias, null);
    if (!create) throw new SecurityException("App unlock key is unavailable. Use phone unlock.");
    KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
    g.init(
        new KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
    return g.generateKey();
  }

  public synchronized String customType() {
    return new File(dir, "custom.bin").exists() ? authPrefs.getString("type", "PIN") : "";
  }

  public synchronized void setCustomUnlock(String type, char[] secret) throws Exception {
    if (!unlocked()) throw new SecurityException("Unlock first");
    CredentialSeal.validate(type, secret);
    byte[] protectedKey = CredentialSeal.seal(secret, sessionKey);
    try {
      atomic(
          new File(dir, "custom.bin"),
          Envelope.seal(customDeviceKey(true), protectedKey, "DocuBuds/custom-device/v1"));
      authPrefs.edit().putString("type", type).putInt("attempts", 0).putLong("until", 0).commit();
    } finally {
      Arrays.fill(protectedKey, (byte) 0);
    }
  }

  public synchronized long customWaitSeconds() {
    return Math.max(0, (authPrefs.getLong("until", 0) - System.currentTimeMillis() + 999) / 1000);
  }

  public synchronized void unlockCustom(char[] secret) throws Exception {
    long wait = customWaitSeconds();
    if (wait > 0)
      throw new SecurityException("Try again in " + wait + " seconds, or use phone unlock.");
    byte[] inner =
        Envelope.open(
            customDeviceKey(false),
            readAtomic(new File(dir, "custom.bin"), 4096),
            "DocuBuds/custom-device/v1");
    byte[] raw = null;
    try {
      raw = CredentialSeal.open(secret, inner);
    } catch (java.security.GeneralSecurityException | SecurityException e) {
      int attempts = authPrefs.getInt("attempts", 0) + 1;
      long delay = attempts >= 5 ? Math.min(3600, 30L * (1L << Math.min(7, attempts - 5))) : 0;
      authPrefs
          .edit()
          .putInt("attempts", attempts)
          .putLong("until", System.currentTimeMillis() + delay * 1000)
          .commit();
      throw new SecurityException(
          delay > 0
              ? "Too many attempts. Try again in " + delay + " seconds."
              : "Incorrect app "
                  + customType().toLowerCase(java.util.Locale.ROOT)
                  + ". Try again.");
    } finally {
      Arrays.fill(inner, (byte) 0);
    }
    try {
      loadMetadata(raw, true);
      authPrefs.edit().putInt("attempts", 0).putLong("until", 0).commit();
    } catch (Exception e) {
      lock();
      throw e;
    }
  }

  public synchronized void removeCustomUnlock() throws Exception {
    if (!unlocked()) throw new SecurityException("Unlock first");
    File f = new File(dir, "custom.bin");
    if (f.exists() && !f.delete()) throw new IOException("Could not remove app unlock");
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    ks.deleteEntry(ALIAS + ".custom");
    authPrefs.edit().clear().commit();
  }

  public synchronized boolean unlocked() {
    return sessionKey != null && data != null;
  }

  public synchronized void lock() {
    if (sessionKey != null) Arrays.fill(sessionKey, (byte) 0);
    sessionKey = null;
    data = null;
  }

  private SecretKey key() {
    if (sessionKey == null) throw new SecurityException("Vault locked");
    return new SecretKeySpec(sessionKey, "AES");
  }

  public synchronized JSONObject root() {
    if (!unlocked()) throw new SecurityException("Vault locked");
    return data;
  }

  public synchronized void save() throws Exception {
    atomic(
        new File(dir, "records.bin"),
        Envelope.seal(
            key(), data.toString().getBytes(StandardCharsets.UTF_8), "DocuBuds/metadata/v1"));
  }

  public synchronized JSONObject addSpace(String title, String parent, String kind)
      throws Exception {
    JSONObject x =
        new JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("title", title)
            .put("parent", parent)
            .put("kind", kind);
    data.getJSONArray("spaces").put(x);
    return x;
  }

  public synchronized JSONObject find(String array, String id) throws Exception {
    JSONArray a = data.getJSONArray(array);
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      if (o.getString("id").equals(id)) return o;
    }
    return null;
  }

  public synchronized void remove(String array, String id) throws Exception {
    JSONArray a = data.getJSONArray(array);
    for (int i = a.length() - 1; i >= 0; i--)
      if (a.getJSONObject(i).getString("id").equals(id)) a.remove(i);
  }

  public synchronized JSONObject attach(String name, String mime, byte[] bytes) throws Exception {
    if (bytes.length > MAX_FILE) throw new IOException("Files can be up to 32 MB each");
    String id = UUID.randomUUID().toString();
    atomic(new File(dir, id + ".bin"), Envelope.seal(key(), bytes, "DocuBuds/file/" + id));
    return new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("mime", mime == null ? "application/octet-stream" : mime)
        .put("size", bytes.length)
        .put("sha256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
  }

  public synchronized byte[] content(JSONObject f) throws Exception {
    String id = f.getString("id");
    if (!id.matches("[0-9a-f-]{36}")) throw new SecurityException("Invalid file");
    return Envelope.open(
        key(), read(new File(dir, id + ".bin"), MAX_FILE + 1024), "DocuBuds/file/" + id);
  }

  public synchronized void deleteBlob(JSONObject f) {
    new File(dir, f.optString("id") + ".bin").delete();
  }

  public synchronized void purgeOrphans() throws Exception {
    Set<String> used = new HashSet<>();
    JSONArray a = data.getJSONArray("records");
    for (int i = 0; i < a.length(); i++) {
      JSONArray fs = a.getJSONObject(i).optJSONArray("files");
      if (fs != null)
        for (int j = 0; j < fs.length(); j++)
          used.add(fs.getJSONObject(j).optString("id") + ".bin");
    }
    File[] files = dir.listFiles();
    if (files != null)
      for (File f : files)
        if (f.getName().matches("[0-9a-f-]{36}\\.bin") && !used.contains(f.getName())) f.delete();
  }

  public synchronized void destroy() throws Exception {
    lock();
    File[] fs = dir.listFiles();
    if (fs != null)
      for (File f : fs) if (!f.delete()) throw new IOException("Could not remove vault file");
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    ks.deleteEntry(ALIAS);
    ks.deleteEntry(ALIAS + ".custom");
    authPrefs.edit().clear().commit();
  }

  public static byte[] read(File f, int max) throws IOException {
    try (InputStream in = new FileInputStream(f)) {
      return read(in, max);
    }
  }

  public static byte[] read(InputStream in, int max) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buffer = new byte[32768];
    int n;
    while ((n = in.read(buffer)) != -1) {
      if (b.size() + n > max) throw new IOException("File exceeds the 32 MB limit");
      b.write(buffer, 0, n);
    }
    return b.toByteArray();
  }

  private static byte[] readAtomic(File f, int max) throws IOException {
    try (InputStream in = new AtomicFile(f).openRead()) {
      return read(in, max);
    }
  }

  private static void atomic(File f, byte[] b) throws IOException {
    AtomicFile a = new AtomicFile(f);
    FileOutputStream o = null;
    try {
      o = a.startWrite();
      o.write(b);
      a.finishWrite(o);
    } catch (IOException e) {
      if (o != null) a.failWrite(o);
      throw e;
    }
  }

  private static String hex(byte[] b) {
    StringBuilder s = new StringBuilder();
    for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
    return s.toString();
  }
}
