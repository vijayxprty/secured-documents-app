package com.docubuds.app;

import android.app.Application;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

public final class VaultApp extends Application {
  public VaultStore store;
  public final ExecutorService io = Executors.newSingleThreadExecutor();
  public final Handler main = new Handler(Looper.getMainLooper());
  public volatile long lastInteraction;
  public volatile boolean busy = false;
  public static final ConcurrentHashMap<String, Shared> shares = new ConcurrentHashMap<>();

  public static final class Shared {
    public byte[] bytes;
    public String name, mime;
    public long until;

    public Shared(byte[] b, String n, String m) {
      bytes = b;
      name = n;
      mime = m;
      until = SystemClock.elapsedRealtime() + 600000;
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    store = new VaultStore(this);
    clearTemporary();
    main.post(
        new Runnable() {
          @Override
          public void run() {
            if (store.unlocked() && SystemClock.elapsedRealtime() - lastInteraction >= 60000)
              store.lock();
            pruneShares();
            main.postDelayed(this, 1000);
          }
        });
  }

  public void clearTemporary() {
    java.io.File[] fs = getCacheDir().listFiles();
    if (fs != null) for (java.io.File f : fs) if (f.isFile()) f.delete();
  }

  public static void pruneShares() {
    long now = SystemClock.elapsedRealtime();
    for (var e : shares.entrySet())
      if (e.getValue().until < now) {
        Shared s = shares.remove(e.getKey());
        if (s != null) Arrays.fill(s.bytes, (byte) 0);
      }
  }

  public void touch() {
    lastInteraction = SystemClock.elapsedRealtime();
  }
}
