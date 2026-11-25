package com.docubuds.app;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

public class ShareProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  private VaultApp.Shared item(Uri uri) throws FileNotFoundException {
    VaultApp.pruneShares();
    VaultApp.Shared s = VaultApp.shares.get(uri.getLastPathSegment());
    if (s == null) throw new FileNotFoundException("This sharing link has expired");
    return s;
  }

  @Override
  public String getType(Uri u) {
    try {
      return item(u).mime;
    } catch (Exception e) {
      return "application/octet-stream";
    }
  }

  @Override
  public Cursor query(Uri u, String[] p, String s, String[] a, String order) {
    try {
      VaultApp.Shared f = item(u);
      MatrixCursor c =
          new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
      c.addRow(new Object[] {f.name, f.bytes.length});
      return c;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    VaultApp.Shared f = item(u);
    try {
      ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
      new Thread(
              () -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                  out.write(f.bytes);
                } catch (Exception ignored) {
                }
              })
          .start();
      return pipe[0];
    } catch (IOException e) {
      throw new FileNotFoundException("Could not open shared file");
    }
  }

  @Override
  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
