package com.docubuds.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.camera.core.*;
import androidx.camera.core.resolutionselector.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.*;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/** Captures JPEG into memory. No gallery file or unencrypted staging file is created. */
public final class NativeScanner extends Dialog implements LifecycleOwner {
  private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
  private final Consumer<byte[]> saved;
  private final Consumer<Exception> failed;
  private final Runnable touch;
  private ProcessCameraProvider provider;
  private ImageCapture capture;
  private boolean closed = false, capturing = false;
  private Button shutter;

  public NativeScanner(
      Context c, Consumer<byte[]> saved, Consumer<Exception> failed, Runnable touch) {
    super(c, android.R.style.Theme_Material_Light_NoActionBar);
    this.saved = saved;
    this.failed = failed;
    this.touch = touch;
  }

  @Override
  public Lifecycle getLifecycle() {
    return lifecycle;
  }

  private int dp(int n) {
    return (int) (n * getContext().getResources().getDisplayMetrics().density + .5f);
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    LinearLayout ui = new LinearLayout(getContext());
    ui.setOrientation(LinearLayout.VERTICAL);
    ui.setBackgroundColor(Color.rgb(20, 20, 29));
    ui.setPadding(dp(18), dp(38), dp(18), dp(24));
    TextView title = new TextView(getContext());
    title.setText("Scan a document");
    title.setTextColor(Color.WHITE);
    title.setTextSize(24);
    ui.addView(title);
    TextView sub = new TextView(getContext());
    sub.setText("Capture a clear page. It stays encrypted in DocuBuds.");
    sub.setTextColor(Color.LTGRAY);
    sub.setPadding(0, dp(12), 0, dp(18));
    ui.addView(sub);
    PreviewView preview = new PreviewView(getContext());
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    ui.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    shutter = new Button(getContext());
    shutter.setText("Capture page");
    shutter.setMinHeight(dp(60));
    shutter.setEnabled(false);
    ui.addView(shutter, new LinearLayout.LayoutParams(-1, dp(64)));
    Button flash = new Button(getContext());
    flash.setText("Flash: off");
    flash.setMinHeight(dp(52));
    flash.setOnClickListener(
        v -> {
          touch.run();
          if (capture != null) {
            boolean enabled = capture.getFlashMode() == ImageCapture.FLASH_MODE_OFF;
            capture.setFlashMode(
                enabled ? ImageCapture.FLASH_MODE_AUTO : ImageCapture.FLASH_MODE_OFF);
            flash.setText(enabled ? "Flash: auto" : "Flash: off");
          }
        });
    ui.addView(flash);
    Button cancel = new Button(getContext());
    cancel.setText("Cancel scan");
    cancel.setMinHeight(dp(52));
    cancel.setOnClickListener(v -> dismiss());
    ui.addView(cancel);
    setContentView(ui);
    lifecycle.setCurrentState(Lifecycle.State.RESUMED);
    var future = ProcessCameraProvider.getInstance(getContext());
    future.addListener(
        () -> {
          try {
            if (closed) return;
            provider = future.get();
            Preview p = new Preview.Builder().build();
            p.setSurfaceProvider(preview.getSurfaceProvider());
            capture =
                new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .setResolutionSelector(
                        new ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .build())
                    .build();
            CameraSelector selector =
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    ? CameraSelector.DEFAULT_BACK_CAMERA
                    : CameraSelector.DEFAULT_FRONT_CAMERA;
            provider.bindToLifecycle(this, selector, p, capture);
            shutter.setEnabled(true);
          } catch (Exception e) {
            dismiss();
            failed.accept(
                new Exception(
                    "Camera could not start. You can import an existing local image.", e));
          }
        },
        getContext().getMainExecutor());
    shutter.setOnClickListener(
        v -> {
          touch.run();
          if (capture == null || capturing) return;
          capturing = true;
          shutter.setEnabled(false);
          capture.setTargetRotation(preview.getDisplay().getRotation());
          capture.takePicture(
              getContext().getMainExecutor(),
              new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(ImageProxy image) {
                  byte[] bytes = null;
                  try {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    if (bytes.length > VaultStore.MAX_FILE)
                      throw new Exception(
                          "This capture exceeds 32 MB. Import a smaller original image.");
                  } catch (Exception e) {
                    if (bytes != null) java.util.Arrays.fill(bytes, (byte) 0);
                    image.close();
                    dismiss();
                    failed.accept(e);
                    return;
                  }
                  image.close();
                  if (closed) {
                    java.util.Arrays.fill(bytes, (byte) 0);
                    return;
                  }
                  dismiss();
                  saved.accept(bytes);
                }

                @Override
                public void onError(ImageCaptureException e) {
                  capturing = false;
                  shutter.setEnabled(true);
                  failed.accept(new Exception("Capture failed. Try again in better light.", e));
                }
              });
        });
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent e) {
    touch.run();
    return super.dispatchTouchEvent(e);
  }

  @Override
  public void dismiss() {
    if (!closed) {
      closed = true;
      lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
      if (provider != null) provider.unbindAll();
    }
    super.dismiss();
  }
}
