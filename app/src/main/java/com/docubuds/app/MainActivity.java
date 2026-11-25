package com.docubuds.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.*;
import android.net.Uri;
import android.os.*;
import android.os.storage.StorageManager;
import android.provider.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import javax.crypto.*;
import org.json.*;

public final class MainActivity extends Activity {
  private VaultApp app;
  private VaultStore store;
  private LinearLayout root, body, header, nav;
  private ScrollView scroll;
  private String tab = "Vault", space = "", record = "", query = "";
  private boolean authShowing = false, resumed = false, working = false, settingsDark = false;
  private final ArrayList<Dialog> dialogs = new ArrayList<>();
  private final HashSet<String> revealed = new HashSet<>();
  private String attachTarget = "", captureName = "";
  private byte[] exportBytes;
  private String exportMime, exportName;
  private boolean tearingDown = false;
  private boolean sessionShown = false;
  private NativeScanner scanner;
  private Runnable afterUnlock;
  private CancellationSignal authCancel;
  private long viewToken = 0;
  private int bg, ink, muted, card, line, accent = Color.rgb(0, 41, 112), lavender;
  private static final int IMPORT = 40, CAPTURE = 41, EXPORT = 42;
  private static final String UPI = "jaynishad2010@okhdfcbank",
      PAY = "upi://pay?pa=jaynishad2010@okhdfcbank&pn=Vijay%20Kumar&aid=uGICAgIDQqveaLw";
  private static final String[] TYPES = {
    "Document",
    "Aadhaar",
    "PAN",
    "Passport",
    "Driving licence",
    "Bank account",
    "Payment card",
    "Login",
    "Secure note",
    "Property",
    "Vehicle",
    "Insurance",
    "Health",
    "Education",
    "Offer letter",
    "Joining letter",
    "Payslip",
    "Experience letter",
    "Relieving letter",
    "Resume",
    "Tax record",
    "Warranty",
    "Travel",
    "Reminder",
    "Authenticator"
  };
  private final Handler clock = new Handler(Looper.getMainLooper());
  private final Runnable idle =
      new Runnable() {
        public void run() {
          if (store != null
              && store.unlocked()
              && SystemClock.elapsedRealtime() - app.lastInteraction >= 60000) lock(false);
          if (sessionShown && !store.unlocked()) lock(false);
          VaultApp.pruneShares();
          clock.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
    app = (VaultApp) getApplication();
    store = app.store;
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
    clock.post(idle);
    if ("reminders".equals(getIntent().getAction())) tab = "Reminders";
    colours();
    lockPage();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (sessionShown && !store.unlocked()) lock(false);
    if (store != null && store.unlocked()) {
      if (SystemClock.elapsedRealtime() - app.lastInteraction >= 60000) lock(false);
      else {
        applyPrivacy();
        if (afterUnlock != null) {
          Runnable r = afterUnlock;
          afterUnlock = null;
          r.run();
        }
      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (scanner != null && scanner.isShowing()) scanner.dismiss();
    resumed = false;
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  @Override
  protected void onDestroy() {
    tearingDown = true;
    for (Dialog d : new ArrayList<>(dialogs)) if (d.isShowing()) d.dismiss();
    dialogs.clear();
    clock.removeCallbacks(idle);
    if (authCancel != null) authCancel.cancel();
    super.onDestroy();
  }

  @Override
  public void onUserInteraction() {
    super.onUserInteraction();
    if (store != null && store.unlocked()) app.touch();
  }

  @android.annotation.SuppressLint(
      "GestureBackNavigation") // API 33+ dispatch is registered in onCreate; older devices use this
  // override.
  @Override
  public void onBackPressed() {
    if (!store.unlocked()) {
      super.onBackPressed();
      return;
    }
    if (!record.isEmpty()) {
      record = "";
      render();
    } else if (!space.isEmpty()) {
      try {
        JSONObject s = store.find("spaces", space);
        space = s == null ? "" : s.optString("parent");
      } catch (Exception e) {
        space = "";
      }
      render();
    } else if (!tab.equals("Vault")) {
      tab = "Vault";
      render();
    } else {
      lock(true);
    }
  }

  private int dp(float v) {
    return Math.round(getResources().getDisplayMetrics().density * v);
  }

  private void colours() {
    settingsDark = store != null && store.unlocked() && store.root().optBoolean("dark");
    bg = Color.parseColor(settingsDark ? "#101D2C" : "#F6F5FA");
    ink = Color.parseColor(settingsDark ? "#F0F7FF" : "#102A43");
    muted = Color.parseColor(settingsDark ? "#A9BDCF" : "#62788A");
    card = Color.parseColor(settingsDark ? "#17283A" : "#FFFFFF");
    line = Color.parseColor(settingsDark ? "#30475C" : "#E1EBF3");
    lavender = Color.parseColor(settingsDark ? "#352A50" : "#F0EBFC");
    accent = settingsDark ? Color.parseColor("#B8A1FF") : Color.parseColor("#7555DF");
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  private GradientDrawable border(int colour, int radius) {
    GradientDrawable g = shape(colour, radius);
    g.setStroke(dp(1), line);
    return g;
  }

  private TextView text(String s, int size, int colour, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(colour);
    t.setFontFeatureSettings("kern");
    if (bold)
      t.setTypeface(
          android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    t.setLineSpacing(dp(2), 1);
    return t;
  }

  private LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private void gap(LinearLayout l, int n) {
    View v = new View(this);
    l.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
  }

  private void add(LinearLayout l, View v) {
    l.addView(v, new LinearLayout.LayoutParams(-1, -2));
  }

  private void paragraph(String s) {
    TextView t = text(s, 15, muted, false);
    t.setPadding(0, dp(6), 0, dp(12));
    add(body, t);
  }

  private void heading(String s) {
    gap(body, 24);
    TextView t = text(s, 19, ink, true);
    add(body, t);
    gap(body, 14);
  }

  private TextView button(String label, Runnable action, boolean primary) {
    TextView t = text(label, 16, primary ? Color.WHITE : accent, true);
    t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(56));
    t.setPadding(dp(18), dp(14), dp(18), dp(14));
    t.setBackground(primary ? shape(accent, 15) : border(card, 15));
    t.setOnClickListener(v -> safe(action));
    t.setContentDescription(label);
    return t;
  }

  private View iconButton(String icon, String label, Runnable action) {
    FrameLayout f = new FrameLayout(this);
    f.setBackground(border(card, 13));
    Icons i = new Icons(this, icon, ink);
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
    f.addView(i, p);
    f.setContentDescription(label);
    f.setOnClickListener(v -> safe(action));
    f.setFocusable(true);
    return f;
  }

  private void safe(Runnable r) {
    try {
      r.run();
    } catch (Exception e) {
      error(e);
    }
  }

  private void watchDialog(Dialog d) {
    if (!store.unlocked() || !store.root().optBoolean("screenshots"))
      d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    if (!dialogs.contains(d)) dialogs.add(d);
    android.view.Window.Callback original = d.getWindow().getCallback();
    d.getWindow()
        .setCallback(
            (android.view.Window.Callback)
                java.lang.reflect.Proxy.newProxyInstance(
                    android.view.Window.Callback.class.getClassLoader(),
                    new Class[] {android.view.Window.Callback.class},
                    (proxy, method, args) -> {
                      if (method.getName().startsWith("dispatch")) app.touch();
                      try {
                        return method.invoke(original, args);
                      } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                      }
                    }));
  }

  private void error(Exception e) {
    if (isFinishing()) return;
    String m = e.getMessage();
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Could not complete this action")
            .setMessage(
                m == null ? "Please try again. Your saved records have not been removed." : m)
            .setPositiveButton("OK", null)
            .create();
    watchDialog(d);
    d.show();
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }

  private void layout(String title, boolean back, boolean bottom) {
    colours();
    root = col();
    root.setBackgroundColor(bg);
    root.setPadding(0, 0, 0, 0);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, in) -> {
          android.graphics.Insets a =
              in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          root.setPadding(a.left, a.top, a.right, a.bottom);
          return in;
        });
    root.requestApplyInsets();
    getWindow()
        .getInsetsController()
        .setSystemBarsAppearance(
            settingsDark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
    header = row();
    header.setPadding(dp(20), dp(12), dp(20), dp(10));
    if (back) {
      View b = iconButton("back", "Back", this::onBackPressed);
      header.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48)));
      View spacer = new View(this);
      header.addView(spacer, new LinearLayout.LayoutParams(dp(12), 1));
    } else {
      Icons logo = new Icons(this, "doc", accent);
      header.addView(logo, new LinearLayout.LayoutParams(dp(28), dp(30)));
    }
    TextView brand = text(title, 21, ink, true);
    if (title.equals("DocuBuds")) {
      android.text.SpannableString name = new android.text.SpannableString(title);
      name.setSpan(
          new android.text.style.ForegroundColorSpan(
              settingsDark ? ink : Color.parseColor("#7555DF")),
          0,
          4,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      name.setSpan(
          new android.text.style.ForegroundColorSpan(Color.parseColor("#7555DF")),
          4,
          title.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      brand.setText(name);
    }
    brand.setPadding(dp(8), 0, 0, 0);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    View l = iconButton("lock", "Lock vault", () -> lock(false));
    header.addView(l, new LinearLayout.LayoutParams(dp(48), dp(48)));
    add(root, header);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    body = col();
    body.setPadding(dp(22), dp(20), dp(22), dp(26));
    scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (bottom) navigation();
  }

  private void navigation() {
    LinearLayout dock = col();
    dock.setBackgroundColor(Color.TRANSPARENT);
    dock.setElevation(0);
    dock.setPadding(dp(16), dp(8), dp(16), dp(14));
    dock.setClipChildren(false);
    dock.setClipToPadding(false);
    LinearLayout tools = row();
    tools.setClipChildren(false);
    TextView search = text("Search your vault", 15, muted, false);
    search.setCompoundDrawablePadding(dp(10));
    android.graphics.drawable.Drawable searchIcon =
        getDrawable(android.R.drawable.ic_menu_search).mutate();
    searchIcon.setTint(muted);
    searchIcon.setBounds(0, 0, dp(20), dp(20));
    search.setCompoundDrawables(searchIcon, null, null, null);
    search.setGravity(Gravity.CENTER_VERTICAL);
    search.setPadding(dp(18), 0, dp(12), 0);
    GradientDrawable searchBackground = shape(settingsDark ? card : Color.WHITE, 16);
    searchBackground.setStroke(dp(1), accent);
    search.setBackground(searchBackground);
    search.setElevation(dp(2));
    search.setMinHeight(dp(56));
    search.setContentDescription("Search your vault");
    search.setOnClickListener(v -> openSearch());
    tools.addView(search, new LinearLayout.LayoutParams(0, dp(56), 1));
    add(dock, tools);
    gap(dock, 12);
    View dockDivider = new View(this);
    dockDivider.setBackgroundColor(
        settingsDark ? Color.parseColor("#30475C") : Color.parseColor("#DCE7EF"));
    LinearLayout.LayoutParams dividerLayout = new LinearLayout.LayoutParams(-1, dp(1));
    dividerLayout.setMargins(dp(6), 0, dp(6), 0);
    dock.addView(dockDivider, dividerLayout);
    gap(dock, 8);
    android.widget.FrameLayout floating = new android.widget.FrameLayout(this);
    floating.setClipChildren(false);
    floating.setClipToPadding(false);
    View pill = new View(this);
    pill.setBackground(shape(card, 30));
    pill.setElevation(dp(5));
    android.widget.FrameLayout.LayoutParams pillParams =
        new android.widget.FrameLayout.LayoutParams(-1, dp(72));
    pillParams.topMargin = dp(10);
    floating.addView(pill, pillParams);
    View crest = new View(this);
    crest.setBackground(shape(card, 32));
    crest.setElevation(dp(5));
    android.widget.FrameLayout.LayoutParams crestParams =
        new android.widget.FrameLayout.LayoutParams(
            dp(76), dp(64), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    floating.addView(crest, crestParams);
    nav = row();
    nav.setElevation(dp(6));
    for (String name : new String[] {"Vault", "Notes", "Add", "Reminders", "Settings"}) {
      if (name.equals("Add")) {
        LinearLayout centre = col();
        centre.setGravity(Gravity.CENTER);
        TextView plus = text("+", 30, Color.WHITE, false);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(shape(accent, 24));
        plus.setContentDescription("Add to DocuBuds");
        centre.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView caption = text("Add", 11, muted, false);
        caption.setGravity(Gravity.CENTER);
        add(centre, caption);
        centre.setOnClickListener(
            v -> {
              plus.animate()
                  .rotation(90)
                  .setDuration(180)
                  .withEndAction(() -> plus.setRotation(0))
                  .start();
              floatingAdd();
            });
        plus.setOnClickListener(v -> centre.performClick());
        nav.addView(centre, new LinearLayout.LayoutParams(0, dp(76), 1));
        continue;
      }
      LinearLayout chip = col();
      chip.setGravity(Gravity.CENTER);
      chip.setPadding(dp(4), dp(7), dp(4), dp(7));
      boolean selected = tab.equals(name);
      chip.setBackgroundColor(Color.TRANSPARENT);
      String icon =
          name.equals("Vault")
              ? "folder"
              : name.equals("Notes") ? "note" : name.equals("Reminders") ? "bell" : "settings";
      int colour = selected ? accent : muted;
      chip.addView(new Icons(this, icon, colour), new LinearLayout.LayoutParams(dp(22), dp(22)));
      TextView label = text(name, 11, colour, selected);
      label.setGravity(Gravity.CENTER);
      label.setSingleLine(true);
      label.setEllipsize(android.text.TextUtils.TruncateAt.END);
      add(chip, label);
      View underline = new View(this);
      underline.setBackground(shape(selected ? accent : Color.TRANSPARENT, 2));
      LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(dp(22), dp(3));
      up.gravity = Gravity.CENTER_HORIZONTAL;
      up.topMargin = dp(4);
      chip.addView(underline, up);
      chip.setContentDescription(name);
      chip.setOnClickListener(
          v -> {
            app.touch();
            chip.animate()
                .scaleX(.91f)
                .scaleY(.91f)
                .setDuration(80)
                .withEndAction(
                    () -> {
                      tab = name;
                      space = "";
                      record = "";
                      query = "";
                      render();
                      body.setAlpha(0);
                      body.setTranslationY(dp(8));
                      body.animate().alpha(1).translationY(0).setDuration(180).start();
                    })
                .start();
          });
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(76), 1);
      cp.setMargins(dp(3), 0, dp(3), 0);
      nav.addView(chip, cp);
    }
    floating.addView(nav, new android.widget.FrameLayout.LayoutParams(-1, dp(82)));
    dock.addView(floating, new LinearLayout.LayoutParams(-1, dp(82)));
    add(root, dock);
  }

  private void floatingAdd() {
    try {
      if (tab.equals("Notes") || isNotesSpace(space)) {
        noteEditor(null);
        return;
      }
      if (!record.isEmpty()) {
        JSONObject r = store.find("records", record);
        if (r != null) {
          if (r.optString("type").equals("Secure note")) noteEditor(null);
          else attachmentActions(r);
          return;
        }
      }
      if (tab.equals("Reminders")) {
        edit(null, "Reminder");
        return;
      }
      addMenu();
    } catch (Exception e) {
      error(e);
    }
  }

  private void lockPage() {
    sessionShown = false;
    colours();
    layout("DocuBuds", false, false);
    header.removeAllViews();
    header.setGravity(Gravity.CENTER);
    LinearLayout lockBrand = row();
    lockBrand.setGravity(Gravity.CENTER);
    lockBrand.addView(
        new Icons(this, "doc", accent), new LinearLayout.LayoutParams(dp(25), dp(28)));
    TextView name = text("DocuBuds", 22, ink, true);
    name.setPadding(dp(10), 0, 0, 0);
    lockBrand.addView(name);
    header.addView(lockBrand, new LinearLayout.LayoutParams(-2, -2));
    body.setGravity(Gravity.CENTER);
    boolean returning = store.exists();
    VaultArt art = new VaultArt(this, returning);
    body.addView(art, new LinearLayout.LayoutParams(-1, dp(returning ? 126 : 146)));
    gap(body, 18);
    TextView title =
        text(returning ? "Welcome back." : "Your essentials.\nOne private space.", 34, ink, true);
    title.setGravity(Gravity.CENTER);
    add(body, title);
    gap(body, 10);
    paragraph(
        returning
            ? "Your documents, right where you left them."
            : "Documents, passwords and personal notes. Together on your phone, ready when you need"
                + " them.");
    LinearLayout badges = row();
    badges.setGravity(Gravity.CENTER);
    for (String label : new String[] {"Offline", "Encrypted", "Ad-free"}) {
      TextView badge = text(label, 12, accent, true);
      badge.setPadding(dp(12), dp(8), dp(12), dp(8));
      badge.setBackground(shape(lavender, 16));
      LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
      bp.setMargins(dp(4), 0, dp(4), 0);
      badges.addView(badge, bp);
    }
    add(body, badges);
    gap(body, 26);
    if (returning) {
      String type = store.customType();
      add(
          body,
          button(
              type.isEmpty() ? "Unlock my vault" : "Unlock with app " + credentialLabel(type),
              type.isEmpty() ? this::authenticate : this::customUnlock,
              true));
      gap(body, 12);
      if (!type.isEmpty()) add(body, button("Use phone unlock", this::authenticate, false));
      paragraph("Private to this phone. Locks after one minute of inactivity.");
    } else {
      TextView notice = text("Stored only on this phone", 15, ink, true);
      add(body, notice);
      paragraph(
          "No cloud backup or recovery key. Losing this phone, uninstalling or clearing app data"
              + " can permanently remove your vault.");
      CheckBox acknowledge = new CheckBox(this);
      acknowledge.setText("I understand there is no backup or recovery.");
      acknowledge.setTextSize(14);
      acknowledge.setTextColor(ink);
      add(body, acknowledge);
      gap(body, 12);
      add(
          body,
          button(
              "Create my vault",
              () -> {
                if (acknowledge.isChecked()) authenticate();
                else toast("Please acknowledge the recovery limits first.");
              },
              true));
      paragraph("Start with phone unlock. Add your own app PIN or pattern in Settings.");
    }
    centreLockText(body);
    body.setAlpha(0);
    body.setTranslationY(dp(12));
    body.animate().alpha(1).translationY(0).setDuration(280).start();
    applyPrivacy();
  }

  private void centreLockText(View view) {
    if (view instanceof TextView) ((TextView) view).setGravity(Gravity.CENTER);
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) centreLockText(group.getChildAt(i));
    }
  }

  private void authenticate() {
    if (authShowing) return;
    KeyguardManager km = getSystemService(KeyguardManager.class);
    if (!km.isDeviceSecure()) {
      new AlertDialog.Builder(this)
          .setTitle("Set a phone screen lock")
          .setMessage(
              "DocuBuds needs your phone’s PIN, pattern or password to protect its encryption key.")
          .setPositiveButton(
              "Open security settings",
              (d, w) -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
          .setNegativeButton("Cancel", null)
          .show();
      return;
    }
    authShowing = true;
    authCancel = new CancellationSignal();
    new BiometricPrompt.Builder(this)
        .setTitle("Unlock DocuBuds")
        .setSubtitle("Use your phone’s secure authentication")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
        .authenticate(
            authCancel,
            getMainExecutor(),
            new BiometricPrompt.AuthenticationCallback() {
              @Override
              public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                authShowing = false;
                try {
                  store.unlock();
                  onUnlocked();
                } catch (Exception e) {
                  store.lock();
                  lockPage();
                  new AlertDialog.Builder(MainActivity.this)
                      .setTitle("Vault could not be unlocked")
                      .setMessage(
                          "The encrypted vault could not be opened. Try authenticating again. If"
                              + " the device key was removed or reset, this vault cannot be"
                              + " recovered. Your files will not be overwritten.")
                      .setPositiveButton("OK", null)
                      .show();
                }
              }

              @Override
              public void onAuthenticationError(int code, CharSequence msg) {
                authShowing = false;
                afterUnlock = null;
                toast(msg.toString());
              }
            });
  }

  private void onUnlocked() throws Exception {
    app.touch();
    store.root().put("screenshots", true);
    JSONArray spaces = store.root().getJSONArray("spaces");
    for (int i = 0; i < spaces.length(); i++) {
      JSONObject sp = spaces.getJSONObject(i);
      if (sp.optString("title").equalsIgnoreCase("Secure notes")) sp.put("kind", "notes");
    }
    store.save();
    purgeExpiredTrash();
    store.purgeOrphans();
    ReminderReceiver.schedule(this, store.root().getJSONArray("records"));
    render();
    if (afterUnlock != null) {
      Runnable pending = afterUnlock;
      afterUnlock = null;
      pending.run();
    }
  }

  private void lock(boolean exit) {
    viewToken++;
    for (Dialog d : dialogs) if (d.isShowing()) d.dismiss();
    dialogs.clear();
    revealed.clear();
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    store.lock();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    lockPage();
    if (exit) moveTaskToBack(true);
  }

  private void applyPrivacy() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  private void render() {
    if (!store.unlocked()) {
      lockPage();
      return;
    }
    sessionShown = true;
    viewToken++;
    try {
      applyPrivacy();
      if (!record.isEmpty()) {
        detail();
        return;
      }
      if (tab.equals("Notes")) {
        notesPage();
        return;
      }
      if (tab.equals("Settings")) {
        settings();
        return;
      }
      if (tab.equals("Reminders")) {
        reminders();
        return;
      }
      vault();
    } catch (Exception e) {
      error(e);
    }
  }

  private String iconFor(String s) {
    String n = s.toLowerCase(Locale.ROOT);
    if (n.contains("bank")) return "bank";
    if (n.contains("propert") || n.contains("home") || n.contains("shop") || n.contains("farm"))
      return "home";
    if (n.contains("work") || n.contains("company")) return "work";
    if (n.contains("vehicle")) return "car";
    if (n.contains("id")) return "id";
    return "folder";
  }

  private int count(String id) throws Exception {
    int n = 0;
    JSONArray a = store.root().getJSONArray("records");
    for (int i = 0; i < a.length(); i++) {
      JSONObject r = a.getJSONObject(i);
      if (r.optString("parent").equals(id) && !r.optBoolean("trash") && !r.optBoolean("archived"))
        n++;
    }
    return n;
  }

  private View item(String title, String subtitle, String icon, int colour, Runnable action) {
    LinearLayout r = row();
    r.setPadding(dp(14), dp(15), dp(14), dp(15));
    r.setMinimumHeight(dp(82));
    r.setBackground(border(card, 17));
    FrameLayout badge = new FrameLayout(this);
    badge.setBackground(shape(lavender, 13));
    Icons ic = new Icons(this, icon, colour);
    badge.addView(ic, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
    r.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
    LinearLayout labels = col();
    labels.setPadding(dp(16), 0, dp(8), 0);
    add(labels, text(title, 16, ink, true));
    if (subtitle != null && !subtitle.isEmpty()) {
      gap(labels, 4);
      add(labels, text(subtitle, 14, muted, false));
    }
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(new Icons(this, "next", muted), new LinearLayout.LayoutParams(dp(19), dp(22)));
    r.setOnClickListener(v -> safe(action));
    r.setContentDescription(title + (subtitle == null ? "" : ", " + subtitle));
    LinearLayout outer = col();
    add(outer, r);
    gap(outer, 10);
    r.setOnLongClickListener(v -> outer.performLongClick());
    return outer;
  }

  private EditText input(String hint, String value, boolean multiline) {
    EditText e = new EditText(this);
    e.setTextSize(16);
    e.setTextColor(ink);
    e.setHintTextColor(muted);
    e.setHint(hint);
    e.setText(value);
    e.setPadding(dp(18), dp(16), dp(18), dp(16));
    e.setBackground(border(card, 14));
    e.setMinHeight(dp(60));
    e.setSingleLine(!multiline);
    if (multiline) {
      e.setGravity(Gravity.TOP);
      e.setMinLines(3);
      e.setInputType(
          android.text.InputType.TYPE_CLASS_TEXT
              | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
              | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
              | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    } else
      e.setInputType(
          android.text.InputType.TYPE_CLASS_TEXT
              | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    e.setSaveEnabled(false);
    e.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            app.touch();
          }

          public void afterTextChanged(Editable e) {}
        });
    return e;
  }

  private EditText searchField(String hint, java.util.function.Consumer<String> changed) {
    EditText e = input(hint, query, false);
    e.setCompoundDrawablePadding(dp(10));
    e.setContentDescription(hint);
    e.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int c) {
            query = s.toString();
            changed.accept(query);
          }

          public void afterTextChanged(Editable x) {}
        });
    return e;
  }

  private void vault() throws Exception {
    JSONObject current = space.isEmpty() ? null : store.find("spaces", space);
    if (!space.isEmpty() && current == null) space = "";
    layout("DocuBuds", !space.isEmpty(), true);
    if (space.isEmpty()) {
      add(
          body,
          text(
              (store.root().optString("name", "My vault").equals("My vault")
                  ? "Your vault"
                  : store.root().optString("name")),
              25,
              ink,
              true));
      gap(body, 6);
      paragraph("Your documents, organised on this phone");
      quickActions();
    } else {
      add(body, text(current.getString("title"), 27, ink, true));
      gap(body, 8);
      paragraph(count(space) + " records · On this phone");
    }
    if (isNotesSpace(space)) {
      notesContent(space);
      return;
    }
    LinearLayout results = col();
    add(body, results);
    populate(results);
    support(false);
  }

  private boolean under(String parent, String ancestor) throws Exception {
    if (ancestor.isEmpty()) return true;
    Set<String> seen = new HashSet<>();
    while (!parent.isEmpty() && seen.add(parent)) {
      if (parent.equals(ancestor)) return true;
      JSONObject s = store.find("spaces", parent);
      if (s == null) break;
      parent = s.optString("parent");
    }
    return false;
  }

  private String path(String parent) throws Exception {
    ArrayList<String> a = new ArrayList<>();
    HashSet<String> seen = new HashSet<>();
    while (!parent.isEmpty() && seen.add(parent)) {
      JSONObject p = store.find("spaces", parent);
      if (p == null) break;
      a.add(0, p.optString("title"));
      parent = p.optString("parent");
    }
    return String.join(" / ", a);
  }

  private void populate(LinearLayout target) {
    target.removeAllViews();
    try {
      String q = query.trim().toLowerCase(Locale.ROOT);
      JSONArray spaces = store.root().getJSONArray("spaces"),
          records = store.root().getJSONArray("records");
      int shown = 0;
      TextView h = text(q.isEmpty() ? "Your spaces" : "Search results", 19, ink, true);
      if (space.isEmpty() && q.isEmpty()) {
        target.setBackground(border(card, 20));
        target.setPadding(dp(12), dp(16), dp(12), dp(8));
      }
      target.addView(h);
      gap(target, 16);
      int columns = space.isEmpty() ? 3 : 2;
      LinearLayout gridRow = null;
      int gridIndex = 0;
      for (int i = 0; i < spaces.length(); i++) {
        JSONObject sp = spaces.getJSONObject(i);
        String id = sp.getString("id"), title = sp.getString("title");
        if (q.isEmpty()
            ? !sp.optString("parent").equals(space)
            : !under(sp.optString("parent"), space) || !title.toLowerCase(Locale.ROOT).contains(q))
          continue;
        if (gridIndex % columns == 0) {
          gridRow = row();
          gridRow.setGravity(Gravity.TOP);
          add(target, gridRow);
          gap(target, 12);
        }
        View tile = spaceTile(sp, gridIndex);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (gridIndex % columns != columns - 1) tileParams.setMarginEnd(dp(8));
        gridRow.addView(tile, tileParams);
        gridIndex++;
        shown++;
      }
      if (gridRow != null && gridIndex % columns != 0)
        for (int extra = gridIndex % columns; extra < columns; extra++)
          gridRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
      ArrayList<JSONObject> list = new ArrayList<>();
      for (int i = 0; i < records.length(); i++) {
        JSONObject r = records.getJSONObject(i);
        if (r.optBoolean("trash") || r.optBoolean("archived")) continue;
        if (q.isEmpty()) {
          if (!space.isEmpty() && !r.optString("parent").equals(space)) continue;
          if (space.isEmpty()
              && !r.optBoolean("pinned")
              && !store.root().optBoolean("recent", true)) continue;
        } else {
          if (!under(r.optString("parent"), space)) continue;
          String searchable =
              r.optString("title")
                  + " "
                  + r.optString("type")
                  + " "
                  + r.optString("notes")
                  + " "
                  + r.optString("tags")
                  + " "
                  + r.optString("ocr");
          JSONObject f = r.optJSONObject("fields");
          if (f != null)
            for (Iterator<String> it = f.keys(); it.hasNext(); ) {
              String k = it.next();
              if (!sensitive(k)) searchable += " " + f.optString(k);
            }
          if (!searchable.toLowerCase(Locale.ROOT).contains(q)) continue;
        }
        list.add(r);
      }
      list.sort(
          (a, b) ->
              Boolean.compare(b.optBoolean("pinned"), a.optBoolean("pinned")) != 0
                  ? Boolean.compare(b.optBoolean("pinned"), a.optBoolean("pinned"))
                  : Long.compare(b.optLong("updated"), a.optLong("updated")));
      if (!list.isEmpty()) {
        gap(target, 20);
        add(
            target,
            text(
                q.isEmpty()
                    ? (space.isEmpty() ? "Pinned & recently added" : "Documents & details")
                    : "Matching records",
                19,
                ink,
                true));
        gap(target, 14);
      }
      int max = q.isEmpty() && space.isEmpty() ? 12 : Integer.MAX_VALUE;
      for (JSONObject r : list) {
        if (max-- <= 0) break;
        String id = r.getString("id");
        String sub = r.optString("type") + (r.optBoolean("pinned") ? " · Pinned" : "");
        String p = path(r.optString("parent"));
        if (!p.isEmpty()) sub += "\n" + p;
        add(
            target,
            item(
                r.optString("title"),
                sub,
                "doc",
                accent,
                () -> {
                  record = id;
                  revealed.clear();
                  query = "";
                  render();
                }));
        shown++;
      }
      if (shown == 0) {
        gap(target, 18);
        add(
            target,
            text(q.isEmpty() ? "A space for what matters" : "No matching records", 20, ink, true));
        gap(target, 10);
        add(
            target,
            text(
                q.isEmpty()
                    ? "Add a document, save private details, or create your own space."
                    : "Try a different title, tag or document detail.",
                16,
                muted,
                false));
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private AlertDialog showDialog(
      String title, View view, String positive, DialogInterface.OnClickListener save) {
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(positive, save)
            .setNegativeButton("Cancel", null)
            .create();
    dialogs.add(d);
    watchDialog(d);
    d.getWindow().setBackgroundDrawable(shape(card, 26));
    d.show();
    d.getButton(-1).setAllCaps(false);
    d.getButton(-2).setAllCaps(false);
    d.getButton(-1).setMinHeight(dp(52));
    d.getButton(-2).setMinHeight(dp(52));
    return d;
  }

  private LinearLayout form() {
    LinearLayout l = col();
    l.setPadding(dp(22), dp(12), dp(22), dp(12));
    return l;
  }

  private ScrollView formScroll(LinearLayout l) {
    ScrollView s = new ScrollView(this);
    s.addView(l);
    return s;
  }

  private void createSpace() {
    LinearLayout f = form();
    EditText name = input("Space name", "", false);
    add(f, name);
    gap(f, 14);
    Spinner kind = new Spinner(this);
    kind.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Folder", "Property", "Company", "Vehicle"}));
    add(f, kind);
    paragraphOn(
        f,
        "Choose any name, for example Home documents, Shop, or an employer. Templates create useful"
            + " subfolders.");
    AlertDialog d = showDialog("Create a space", formScroll(f), "Create", null);
    d.getButton(-1)
        .setOnClickListener(
            v -> {
              try {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                  name.setError("Enter a name");
                  return;
                }
                String k = kind.getSelectedItem().toString();
                JSONObject s = store.addSpace(n, space, k);
                String id = s.getString("id");
                String[] subs =
                    k.equals("Property")
                        ? new String[] {
                          "Ownership papers",
                          "Tax receipts",
                          "Agreements",
                          "Plans and approvals",
                          "Utilities",
                          "Maintenance",
                          "Insurance"
                        }
                        : k.equals("Company")
                            ? new String[] {
                              "Offer and joining",
                              "Contracts",
                              "Payslips",
                              "Appraisals",
                              "Experience and leaving",
                              "Resumes"
                            }
                            : k.equals("Vehicle")
                                ? new String[] {
                                  "Registration", "Insurance", "Service history", "Purchase papers"
                                }
                                : new String[] {};
                for (String sub : subs) store.addSpace(sub, id, "folder");
                store.save();
                d.dismiss();
                space = id;
                render();
              } catch (Exception e) {
                error(e);
              }
            });
  }

  private void paragraphOn(LinearLayout l, String s) {
    TextView t = text(s, 14, muted, false);
    t.setPadding(0, dp(12), 0, dp(10));
    add(l, t);
  }

  private void chooseDestination(
      String current, java.util.function.Consumer<String> done, String blocked) {
    try {
      ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
      ids.add("");
      names.add("Vault root");
      JSONArray a = store.root().getJSONArray("spaces");
      for (int i = 0; i < a.length(); i++) {
        JSONObject s = a.getJSONObject(i);
        if (blocked != null && under(s.getString("id"), blocked)) continue;
        ids.add(s.getString("id"));
        names.add(path(s.getString("id")));
      }
      AlertDialog d =
          new AlertDialog.Builder(this)
              .setTitle("Choose destination")
              .setItems(names.toArray(new String[0]), (x, w) -> done.accept(ids.get(w)))
              .setNegativeButton("Cancel", null)
              .create();
      dialogs.add(d);
      watchDialog(d);
      d.show();
    } catch (Exception e) {
      error(e);
    }
  }

  private void spaceMenu(String id) {
    try {
      JSONObject s = store.find("spaces", id);
      optionsSheet(
          s.optString("title"),
          "Organise this space",
          new String[] {"Rename", "Move", "Delete empty space", "Move earlier", "Move later"},
          new String[] {"doc", "folder", "doc", "back", "next"},
          w -> {
            if (w == 3 || w == 4) {
              try {
                reorder(
                    store.root().getJSONArray("spaces"),
                    id,
                    w == 3 ? -1 : 1,
                    s.optString("parent"));
                store.save();
                render();
              } catch (Exception ex) {
                error(ex);
              }
            }
            if (w == 0) {
              LinearLayout f = form();
              EditText e = input("Space name", s.optString("title"), false);
              add(f, e);
              showDialog(
                  "Rename space",
                  f,
                  "Save",
                  (a, b) -> {
                    try {
                      String n = e.getText().toString().trim();
                      if (!n.isEmpty()) {
                        s.put("title", n);
                        store.save();
                        render();
                      }
                    } catch (Exception x) {
                      error(x);
                    }
                  });
            }
            if (w == 1)
              chooseDestination(
                  s.optString("parent"),
                  parent -> {
                    try {
                      s.put("parent", parent);
                      store.save();
                      render();
                    } catch (Exception x) {
                      error(x);
                    }
                  },
                  id);
            if (w == 2) {
              try {
                JSONArray ss = store.root().getJSONArray("spaces"),
                    rr = store.root().getJSONArray("records");
                for (int i = 0; i < ss.length(); i++)
                  if (ss.getJSONObject(i).optString("parent").equals(id))
                    throw new Exception("Move or delete the subspaces first.");
                for (int i = 0; i < rr.length(); i++)
                  if (rr.getJSONObject(i).optString("parent").equals(id))
                    throw new Exception(
                        "Move or permanently delete this space’s records first, including"
                            + " archived and trashed records.");
                store.remove("spaces", id);
                store.save();
                render();
              } catch (Exception x) {
                error(x);
              }
            }
          });
    } catch (Exception e) {
      error(e);
    }
  }

  private void reorder(JSONArray a, String id, int direction, String parent) throws Exception {
    for (int i = 0; i < a.length(); i++)
      if (a.getJSONObject(i).optString("id").equals(id)) {
        for (int j = i + direction; j >= 0 && j < a.length(); j += direction) {
          if (parent != null && !a.getJSONObject(j).optString("parent").equals(parent)) continue;
          Object x = a.get(i);
          a.put(i, a.get(j));
          a.put(j, x);
          return;
        }
        return;
      }
  }

  private void exposeMenu(View wrapper, String name, Runnable action) {
    LinearLayout outer = (LinearLayout) wrapper;
    LinearLayout row = (LinearLayout) outer.getChildAt(0);
    row.removeViewAt(row.getChildCount() - 1);
    View more = iconButton("more", "Options for " + name, action);
    row.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
  }

  private void addMenu() {
    LinearLayout choices = col();
    Dialog[] holder = {null};
    String[] titles = {
      "Import files", "Scan page", "New note", "Save details", "New space", "Reminder"
    };
    String[] subtitles = {"On device", "Camera", "Type here", "IDs & logins", "Organise", "Dates"};
    String[] icons = {"folder", "scan", "note", "id", "plus", "bell"};
    for (int i = 0; i < titles.length; i += 3) {
      LinearLayout pair = row();
      for (int j = i; j < i + 3; j++) {
        final int choice = j;
        LinearLayout tile = col();
        tile.setPadding(dp(16), dp(14), dp(12), dp(14));
        tile.setBackground(shape(bg, 20));
        tile.setMinimumHeight(dp(110));
        tile.addView(
            new Icons(this, icons[j], badgeInk(j)), new LinearLayout.LayoutParams(dp(25), dp(25)));
        gap(tile, 10);
        add(tile, text(titles[j], 14, ink, true));
        add(tile, text(subtitles[j], 12, muted, false));
        tile.setContentDescription(titles[j]);
        tile.setOnClickListener(
            v -> {
              holder[0].dismiss();
              switch (choice) {
                case 0:
                  importFiles("");
                  break;
                case 1:
                  capture("");
                  break;
                case 2:
                  noteEditor(null);
                  break;
                case 3:
                  edit(null, null);
                  break;
                case 4:
                  createSpace();
                  break;
                case 5:
                  edit(null, "Reminder");
                  break;
              }
            });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        if (j < i + 2) tp.setMarginEnd(dp(10));
        pair.addView(tile, tp);
      }
      add(choices, pair);
      gap(choices, 12);
    }
    holder[0] = sheet("Add to DocuBuds", "Choose a quick action", choices);
  }

  private String[] fieldsFor(String type) {
    return switch (type) {
      case "Aadhaar" -> new String[] {"Holder name", "Aadhaar number", "Address"};
      case "PAN" -> new String[] {"Holder name", "PAN number"};
      case "Passport" -> new String[] {"Holder name", "Passport number", "Country", "Issue date"};
      case "Driving licence" -> new String[] {"Holder name", "Licence number", "Issuer"};
      case "Bank account" ->
          new String[] {
            "Account holder", "Bank", "Account number", "IFSC", "Branch", "Account type"
          };
      case "Payment card" ->
          new String[] {
            "Card holder", "Issuer", "Card number", "Expiry month and year", "Support number"
          };
      case "Login" -> new String[] {"Service", "Website", "Email or username", "Password"};
      case "Property" ->
          new String[] {
            "Property name", "Address", "Owner", "Property type", "Registration number"
          };
      case "Vehicle" -> new String[] {"Vehicle name", "Registration number", "Model", "Owner"};
      case "Insurance" -> new String[] {"Insurer", "Policy number", "Insured person", "Nominee"};
      case "Health" -> new String[] {"Person", "Provider", "Record date"};
      case "Education" -> new String[] {"Institution", "Qualification", "Person", "Year"};
      case "Payslip" -> new String[] {"Company", "Employee ID", "Year", "Month"};
      case "Offer letter", "Joining letter", "Experience letter", "Relieving letter" ->
          new String[] {"Company", "Role", "Employee ID", "Joining date", "Leaving date"};
      case "Resume" -> new String[] {"Version label", "Associated company", "Version date"};
      case "Tax record" -> new String[] {"Person", "Tax year", "Reference number"};
      case "Warranty" -> new String[] {"Item", "Seller", "Purchase date"};
      case "Travel" -> new String[] {"Trip", "Booking reference", "Person"};
      case "Authenticator" -> new String[] {"Service", "Account", "Secret (Base32)"};
      default -> new String[] {};
    };
  }

  private void edit(JSONObject existing, String preset) {
    if ("Secure note".equals(preset)
        || (existing != null && existing.optString("type").equals("Secure note"))) {
      noteEditor(existing);
      return;
    }
    try {
      boolean fresh = existing == null;
      JSONObject draft = fresh ? new JSONObject() : new JSONObject(existing.toString());
      LinearLayout f = form();
      EditText title = input("Record title", draft.optString("title"), false);
      add(f, title);
      gap(f, 14);
      Spinner type = new Spinner(this);
      type.setAdapter(
          new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TYPES));
      String selected =
          fresh ? (preset == null ? "Document" : preset) : draft.optString("type", "Document");
      type.setSelection(Math.max(0, Arrays.asList(TYPES).indexOf(selected)));
      add(f, type);
      gap(f, 12);
      TextView dest =
          button("Destination: " + (space.isEmpty() ? "Vault root" : path(space)), () -> {}, false);
      String[] parent = {fresh ? space : draft.optString("parent")};
      dest.setText("Destination: " + (parent[0].isEmpty() ? "Vault root" : path(parent[0])));
      dest.setOnClickListener(
          v ->
              chooseDestination(
                  parent[0],
                  id -> {
                    parent[0] = id;
                    try {
                      dest.setText("Destination: " + (id.isEmpty() ? "Vault root" : path(id)));
                    } catch (Exception ignored) {
                    }
                  },
                  null));
      add(f, dest);
      gap(f, 16);
      LinearLayout fs = col();
      add(f, fs);
      LinkedHashMap<String, EditText> editors = new LinkedHashMap<>();
      JSONObject old = draft.optJSONObject("fields");
      if (old == null) old = new JSONObject();
      final JSONObject initial = old;
      Runnable rebuild =
          () -> {
            for (var entry : editors.entrySet())
              try {
                initial.put(entry.getKey(), entry.getValue().getText().toString());
              } catch (Exception ignored) {
              }
            fs.removeAllViews();
            editors.clear();
            String ty = type.getSelectedItem().toString();
            LinkedHashSet<String> labels = new LinkedHashSet<>(Arrays.asList(fieldsFor(ty)));
            for (Iterator<String> it = initial.keys(); it.hasNext(); ) labels.add(it.next());
            for (String label : labels) addField(fs, editors, label, initial.optString(label));
          };
      type.setOnItemSelectedListener(
          new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int p, long id) {
              rebuild.run();
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
          });
      rebuild.run();
      add(
          f,
          button(
              "＋  Add custom field",
              () -> {
                LinearLayout ff = form();
                EditText n = input("Field label", "", false);
                add(ff, n);
                showDialog(
                    "Custom field",
                    ff,
                    "Add",
                    (d, w) -> {
                      String k = n.getText().toString().trim();
                      if (!k.isEmpty() && !editors.containsKey(k)) addField(fs, editors, k, "");
                    });
              },
              false));
      gap(f, 16);
      EditText note = input("Private notes", draft.optString("notes"), true);
      add(f, note);
      gap(f, 16);
      EditText tags = input("Tags, separated by commas", draft.optString("tags"), false);
      add(f, tags);
      gap(f, 16);
      String[] expiry = {draft.optString("expiry")};
      TextView date =
          button(expiry[0].isEmpty() ? "Set expiry or reminder date" : expiry[0], () -> {}, false);
      date.setOnClickListener(
          v ->
              pickDate(
                  expiry[0],
                  val -> {
                    expiry[0] = val;
                    date.setText(val);
                  }));
      add(f, date);
      gap(f, 8);
      add(
          f,
          button(
              "Clear date",
              () -> {
                expiry[0] = "";
                date.setText("Set expiry or reminder date");
              },
              false));
      paragraphOn(
          f,
          "Dates create private local reminders 7 days before and on the date. Notification timing"
              + " depends on Android settings. Files are added after saving this record.");
      AlertDialog d = showDialog(fresh ? "New record" : "Edit record", formScroll(f), "Save", null);
      d.getButton(-1)
          .setOnClickListener(
              v -> {
                try {
                  String n = title.getText().toString().trim();
                  if (n.isEmpty()) {
                    title.setError("Enter a title");
                    return;
                  }
                  String ty = type.getSelectedItem().toString();
                  if (ty.equals("Reminder") && expiry[0].isEmpty()) {
                    toast("Choose a reminder date.");
                    return;
                  }
                  JSONObject fields = new JSONObject();
                  for (var entry : editors.entrySet())
                    fields.put(entry.getKey(), entry.getValue().getText().toString());
                  if (ty.equals("Authenticator")) {
                    totp(fields.optString("Secret (Base32)"), System.currentTimeMillis());
                  }
                  long now = System.currentTimeMillis();
                  if (!fresh) rememberVersion(existing);
                  JSONObject r = fresh ? draft : existing;
                  if (fresh) {
                    r.put("id", UUID.randomUUID().toString())
                        .put("created", now)
                        .put("files", new JSONArray());
                    store.root().getJSONArray("records").put(r);
                  }
                  r.put("title", n)
                      .put("type", ty)
                      .put("parent", parent[0])
                      .put("fields", fields)
                      .put("notes", note.getText().toString())
                      .put("tags", tags.getText().toString())
                      .put("expiry", expiry[0])
                      .put("updated", now);
                  store.save();
                  schedule();
                  d.dismiss();
                  record = r.getString("id");
                  render();
                } catch (Exception ex) {
                  error(ex);
                }
              });
    } catch (Exception e) {
      error(e);
    }
  }

  private void addField(LinearLayout f, Map<String, EditText> map, String label, String value) {
    TextView l = text(label, 14, muted, true);
    add(f, l);
    gap(f, 6);
    EditText e = input(label, value, false);
    if (sensitive(label)) {
      e.setInputType(
          android.text.InputType.TYPE_CLASS_TEXT
              | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
      e.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }
    add(f, e);
    map.put(label, e);
    if (sensitive(label)) {
      TextView reveal = text("Show / hide", 14, accent, true);
      reveal.setMinHeight(dp(48));
      reveal.setGravity(Gravity.CENTER_VERTICAL);
      reveal.setOnClickListener(
          v -> {
            e.setTransformationMethod(
                e.getTransformationMethod() == null
                    ? PasswordTransformationMethod.getInstance()
                    : null);
          });
      add(f, reveal);
    }
    gap(f, 14);
  }

  private boolean sensitive(String label) {
    String l = label.toLowerCase(Locale.ROOT);
    return l.contains("password")
        || l.contains("secret")
        || l.contains("number")
        || l.contains("aadhaar")
        || l.contains("pan ")
        || l.contains("employee id");
  }

  private void pickDate(String value, java.util.function.Consumer<String> done) {
    LocalDate d;
    try {
      d = LocalDate.parse(value);
    } catch (Exception e) {
      d = LocalDate.now();
    }
    DatePickerDialog dp =
        new DatePickerDialog(
            this,
            (v, y, m, day) -> done.accept(LocalDate.of(y, m + 1, day).toString()),
            d.getYear(),
            d.getMonthValue() - 1,
            d.getDayOfMonth());
    dialogs.add(dp);
    watchDialog(dp);
    dp.show();
  }

  private void rememberVersion(JSONObject r) throws Exception {
    JSONArray history = r.optJSONArray("history");
    if (history == null) history = new JSONArray();
    JSONObject copy = new JSONObject(r.toString());
    copy.remove("history");
    copy.remove("files");
    history.put(copy);
    while (history.length() > 10) history.remove(0);
    r.put("history", history);
  }

  private void schedule() throws Exception {
    ReminderReceiver.schedule(this, store.root().getJSONArray("records"));
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 51);
  }

  private void detail() throws Exception {
    JSONObject r = store.find("records", record);
    if (r == null) {
      record = "";
      render();
      return;
    }
    if (r.optString("type").equals("Secure note") && !r.optBoolean("trash")) {
      noteDetail(r);
      return;
    }
    layout("DocuBuds", true, true);
    add(body, text(r.optString("title"), 27, ink, true));
    gap(body, 8);
    paragraph(
        r.optString("type")
            + (r.optString("parent").isEmpty() ? "" : "\n" + path(r.optString("parent"))));
    if (r.optBoolean("trash")) {
      paragraph("In trash. Permanently deleted after 30 days.");
      add(
          body,
          button(
              "Restore record",
              () -> {
                try {
                  r.put("trash", false);
                  r.remove("deletedAt");
                  store.save();
                  schedule();
                  render();
                } catch (Exception e) {
                  error(e);
                }
              },
              true));
      gap(body, 12);
      add(
          body,
          button(
              "Delete permanently",
              () ->
                  confirm(
                      "Permanently delete?",
                      "This removes this record and its attachments from DocuBuds. Exported copies"
                          + " are unaffected.",
                      () -> permanentDelete(r)),
              false));
      support(false);
      return;
    }
    View options = iconButton("more", "Document actions", () -> recordActions(r));
    LinearLayout.LayoutParams optionsLayout = new LinearLayout.LayoutParams(dp(48), dp(48));
    optionsLayout.setMarginEnd(dp(10));
    header.addView(options, header.getChildCount() - 1, optionsLayout);

    gap(body, 14);
    JSONObject fields = r.optJSONObject("fields");
    if (fields != null)
      for (Iterator<String> it = fields.keys(); it.hasNext(); ) {
        String key = it.next(), val = fields.optString(key);
        if (val.isEmpty()) continue;
        LinearLayout box = col();
        box.setBackground(border(card, 15));
        box.setPadding(dp(16), dp(14), dp(16), dp(12));
        add(box, text(key, 13, muted, true));
        gap(box, 6);
        boolean hidden = sensitive(key) && !revealed.contains(key);
        TextView value = text(hidden ? "••••••••" : val, 17, ink, false);
        value.setTextIsSelectable(!hidden);
        add(box, value);
        LinearLayout small = row();
        TextView reveal =
            button(
                hidden ? "Reveal" : "Hide",
                () -> {
                  if (revealed.contains(key)) revealed.remove(key);
                  else revealed.add(key);
                  render();
                },
                false);
        if (sensitive(key)) small.addView(reveal, new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView copy = button("Copy", () -> copyText(key, val, true), false);
        small.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1));
        gap(box, 10);
        add(box, small);
        add(body, box);
        gap(body, 12);
      }
    if (r.optString("type").equals("Authenticator")) {
      heading("Authenticator code");
      TextView code = text("", 30, accent, true);
      add(body, code);
      String secret = fields == null ? "" : fields.optString("Secret (Base32)");
      Runnable tick =
          new Runnable() {
            public void run() {
              if (!store.unlocked() || !record.equals(r.optString("id")) || !resumed) return;
              try {
                code.setText(
                    totp(secret, System.currentTimeMillis())
                        + "   "
                        + (30 - System.currentTimeMillis() / 1000 % 30)
                        + "s");
                clock.postDelayed(this, 1000);
              } catch (Exception e) {
                code.setText("Check the saved secret");
              }
            }
          };
      tick.run();
      paragraph("Generated on this phone. The device clock must be accurate.");
    }
    if (!r.optString("notes").isEmpty()) {
      heading("Private notes");
      TextView n = text(r.optString("notes"), 16, ink, false);
      n.setTextIsSelectable(true);
      add(body, n);
    }
    if (!r.optString("tags").isEmpty()) paragraph("Tags: " + r.optString("tags"));
    if (!r.optString("expiry").isEmpty())
      paragraph("Expiry / reminder date: " + r.optString("expiry"));
    heading("Attachments");
    JSONArray files = r.optJSONArray("files");
    if (files == null) files = new JSONArray();
    for (int i = 0; i < files.length(); i++) {
      JSONObject f = files.getJSONObject(i);
      View row =
          item(
              f.optString("name"),
              formatSize(f.optLong("size")) + " · Original file",
              "doc",
              accent,
              () -> preview(f));
      row.setOnLongClickListener(
          v -> {
            fileOptions(r, f);
            return true;
          });
      exposeMenu(row, f.optString("name"), () -> fileOptions(r, f));
      add(body, row);
    }
    if (files.length() == 0)
      paragraph("No attachments yet. Import a local file or capture a document.");
    gap(body, 12);
    support(false);
  }

  private void attachmentActions(JSONObject r) {
    optionsSheet(
        "Add an attachment",
        "Choose where your document comes from.",
        new String[] {"Import local files", "Scan a page"},
        new String[] {"folder", "doc"},
        i -> {
          if (i == 0) importFiles(r.optString("id"));
          else capture(r.optString("id"));
        });
  }

  private void recordActions(JSONObject r) {
    if (r.optString("type").equals("Secure note")) {
      noteActions(r);
      return;
    }
    String[] labels = {
      "Edit details",
      r.optBoolean("pinned") ? "Unpin record" : "Pin record",
      "Share files",
      "Share or export details",
      "Create PDF from images",
      "Move to another space",
      r.optBoolean("archived") ? "Unarchive record" : "Archive record",
      "View edit history",
      "Move to trash"
    };
    optionsSheet(
        "Document actions",
        "Everything you can do with this record.",
        labels,
        null,
        i -> {
          try {
            switch (i) {
              case 0:
                edit(r, null);
                break;
              case 1:
                r.put("pinned", !r.optBoolean("pinned"));
                store.save();
                render();
                break;
              case 2:
                selectShare(r);
                break;
              case 3:
                shareDetails(r);
                break;
              case 4:
                makePdf(r);
                break;
              case 5:
                chooseDestination(
                    r.optString("parent"),
                    id -> {
                      try {
                        r.put("parent", id);
                        r.put("updated", System.currentTimeMillis());
                        store.save();
                        render();
                      } catch (Exception e) {
                        error(e);
                      }
                    },
                    null);
                break;
              case 6:
                r.put("archived", !r.optBoolean("archived"));
                store.save();
                schedule();
                render();
                break;
              case 7:
                history(r);
                break;
              case 8:
                confirm(
                    "Move record to trash?",
                    "Restore it from Settings → Trash for 30 days.",
                    () -> {
                      try {
                        r.put("trash", true).put("deletedAt", System.currentTimeMillis());
                        store.save();
                        schedule();
                        record = "";
                        render();
                      } catch (Exception e) {
                        error(e);
                      }
                    });
                break;
            }
          } catch (Exception e) {
            error(e);
          }
        });
  }

  private void noteActions(JSONObject r) {
    optionsSheet(
        "Note actions",
        "Keep your thoughts organised.",
        new String[] {
          "Edit note",
          r.optBoolean("pinned") ? "Unpin note" : "Pin note",
          "Share note",
          "Move to another space",
          r.optBoolean("archived") ? "Unarchive note" : "Archive note",
          "View edit history",
          "Move to trash"
        },
        null,
        i -> {
          try {
            switch (i) {
              case 0:
                noteEditor(r);
                break;
              case 1:
                r.put("pinned", !r.optBoolean("pinned"));
                store.save();
                render();
                break;
              case 2:
                shareDetails(r);
                break;
              case 3:
                chooseDestination(
                    r.optString("parent"),
                    id -> {
                      try {
                        r.put("parent", id);
                        store.save();
                        render();
                      } catch (Exception e) {
                        error(e);
                      }
                    },
                    null);
                break;
              case 4:
                r.put("archived", !r.optBoolean("archived"));
                store.save();
                render();
                break;
              case 5:
                history(r);
                break;
              case 6:
                confirm(
                    "Move note to trash?",
                    "Restore it from Settings → Trash for 30 days.",
                    () -> {
                      try {
                        r.put("trash", true).put("deletedAt", System.currentTimeMillis());
                        store.save();
                        record = "";
                        render();
                      } catch (Exception e) {
                        error(e);
                      }
                    });
                break;
            }
          } catch (Exception e) {
            error(e);
          }
        });
  }

  private void fileOptions(JSONObject r, JSONObject f) {
    optionsSheet(
        f.optString("name"),
        "Your original file stays safely stored.",
        new String[] {
          "Open",
          "Share original file",
          "Save a local copy",
          "Rename attachment",
          "Recognise text offline",
          "Crop or rotate a copy",
          "Remove attachment",
          "Move earlier",
          "Move later"
        },
        null,
        w -> {
          if (w == 0) preview(f);
          if (w == 1) shareOne(f);
          if (w == 2) export(f);
          if (w == 3) {
            LinearLayout fo = form();
            EditText n = input("File name", f.optString("name"), false);
            add(fo, n);
            showDialog(
                "Rename attachment",
                fo,
                "Save",
                (x, y) -> {
                  try {
                    String name = n.getText().toString().trim();
                    if (!name.isEmpty()) {
                      f.put("name", name);
                      store.save();
                      render();
                    }
                  } catch (Exception ex) {
                    error(ex);
                  }
                });
          }
          if (w == 7 || w == 8) {
            try {
              reorder(r.getJSONArray("files"), f.optString("id"), w == 7 ? -1 : 1, null);
              store.save();
              render();
            } catch (Exception ex) {
              error(ex);
            }
          }
          if (w == 4) ocr(r, f);
          if (w == 5) crop(r, f);
          if (w == 6)
            confirm(
                "Remove attachment?",
                "This removes its encrypted copy from DocuBuds. Original files outside the"
                    + " app are unaffected.",
                () -> {
                  try {
                    JSONArray fs = r.getJSONArray("files");
                    for (int j = fs.length() - 1; j >= 0; j--)
                      if (fs.getJSONObject(j).optString("id").equals(f.optString("id")))
                        fs.remove(j);
                    store.save();
                    store.deleteBlob(f);
                    render();
                  } catch (Exception ex) {
                    error(ex);
                  }
                });
        });
  }

  private void confirm(String title, String message, Runnable yes) {
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Continue",
                (x, y) -> {
                  if (store.unlocked()) safe(yes);
                })
            .create();
    dialogs.add(d);
    watchDialog(d);
    d.show();
  }

  private void permanentDelete(JSONObject r) {
    try {
      JSONArray fs = r.optJSONArray("files");
      store.remove("records", r.getString("id"));
      store.save();
      if (fs != null) for (int i = 0; i < fs.length(); i++) store.deleteBlob(fs.getJSONObject(i));
      schedule();
      record = "";
      render();
    } catch (Exception e) {
      error(e);
    }
  }

  private void purgeExpiredTrash() throws Exception {
    JSONArray a = store.root().getJSONArray("records");
    boolean changed = false;
    ArrayList<JSONObject> files = new ArrayList<>();
    for (int i = a.length() - 1; i >= 0; i--) {
      JSONObject r = a.getJSONObject(i);
      if (r.optBoolean("trash")
          && System.currentTimeMillis() - r.optLong("deletedAt") > 30L * 86400000) {
        JSONArray fs = r.optJSONArray("files");
        if (fs != null) for (int j = 0; j < fs.length(); j++) files.add(fs.getJSONObject(j));
        a.remove(i);
        changed = true;
      }
    }
    if (changed) {
      store.save();
      for (JSONObject f : files) store.deleteBlob(f);
    }
  }

  private void history(JSONObject r) {
    JSONArray a = r.optJSONArray("history");
    if (a == null || a.length() == 0) {
      toast("No earlier edits saved.");
      return;
    }
    String[] names = new String[a.length()];
    for (int i = 0; i < a.length(); i++) {
      JSONObject v = a.optJSONObject(i);
      names[i] =
          v.optString("title")
              + " · "
              + DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
                  .format(
                      Instant.ofEpochMilli(v.optLong("updated")).atZone(ZoneId.systemDefault()));
    }
    final JSONArray versions = a;
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Earlier details · latest 10 edits")
            .setItems(
                names,
                (x, i) ->
                    confirm(
                        "Restore these details?",
                        "Attachments remain unchanged. The current details will be kept in"
                            + " history.",
                        () -> {
                          try {
                            JSONObject old = new JSONObject(versions.getJSONObject(i).toString());
                            rememberVersion(r);
                            if (!old.has("noteHtml")) r.remove("noteHtml");
                            if (!old.has("noteColor")) r.remove("noteColor");
                            for (String k :
                                new String[] {
                                  "title",
                                  "type",
                                  "fields",
                                  "notes",
                                  "noteHtml",
                                  "noteColor",
                                  "expiry",
                                  "tags",
                                  "parent"
                                }) if (old.has(k)) r.put(k, old.get(k));
                            r.put("updated", System.currentTimeMillis());
                            store.save();
                            schedule();
                            render();
                          } catch (Exception e) {
                            error(e);
                          }
                        }))
            .setNegativeButton("Close", null)
            .create();
    dialogs.add(d);
    watchDialog(d);
    d.show();
  }

  private String formatSize(long b) {
    return b >= 1048576
        ? String.format(Locale.ROOT, "%.1f MB", b / 1048576f)
        : b >= 1024 ? String.format(Locale.ROOT, "%.0f KB", b / 1024f) : b + " bytes";
  }

  private void importFiles(String target) {
    attachTarget = target;
    Intent in =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
    startActivityForResult(in, IMPORT);
  }

  private void capture(String target) {
    attachTarget = target;
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.CAMERA}, 52);
      return;
    }
    String parent = space;
    scanner =
        new NativeScanner(
            this,
            (bytes) -> {
              if (!store.unlocked()) {
                Arrays.fill(bytes, (byte) 0);
                return;
              }
              background(
                  "Encrypting captured page…",
                  () -> {
                    try {
                      JSONObject file =
                          store.attach(
                              "Scan "
                                  + java.time.LocalDateTime.now()
                                      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"))
                                  + ".jpg",
                              "image/jpeg",
                              bytes);
                      JSONObject r =
                          target.isEmpty()
                              ? newRecord(file.optString("name"), parent)
                              : store.find("records", target);
                      if (r == null) throw new IOException("Destination record is unavailable");
                      r.getJSONArray("files").put(file);
                      store.save();
                    } finally {
                      Arrays.fill(bytes, (byte) 0);
                    }
                  },
                  () -> {
                    render();
                    toast("Page encrypted and saved. Scan another page to continue.");
                  });
            },
            this::error,
            app::touch);
    watchDialog(scanner);
    scanner.show();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] granted) {
    super.onRequestPermissionsResult(code, permissions, granted);
    if (code == 52) {
      if (granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED && store.unlocked())
        capture(attachTarget);
      else toast("Camera access is optional. You can import local files.");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK) {
      if (request == EXPORT) {
        if (exportBytes != null) Arrays.fill(exportBytes, (byte) 0);
        exportBytes = null;
      }
      if (request == CAPTURE && !captureName.isEmpty())
        new File(getCacheDir(), captureName).delete();
      return;
    }
    if (request == IMPORT) {
      ArrayList<Uri> uris = new ArrayList<>();
      if (data != null && data.getClipData() != null) {
        for (int i = 0; i < data.getClipData().getItemCount(); i++)
          uris.add(data.getClipData().getItemAt(i).getUri());
      } else if (data != null && data.getData() != null) uris.add(data.getData());
      String dest = attachTarget;
      String parent = space;
      defer(() -> importUris(uris, dest, parent));
    }

    if (request == EXPORT && data != null && data.getData() != null) {
      Uri uri = data.getData();
      byte[] b = exportBytes;
      exportBytes = null;
      if (b != null) defer(() -> writeExport(uri, b));
    }
  }

  private void defer(Runnable r) {
    if (store.unlocked() && SystemClock.elapsedRealtime() - app.lastInteraction < 60000) {
      app.touch();
      r.run();
    } else {
      afterUnlock = r;
      lock(false);
      if (store.customType().isEmpty()) authenticate();
      else customUnlock();
    }
  }

  private interface Job {
    void run() throws Exception;
  }

  private void background(String message, Job work, Runnable complete) {
    if (working) {
      toast("Please wait for the current operation.");
      return;
    }
    working = true;
    ProgressDialog p = new ProgressDialog(this);
    p.setMessage(message);
    p.setCancelable(false);
    dialogs.add(p);
    watchDialog(p);
    p.show();
    long token = viewToken;
    app.io.execute(
        () -> {
          Exception failed = null;
          try {
            work.run();
          } catch (Exception e) {
            failed = e;
          }
          Exception finalFailed = failed;
          app.main.post(
              () -> {
                working = false;
                if (p.isShowing()) p.dismiss();
                if (!store.unlocked()) return;
                app.touch();
                if (finalFailed != null) error(finalFailed);
                else complete.run();
              });
        });
  }

  private void importUris(ArrayList<Uri> uris, String target, String parent) {
    if (uris.size() > 30) {
      toast("Select up to 30 files per import.");
      return;
    }
    ArrayList<String> fail = new ArrayList<>();
    int[] added = {0};
    background(
        "Encrypting local files…",
        () -> {
          for (Uri uri : uris) {
            try {
              if (!store.unlocked()) break;
              String name = "Document";
              try (android.database.Cursor c =
                  getContentResolver()
                      .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) name = c.getString(0);
              }
              byte[] bytes;
              try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot read file");
                bytes = VaultStore.read(in, VaultStore.MAX_FILE);
              }
              try {
                JSONObject attachment =
                    store.attach(name, getContentResolver().getType(uri), bytes);
                JSONObject r =
                    target.isEmpty() ? newRecord(name, parent) : store.find("records", target);
                if (r == null) throw new IOException("Destination record no longer exists");
                r.getJSONArray("files").put(attachment);
                r.put("updated", System.currentTimeMillis());
                store.save();
                added[0]++;
              } finally {
                Arrays.fill(bytes, (byte) 0);
              }
            } catch (Exception e) {
              fail.add(
                  "File "
                      + (added[0] + fail.size() + 1)
                      + ": "
                      + (e.getMessage() == null ? "Import failed" : e.getMessage()));
            }
          }
        },
        () -> {
          render();
          toast(added[0] + " file(s) encrypted and saved");
          if (!fail.isEmpty()) info("Some files were not imported", String.join("\n", fail));
        });
  }

  private JSONObject newRecord(String name, String parent) throws Exception {
    JSONObject r =
        new JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("title", name)
            .put("type", "Document")
            .put("parent", parent)
            .put("notes", "")
            .put("tags", "")
            .put("fields", new JSONObject())
            .put("created", System.currentTimeMillis())
            .put("updated", System.currentTimeMillis())
            .put("files", new JSONArray());
    store.root().getJSONArray("records").put(r);
    return r;
  }

  private void export(JSONObject f) {
    background(
        "Preparing original file…",
        () -> {
          exportBytes = store.content(f);
          exportMime = f.optString("mime");
          exportName = f.optString("name");
        },
        () -> {
          Intent in =
              new Intent(Intent.ACTION_CREATE_DOCUMENT)
                  .addCategory(Intent.CATEGORY_OPENABLE)
                  .setType(exportMime)
                  .putExtra(Intent.EXTRA_TITLE, exportName)
                  .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
          startActivityForResult(in, EXPORT);
        });
  }

  private void writeExport(Uri uri, byte[] b) {
    background(
        "Saving your local copy…",
        () -> {
          try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Destination unavailable");
            out.write(b);
          } finally {
            Arrays.fill(b, (byte) 0);
          }
        },
        () -> toast("Original file saved. This copy is outside the encrypted vault."));
  }

  private Uri shared(byte[] bytes, String name, String mime) {
    VaultApp.pruneShares();
    if (VaultApp.shares.size() >= 4) {
      toast("Earlier share links were cleared to free memory.");
      for (VaultApp.Shared s : VaultApp.shares.values()) Arrays.fill(s.bytes, (byte) 0);
      VaultApp.shares.clear();
    }
    String token = UUID.randomUUID().toString();
    VaultApp.shares.put(token, new VaultApp.Shared(bytes, name, mime));
    app.main.postDelayed(VaultApp::pruneShares, 601000);
    return Uri.parse("content://com.docubuds.app.files/shared/" + token);
  }

  private void shareOne(JSONObject f) {
    final byte[][] b = {null};
    background(
        "Preparing original file…",
        () -> b[0] = store.content(f),
        () ->
            launchShare(
                new ArrayList<>(List.of(shared(b[0], f.optString("name"), f.optString("mime")))),
                f.optString("mime")));
  }

  private void selectShare(JSONObject r) {
    JSONArray fs = r.optJSONArray("files");
    if (fs == null || fs.length() == 0) {
      toast("Add an attachment first.");
      return;
    }
    String[] names = new String[fs.length()];
    boolean[] checked = new boolean[fs.length()];
    for (int i = 0; i < fs.length(); i++) names[i] = fs.optJSONObject(i).optString("name");
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Share original files")
            .setMultiChoiceItems(names, checked, (x, w, on) -> checked[w] = on)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Share",
                (x, w) -> {
                  ArrayList<JSONObject> selected = new ArrayList<>();
                  for (int i = 0; i < checked.length; i++)
                    if (checked[i]) selected.add(fs.optJSONObject(i));
                  if (selected.isEmpty()) return;
                  if (selected.size() > 4) {
                    toast("Share up to four files at a time.");
                    return;
                  }
                  ArrayList<byte[]> bytes = new ArrayList<>();
                  background(
                      "Preparing selected originals…",
                      () -> {
                        long sum = 0;
                        for (JSONObject f : selected) {
                          sum += f.optLong("size");
                          if (sum > 48L * 1024 * 1024)
                            throw new IOException("Share up to 48 MB at once");
                          bytes.add(store.content(f));
                        }
                      },
                      () -> {
                        ArrayList<Uri> uris = new ArrayList<>();
                        for (int i = 0; i < selected.size(); i++)
                          uris.add(
                              shared(
                                  bytes.get(i),
                                  selected.get(i).optString("name"),
                                  selected.get(i).optString("mime")));
                        launchShare(uris, "*/*");
                      });
                })
            .create();
    dialogs.add(d);
    watchDialog(d);
    d.show();
  }

  private void launchShare(ArrayList<Uri> uris, String mime) {
    Intent in =
        new Intent(uris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND)
            .setType(mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (uris.size() > 1) in.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    else in.putExtra(Intent.EXTRA_STREAM, uris.get(0));
    ClipData clip = ClipData.newRawUri("Selected DocuBuds files", uris.get(0));
    for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
    in.setClipData(clip);
    try {
      startActivity(Intent.createChooser(in, "Share original file · receiving apps may compress"));
    } catch (Exception e) {
      error(e);
    }
  }

  private void shareDetails(JSONObject r) {
    ArrayList<String> keys = new ArrayList<>(), values = new ArrayList<>();
    try {
      JSONObject fields = r.optJSONObject("fields");
      if (fields != null)
        for (Iterator<String> it = fields.keys(); it.hasNext(); ) {
          String k = it.next();
          keys.add(k);
          values.add(fields.optString(k));
        }
      if (!r.optString("notes").isEmpty()) {
        keys.add("Notes");
        values.add(r.optString("notes"));
      }
      if (keys.isEmpty()) {
        keys.add("Title");
        values.add(r.optString("title"));
      }
      boolean[] selected = new boolean[keys.size()];
      AlertDialog d =
          new AlertDialog.Builder(this)
              .setTitle("Choose details to include")
              .setMultiChoiceItems(
                  keys.toArray(new String[0]), selected, (x, i, b) -> selected[i] = b)
              .setNegativeButton("Cancel", null)
              .setPositiveButton(
                  "Preview",
                  (x, y) -> {
                    StringBuilder b = new StringBuilder(r.optString("title") + "\n\n");
                    int n = 0;
                    for (int i = 0; i < selected.length; i++)
                      if (selected[i]) {
                        n++;
                        b.append(keys.get(i)).append(": ").append(values.get(i)).append("\n");
                      }
                    if (n == 0) {
                      toast("Select at least one detail");
                      return;
                    }
                    String summary = b.toString();
                    AlertDialog preview =
                        new AlertDialog.Builder(this)
                            .setTitle("Selected details preview")
                            .setMessage(summary)
                            .setNegativeButton("Cancel", null)
                            .setNeutralButton(
                                "Save local text",
                                (a, c) -> {
                                  exportBytes = summary.getBytes(StandardCharsets.UTF_8);
                                  Intent in =
                                      new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                          .setType("text/plain")
                                          .addCategory(Intent.CATEGORY_OPENABLE)
                                          .putExtra(Intent.EXTRA_TITLE, "DocuBuds details.txt")
                                          .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                                  startActivityForResult(in, EXPORT);
                                })
                            .setPositiveButton(
                                "Share",
                                (a, c) -> {
                                  Uri u =
                                      shared(
                                          summary.getBytes(StandardCharsets.UTF_8),
                                          "DocuBuds details.txt",
                                          "text/plain");
                                  launchShare(new ArrayList<>(List.of(u)), "text/plain");
                                })
                            .create();
                    dialogs.add(preview);
                    watchDialog(preview);
                    preview.show();
                  })
              .create();
      dialogs.add(d);
      watchDialog(d);
      d.show();
    } catch (Exception e) {
      error(e);
    }
  }

  private void preview(JSONObject f) {
    final byte[][] b = {null};
    background(
        "Opening encrypted file…",
        () -> b[0] = store.content(f),
        () -> {
          String mime = f.optString("mime"), name = f.optString("name");
          if (mime.startsWith("image/")) {
            Bitmap bm = decode(b[0], 2400);
            Arrays.fill(b[0], (byte) 0);
            if (bm == null) {
              toast("This image format cannot be previewed. You can share or export the original.");
              return;
            }
            Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
            LinearLayout container = col();
            container.setBackgroundColor(card);
            container.setPadding(dp(16), dp(38), dp(16), dp(22));
            add(container, button("Close preview", d::dismiss, false));
            ZoomImage z = new ZoomImage(this, bm);
            container.addView(z, new LinearLayout.LayoutParams(-1, 0, 1));
            add(container, button("Share original", () -> shareOne(f), true));
            d.setContentView(container);
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            d.setOnDismissListener(x -> bm.recycle());
            dialogs.add(d);
            watchDialog(d);
            d.show();
            return;
          }
          if (mime.equals("application/pdf") || name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            pdfPreview(f, b[0]);
            return;
          }
          if (mime.startsWith("text/") || name.endsWith(".txt")) {
            String t = new String(b[0], StandardCharsets.UTF_8);
            Arrays.fill(b[0], (byte) 0);
            if (t.length() > 250000)
              t = t.substring(0, 250000) + "\n[Preview limited to 250,000 characters]";
            ScrollView s = new ScrollView(this);
            TextView tx = text(t, 16, ink, false);
            tx.setPadding(dp(22), dp(18), dp(22), dp(18));
            tx.setTextIsSelectable(true);
            s.addView(tx);
            AlertDialog d =
                new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setView(s)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Share original", (x, w) -> shareOne(f))
                    .create();
            dialogs.add(d);
            watchDialog(d);
            d.show();
            return;
          }
          Arrays.fill(b[0], (byte) 0);
          AlertDialog d =
              new AlertDialog.Builder(this)
                  .setTitle("Original file safely stored")
                  .setMessage(
                      "This file type has no internal preview. Export or share the original to open"
                          + " it in an app you choose.")
                  .setPositiveButton("Share", (x, w) -> shareOne(f))
                  .setNeutralButton("Save locally", (x, w) -> export(f))
                  .setNegativeButton("Close", null)
                  .create();
          dialogs.add(d);
          watchDialog(d);
          d.show();
        });
  }

  private Bitmap decode(byte[] b, int max) {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(b, 0, b.length, o);
    int sample = 1;
    while (o.outWidth / sample > max || o.outHeight / sample > max) sample *= 2;
    o.inJustDecodeBounds = false;
    o.inSampleSize = sample;
    return BitmapFactory.decodeByteArray(b, 0, b.length, o);
  }

  private ParcelFileDescriptor memoryFd(byte[] data, Handler handler) throws IOException {
    return getSystemService(StorageManager.class)
        .openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            new ProxyFileDescriptorCallback() {
              public long onGetSize() {
                return data.length;
              }

              public int onRead(long offset, int size, byte[] target) {
                if (offset >= data.length) return 0;
                int n = Math.min(size, data.length - (int) offset);
                System.arraycopy(data, (int) offset, target, 0, n);
                return n;
              }

              public void onRelease() {}
            },
            handler);
  }

  private void pdfPreview(JSONObject f, byte[] data) {
    HandlerThread thread = new HandlerThread("DocuBuds PDF memory");
    thread.start();
    try {
      ParcelFileDescriptor fd = memoryFd(data, new Handler(thread.getLooper()));
      android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(fd);
      int[] page = {0};
      Bitmap[] shown = {null};
      Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
      LinearLayout panel = col();
      panel.setBackgroundColor(card);
      panel.setPadding(dp(16), dp(38), dp(16), dp(20));
      TextView heading = text(f.optString("name"), 18, ink, true);
      add(panel, heading);
      gap(panel, 10);
      LinearLayout ctr = row();
      TextView prev = button("Previous", () -> {}, false), next = button("Next", () -> {}, false);
      ctr.addView(prev, new LinearLayout.LayoutParams(0, -2, 1));
      ctr.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
      add(panel, ctr);
      FrameLayout pageView = new FrameLayout(this);
      panel.addView(pageView, new LinearLayout.LayoutParams(-1, 0, 1));
      Runnable renderPage =
          () -> {
            try {
              android.graphics.pdf.PdfRenderer.Page pg = renderer.openPage(page[0]);
              float ratio = Math.min(2f, 1600f / pg.getWidth());
              Bitmap bm =
                  Bitmap.createBitmap(
                      (int) (pg.getWidth() * ratio),
                      (int) (pg.getHeight() * ratio),
                      Bitmap.Config.ARGB_8888);
              bm.eraseColor(Color.WHITE);
              pg.render(
                  bm, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
              pg.close();
              pageView.removeAllViews();
              pageView.addView(new ZoomImage(this, bm), new FrameLayout.LayoutParams(-1, -1));
              if (shown[0] != null) shown[0].recycle();
              shown[0] = bm;
              heading.setText(
                  f.optString("name")
                      + "\nPage "
                      + (page[0] + 1)
                      + " of "
                      + renderer.getPageCount());
              prev.setEnabled(page[0] > 0);
              next.setEnabled(page[0] < renderer.getPageCount() - 1);
            } catch (Exception e) {
              error(e);
            }
          };
      prev.setOnClickListener(
          v -> {
            app.touch();
            if (page[0] > 0) {
              page[0]--;
              renderPage.run();
            }
          });
      next.setOnClickListener(
          v -> {
            app.touch();
            if (page[0] < renderer.getPageCount() - 1) {
              page[0]++;
              renderPage.run();
            }
          });
      add(panel, button("Close preview", d::dismiss, false));
      d.setContentView(panel);
      d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
      d.setOnDismissListener(
          x -> {
            renderer.close();
            try {
              fd.close();
            } catch (Exception ignored) {
            }
            thread.quitSafely();
            Arrays.fill(data, (byte) 0);
            if (shown[0] != null) shown[0].recycle();
          });
      dialogs.add(d);
      watchDialog(d);
      d.show();
      renderPage.run();
    } catch (Exception e) {
      thread.quitSafely();
      Arrays.fill(data, (byte) 0);
      error(
          new IOException(
              "This PDF could not be previewed. It may be password protected, damaged or"
                  + " unsupported. The original can still be exported."));
    }
  }

  private final class ZoomImage extends View {
    private final Bitmap bitmap;
    private final Paint p = new Paint(3);
    private float scale = 1, dx = 0, dy = 0, lx, ly;
    private final ScaleGestureDetector detector;

    ZoomImage(Context c, Bitmap b) {
      super(c);
      bitmap = b;
      setContentDescription("Document preview. Pinch to zoom and drag to pan.");
      detector =
          new ScaleGestureDetector(
              c,
              new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                  scale = Math.max(1, Math.min(6, scale * d.getScaleFactor()));
                  invalidate();
                  return true;
                }
              });
    }

    protected void onDraw(Canvas c) {
      if (bitmap.isRecycled()) return;
      float base =
          Math.min(
              getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
      float w = bitmap.getWidth() * base * scale, h = bitmap.getHeight() * base * scale;
      c.drawBitmap(
          bitmap,
          null,
          new RectF(
              (getWidth() - w) / 2 + dx,
              (getHeight() - h) / 2 + dy,
              (getWidth() + w) / 2 + dx,
              (getHeight() + h) / 2 + dy),
          p);
    }

    public boolean onTouchEvent(android.view.MotionEvent e) {
      app.touch();
      detector.onTouchEvent(e);
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        lx = e.getX();
        ly = e.getY();
      } else if (e.getAction() == MotionEvent.ACTION_MOVE && e.getPointerCount() == 1) {
        dx += e.getX() - lx;
        dy += e.getY() - ly;
        lx = e.getX();
        ly = e.getY();
        invalidate();
      }
      return true;
    }
  }

  private void makePdf(JSONObject r) {
    JSONArray fs = r.optJSONArray("files");
    ArrayList<JSONObject> images = new ArrayList<>();
    if (fs != null)
      for (int i = 0; i < fs.length(); i++) {
        JSONObject f = fs.optJSONObject(i);
        if (f.optString("mime").startsWith("image/")) images.add(f);
      }
    if (images.isEmpty()) {
      toast("Add image attachments first. Each image becomes one PDF page.");
      return;
    }
    confirm(
        "Create a PDF?",
        "Image attachments will become pages in their current order. The originals are kept"
            + " unchanged. Large images are scaled to fit PDF pages.",
        () ->
            background(
                "Creating your PDF locally…",
                () -> {
                  android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
                  try {
                    int i = 1;
                    for (JSONObject f : images) {
                      byte[] bytes = store.content(f);
                      Bitmap bm = decode(bytes, 3500);
                      Arrays.fill(bytes, (byte) 0);
                      if (bm == null) throw new IOException("An image could not be decoded");
                      int pw = bm.getWidth(), ph = bm.getHeight();
                      android.graphics.pdf.PdfDocument.Page page =
                          pdf.startPage(
                              new android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, i++)
                                  .create());
                      page.getCanvas().drawBitmap(bm, 0, 0, null);
                      pdf.finishPage(page);
                      bm.recycle();
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    pdf.writeTo(out);
                    byte[] bytes = out.toByteArray();
                    JSONObject f =
                        store.attach(r.optString("title") + ".pdf", "application/pdf", bytes);
                    Arrays.fill(bytes, (byte) 0);
                    r.getJSONArray("files").put(f);
                    r.put("updated", System.currentTimeMillis());
                    store.save();
                  } finally {
                    pdf.close();
                  }
                },
                () -> {
                  render();
                  toast("PDF saved. Original images unchanged.");
                }));
  }

  private void crop(JSONObject r, JSONObject f) {
    if (!f.optString("mime").startsWith("image/")) {
      toast("Crop and rotate work with image attachments.");
      return;
    }
    final byte[][] raw = {null};
    background(
        "Opening image editor…",
        () -> raw[0] = store.content(f),
        () -> {
          Bitmap b = decode(raw[0], 3500);
          Arrays.fill(raw[0], (byte) 0);
          if (b == null) {
            toast("Unsupported image");
            return;
          }
          Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
          LinearLayout ui = col();
          ui.setBackgroundColor(card);
          ui.setPadding(dp(18), dp(40), dp(18), dp(20));
          add(ui, text("Crop a copy", 24, ink, true));
          paragraphOn(
              ui,
              "Drag the selection’s corners. Your original remains unchanged. The edited copy is"
                  + " saved as PNG.");
          CropView editor = new CropView(b);
          ui.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
          add(ui, button("Rotate 90°", editor::rotate, false));
          gap(ui, 10);
          add(
              ui,
              button(
                  "Save edited copy",
                  () -> {
                    Bitmap out = editor.result();
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    out.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    out.recycle();
                    byte[] bytes = stream.toByteArray();
                    d.dismiss();
                    background(
                        "Encrypting edited copy…",
                        () -> {
                          JSONObject file =
                              store.attach(
                                  "Edited " + f.optString("name") + ".png", "image/png", bytes);
                          Arrays.fill(bytes, (byte) 0);
                          r.getJSONArray("files").put(file);
                          store.save();
                        },
                        () -> {
                          render();
                          toast("Edited copy saved. Original unchanged.");
                        });
                  },
                  true));
          gap(ui, 10);
          add(ui, button("Cancel", d::dismiss, false));
          d.setContentView(ui);
          d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
          d.setOnDismissListener(x -> editor.dispose());
          dialogs.add(d);
          watchDialog(d);
          d.show();
        });
  }

  private final class CropView extends View {
    Bitmap b;
    RectF bounds = new RectF(), sel = new RectF();
    Paint paint = new Paint(3);
    boolean initialized = false;
    int corner = -1;

    CropView(Bitmap bitmap) {
      super(MainActivity.this);
      b = bitmap;
    }

    void rotate() {
      Matrix m = new Matrix();
      m.postRotate(90);
      Bitmap old = b;
      b = Bitmap.createBitmap(old, 0, 0, old.getWidth(), old.getHeight(), m, true);
      if (old != b) old.recycle();
      initialized = false;
      invalidate();
    }

    void dispose() {
      if (b != null && !b.isRecycled()) b.recycle();
    }

    protected void onDraw(Canvas c) {
      if (b == null || b.isRecycled()) return;
      float factor =
          Math.min(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight());
      float w = b.getWidth() * factor, h = b.getHeight() * factor;
      bounds.set(
          (getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
      c.drawBitmap(b, null, bounds, paint);
      if (!initialized) {
        sel.set(bounds);
        sel.inset(w * .04f, h * .04f);
        initialized = true;
      }
      paint.setColor(accent);
      paint.setStrokeWidth(dp(3));
      paint.setStyle(Paint.Style.STROKE);
      c.drawRect(sel, paint);
      paint.setStyle(Paint.Style.FILL);
      for (float x : new float[] {sel.left, sel.right})
        for (float y : new float[] {sel.top, sel.bottom}) c.drawCircle(x, y, dp(9), paint);
    }

    public boolean onTouchEvent(MotionEvent e) {
      app.touch();
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        float[] ds = {
          (e.getX() - sel.left) * (e.getX() - sel.left)
              + (e.getY() - sel.top) * (e.getY() - sel.top),
          (e.getX() - sel.right) * (e.getX() - sel.right)
              + (e.getY() - sel.top) * (e.getY() - sel.top),
          (e.getX() - sel.left) * (e.getX() - sel.left)
              + (e.getY() - sel.bottom) * (e.getY() - sel.bottom),
          (e.getX() - sel.right) * (e.getX() - sel.right)
              + (e.getY() - sel.bottom) * (e.getY() - sel.bottom)
        };
        corner = 0;
        for (int i = 1; i < 4; i++) if (ds[i] < ds[corner]) corner = i;
      }
      if (e.getAction() == MotionEvent.ACTION_MOVE) {
        float x = Math.max(bounds.left, Math.min(bounds.right, e.getX())),
            y = Math.max(bounds.top, Math.min(bounds.bottom, e.getY()));
        if (corner == 0) {
          sel.left = Math.min(x, sel.right - dp(30));
          sel.top = Math.min(y, sel.bottom - dp(30));
        }
        if (corner == 1) {
          sel.right = Math.max(x, sel.left + dp(30));
          sel.top = Math.min(y, sel.bottom - dp(30));
        }
        if (corner == 2) {
          sel.left = Math.min(x, sel.right - dp(30));
          sel.bottom = Math.max(y, sel.top + dp(30));
        }
        if (corner == 3) {
          sel.right = Math.max(x, sel.left + dp(30));
          sel.bottom = Math.max(y, sel.top + dp(30));
        }
        invalidate();
      }
      return true;
    }

    Bitmap result() {
      int x = Math.max(0, (int) ((sel.left - bounds.left) / bounds.width() * b.getWidth())),
          y = Math.max(0, (int) ((sel.top - bounds.top) / bounds.height() * b.getHeight()));
      int
          w =
              Math.max(
                  1,
                  Math.min(b.getWidth() - x, (int) (sel.width() / bounds.width() * b.getWidth()))),
          h =
              Math.max(
                  1,
                  Math.min(
                      b.getHeight() - y, (int) (sel.height() / bounds.height() * b.getHeight())));
      return Bitmap.createBitmap(b, x, y, w, h).copy(Bitmap.Config.ARGB_8888, false);
    }
  }

  private void ocr(JSONObject r, JSONObject f) {
    if (!f.optString("mime").startsWith("image/")) {
      toast(
          "Offline text recognition works on image attachments. Scan or import a page image"
              + " first.");
      return;
    }
    AlertDialog choose =
        new AlertDialog.Builder(this)
            .setTitle("Recognise text offline")
            .setItems(
                new String[] {"English / Latin script", "Hindi / Devanagari"},
                (x, w) -> {
                  final byte[][] raw = {null};
                  background(
                      "Opening image for offline recognition…",
                      () -> raw[0] = store.content(f),
                      () -> {
                        Bitmap bitmap = decode(raw[0], 3000);
                        Arrays.fill(raw[0], (byte) 0);
                        if (bitmap == null) {
                          toast("Image could not be decoded");
                          return;
                        }
                        TextRecognizer recognizer =
                            w == 0
                                ? TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                : TextRecognition.getClient(
                                    new DevanagariTextRecognizerOptions.Builder().build());
                        ProgressDialog pd = new ProgressDialog(this);
                        pd.setMessage("Recognising on this phone…");
                        pd.setCancelable(false);
                        dialogs.add(pd);
                        watchDialog(pd);
                        pd.show();
                        recognizer
                            .process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener(
                                result -> {
                                  pd.dismiss();
                                  recognizer.close();
                                  bitmap.recycle();
                                  if (!store.unlocked()) return;
                                  LinearLayout ff = form();
                                  EditText output =
                                      input("Review recognised text", result.getText(), true);
                                  output.setMinLines(8);
                                  add(ff, output);
                                  paragraphOn(
                                      ff,
                                      "Review important identifiers and dates against the original."
                                          + " Recognition can make mistakes.");
                                  showDialog(
                                      "Review recognised text",
                                      formScroll(ff),
                                      "Save searchable text",
                                      (a, b) -> {
                                        try {
                                          r.put("ocr", output.getText().toString());
                                          store.save();
                                          toast("Text encrypted and saved for search");
                                        } catch (Exception e) {
                                          error(e);
                                        }
                                      });
                                })
                            .addOnFailureListener(
                                e -> {
                                  pd.dismiss();
                                  recognizer.close();
                                  bitmap.recycle();
                                  if (store.unlocked()) error(e);
                                });
                      });
                })
            .setNegativeButton("Cancel", null)
            .create();
    dialogs.add(choose);
    watchDialog(choose);
    choose.show();
  }

  private void reminders() throws Exception {
    layout("DocuBuds", false, true);
    add(body, text("Reminders", 28, ink, true));
    gap(body, 10);
    paragraph(
        "Private alerts for the dates you save. Alerts are scheduled for 7 days before and on the"
            + " date, around 9 AM.");

    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      gap(body, 14);
      add(
          body,
          button(
              "Enable reminder notifications",
              () -> requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 51),
              false));
    }
    heading("Upcoming & overdue");
    ArrayList<JSONObject> list = new ArrayList<>();
    JSONArray a = store.root().getJSONArray("records");
    for (int i = 0; i < a.length(); i++) {
      JSONObject r = a.getJSONObject(i);
      if (!r.optBoolean("trash") && !r.optBoolean("archived") && !r.optString("expiry").isEmpty())
        list.add(r);
    }
    list.sort(Comparator.comparing(r -> r.optString("expiry")));
    if (list.isEmpty())
      paragraph("No dates yet. Set an expiry date on a document or add a reminder.");
    for (JSONObject r : list) {
      String status = "";
      try {
        long days =
            java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.now(), LocalDate.parse(r.optString("expiry")));
        status =
            days < 0
                ? "Overdue by " + (-days) + " days"
                : days == 0 ? "Today" : "In " + days + " days";
      } catch (Exception ignored) {
      }
      add(
          body,
          item(
              r.optString("title"),
              r.optString("expiry") + " · " + status,
              "bell",
              accent,
              () -> {
                record = r.optString("id");
                render();
              }));
    }
    paragraph(
        "Notification content never includes your document names or private details. Android"
            + " battery and notification settings can delay alerts.");
    support(false);
  }

  private void settings() throws Exception {
    layout("DocuBuds", false, true);
    add(body, text("Settings", 28, ink, true));
    heading("Your vault");
    add(
        body,
        item(
            "Vault name",
            store.root().optString("name"),
            "folder",
            accent,
            () -> {
              LinearLayout f = form();
              EditText e = input("Your name or vault name", store.root().optString("name"), false);
              add(f, e);
              showDialog(
                  "Name your vault",
                  f,
                  "Save",
                  (x, y) -> {
                    try {
                      String n = e.getText().toString().trim();
                      if (!n.isEmpty()) {
                        store.root().put("name", n);
                        store.save();
                        render();
                      }
                    } catch (Exception ex) {
                      error(ex);
                    }
                  });
            }));
    add(
        body,
        item(
            "Unlock method",
            store.customType().isEmpty()
                ? "Phone unlock · add an app PIN or pattern"
                : "App " + store.customType() + " · phone unlock also available",
            "lock",
            accent,
            this::unlockSettings));
    add(
        body,
        item(
            "Auto-lock",
            "After 1 minute of inactivity, including time outside the app",
            "lock",
            accent,
            () ->
                info(
                    "Automatic lock",
                    "Your vault locks after one minute without interaction. Tap the lock button to"
                        + " lock immediately. Opening a share or payment app does not pause the"
                        + " inactivity timer.")));
    add(
        body,
        item(
            "Appearance",
            store.root().optBoolean("dark") ? "Dark" : "Light",
            "settings",
            accent,
            () -> {
              try {
                store.root().put("dark", !store.root().optBoolean("dark"));
                store.save();
                render();
              } catch (Exception e) {
                error(e);
              }
            }));
    add(
        body,
        item(
            "Recent records",
            store.root().optBoolean("recent", true) ? "Shown on vault home" : "Hidden",
            "doc",
            accent,
            () -> {
              try {
                store.root().put("recent", !store.root().optBoolean("recent", true));
                store.save();
                render();
              } catch (Exception e) {
                error(e);
              }
            }));
    add(
        body,
        item(
            "Screenshots",
            "Allowed throughout DocuBuds",
            "doc",
            accent,
            () ->
                info(
                    "Screenshots allowed",
                    "You can capture app screens, notes and document previews. Android controls"
                        + " screenshots of its own phone-authentication prompt.")));
    heading("Tools & organisation");
    add(
        body,
        item(
            "Password generator",
            "Generate on this phone",
            "lock",
            accent,
            this::passwordGenerator));
    add(
        body,
        item(
            "Password review", "Check saved logins locally", "lock", accent, this::passwordReview));
    add(
        body,
        item(
            "Duplicate attachments",
            "Compare original file fingerprints locally",
            "doc",
            accent,
            this::duplicates));
    add(
        body,
        item(
            "Archived records",
            "Hidden from normal browsing",
            "folder",
            accent,
            () -> collection("Archived records", "archived")));
    add(
        body,
        item(
            "Trash",
            "Restore for 30 days or permanently delete",
            "doc",
            accent,
            () -> collection("Trash", "trash")));
    long size = 0;
    File[] files = new File(getNoBackupFilesDir(), "vault").listFiles();
    if (files != null) for (File f : files) size += f.length();
    add(
        body,
        item(
            "Storage",
            formatSize(size) + " encrypted on this phone",
            "folder",
            accent,
            () ->
                info(
                    "Local storage",
                    "Files are preserved at original quality. Each attachment can be up to 32 MB."
                        + " Files stay inside DocuBuds until you deliberately export or share them."
                        + " No backups or recovery keys are created.")));
    heading("Help & app information");
    add(
        body,
        item(
            "Using DocuBuds",
            "Storage, sharing, reminders and recovery limits",
            "doc",
            accent,
            this::help));
    add(
        body,
        item(
            "About DocuBuds",
            "Version 1.2.3 · Free and ad-free",
            "id",
            accent,
            () ->
                info(
                    "DocuBuds 1.2.3",
                    "Your personal document vault. Completely free. No ads, no accounts, no"
                        + " subscriptions, no cloud backups.\n\n"
                        + "Made with love ♥ by Vijay Mamta Sahani (@momentswithvms)\n\n"
                        + "Sharing, UPI payments and Instagram open other apps only when you choose"
                        + " them. Those apps may require internet and control the copies they"
                        + " receive.")));
    add(
        body,
        button(
            "Delete this vault",
            () ->
                confirm(
                    "Delete everything in DocuBuds?",
                    "There is no backup or recovery. Every record and attachment in this vault will"
                        + " be removed permanently. Exported copies elsewhere are not deleted.",
                    () ->
                        confirm(
                            "Final confirmation",
                            "Permanently delete this vault and its encryption key?",
                            () -> {
                              try {
                                ReminderReceiver.schedule(this, new JSONArray());
                                store.destroy();
                                lockPage();
                              } catch (Exception e) {
                                error(e);
                              }
                            })),
            false));
    support(true);
  }

  private void collection(String title, String flag) {
    try {
      layout(title, true, true);
      add(body, text(title, 27, ink, true));
      heading("Records");
      JSONArray a = store.root().getJSONArray("records");
      int n = 0;
      for (int i = 0; i < a.length(); i++) {
        JSONObject r = a.getJSONObject(i);
        if (r.optBoolean(flag) && (flag.equals("trash") || !r.optBoolean("trash"))) {
          n++;
          add(
              body,
              item(
                  r.optString("title"),
                  r.optString("type"),
                  "doc",
                  accent,
                  () -> {
                    record = r.optString("id");
                    render();
                  }));
        }
      }
      if (n == 0) paragraph("Nothing here.");
      support(false);
    } catch (Exception e) {
      error(e);
    }
  }

  private void help() {
    info(
        "How DocuBuds works",
        "• Use your phone’s authentication to unlock. The vault locks after 1 minute of"
            + " inactivity.\n\n"
            + "• Create named spaces for properties, vehicles and employers. Long-press a space to"
            + " rename or move it.\n\n"
            + "• Save structured details or import local files up to 32 MB each. Long-press an"
            + " attachment for share, export, rename, crop or offline OCR. English and Hindi"
            + " recognition models are bundled.\n\n"
            + "• Save dates for generic private reminders. Notification timing depends on"
            + " Android.\n\n"
            + "• Sharing preserves the original bytes. Receiving apps may compress or retain files."
            + " Save/export creates an unencrypted copy outside the vault.\n\n"
            + "• There are no backups or recovery keys. Losing the phone, clearing app data,"
            + " uninstalling, or losing the device encryption key can permanently lose the"
            + " vault.\n\n"
            + "• Removing or resetting the phone’s secure lock can invalidate the key. The app"
            + " cannot recover it.\n\n"
            + "• This vault has no internet permission. Optional share, donation and Instagram"
            + " actions open other apps deliberately.");
  }

  private void info(String title, String message) {
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .create();
    dialogs.add(d);
    watchDialog(d);
    d.show();
  }

  private void passwordGenerator() {
    LinearLayout f = form();
    TextView len = text("Length: 20", 16, ink, true);
    add(f, len);
    SeekBar size = new SeekBar(this);
    size.setMax(52);
    size.setProgress(8);
    add(f, size);
    CheckBox symbols = new CheckBox(this);
    symbols.setText("Include symbols");
    symbols.setChecked(true);
    symbols.setTextColor(ink);
    add(f, symbols);
    EditText output = input("Generated password", "", false);
    add(f, output);
    Runnable generate =
        () -> {
          int n = size.getProgress() + 12;
          len.setText("Length: " + n);
          String alphabet =
              "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
                  + (symbols.isChecked() ? "!@#$%&*+-_=?:" : "");
          SecureRandom rand = new SecureRandom();
          StringBuilder p = new StringBuilder();
          for (int i = 0; i < n; i++) p.append(alphabet.charAt(rand.nextInt(alphabet.length())));
          output.setText(p.toString());
        };
    size.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int v, boolean u) {
            generate.run();
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    generate.run();
    gap(f, 12);
    add(f, button("Generate again", generate, false));
    gap(f, 12);
    add(
        f,
        button(
            "Copy password",
            () -> copyText("Generated password", output.getText().toString(), true),
            false));
    paragraphOn(
        f,
        "Generation is local. Saving a value here does not change a password at another service.");
    showDialog("Password generator", formScroll(f), "Close", null);
  }

  private void passwordReview() {
    try {
      JSONArray a = store.root().getJSONArray("records");
      HashMap<String, Integer> frequency = new HashMap<>();
      for (int i = 0; i < a.length(); i++) {
        JSONObject r = a.getJSONObject(i);
        if (r.optBoolean("trash")) continue;
        String p =
            r.optJSONObject("fields") == null
                ? ""
                : r.optJSONObject("fields").optString("Password");
        if (!p.isEmpty()) frequency.put(p, frequency.getOrDefault(p, 0) + 1);
      }
      StringBuilder b = new StringBuilder();
      int reviewed = 0;
      for (int i = 0; i < a.length(); i++) {
        JSONObject r = a.getJSONObject(i);
        if (r.optBoolean("trash")) continue;
        String p =
            r.optJSONObject("fields") == null
                ? ""
                : r.optJSONObject("fields").optString("Password");
        if (p.isEmpty()) continue;
        reviewed++;
        ArrayList<String> issues = new ArrayList<>();
        if (p.length() < 14) issues.add("shorter than 14 characters");
        if (frequency.get(p) > 1) issues.add("reused in this vault");
        if (!issues.isEmpty())
          b.append(r.optString("title"))
              .append(": ")
              .append(String.join(", ", issues))
              .append("\n\n");
      }
      info(
          "Local password review",
          reviewed
              + " saved passwords reviewed.\n\n"
              + (b.length() == 0
                  ? "No short or reused passwords found by these local checks."
                  : b.toString())
              + "\nThis is not a breach check or a guarantee of password security.");
    } catch (Exception e) {
      error(e);
    }
  }

  private void duplicates() {
    try {
      HashMap<String, ArrayList<String>> hashes = new HashMap<>();
      JSONArray a = store.root().getJSONArray("records");
      for (int i = 0; i < a.length(); i++) {
        JSONObject r = a.getJSONObject(i);
        if (r.optBoolean("trash")) continue;
        JSONArray fs = r.optJSONArray("files");
        if (fs != null)
          for (int j = 0; j < fs.length(); j++) {
            JSONObject f = fs.getJSONObject(j);
            String hash = f.optString("sha256");
            hashes
                .computeIfAbsent(hash, k -> new ArrayList<>())
                .add(r.optString("title") + " / " + f.optString("name"));
          }
      }
      StringBuilder out = new StringBuilder();
      for (var x : hashes.values())
        if (x.size() > 1) out.append(String.join("\n", x)).append("\n\n");
      info(
          "Duplicate original files",
          out.length() == 0
              ? "No identical attachments found."
              : out + "Files are compared locally. Nothing is deleted automatically.");
    } catch (Exception e) {
      error(e);
    }
  }

  private void copyText(String label, String val, boolean sensitive) {
    android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
    ClipData clip = ClipData.newPlainText(label, val);
    PersistableBundle extras = new PersistableBundle();
    extras.putBoolean("android.content.extra.IS_SENSITIVE", sensitive);
    clip.getDescription().setExtras(extras);
    cm.setPrimaryClip(clip);
    clock.postDelayed(
        () -> {
          try {
            ClipData current = cm.getPrimaryClip();
            if (current != null
                && current.getItemCount() > 0
                && val.contentEquals(current.getItemAt(0).coerceToText(this)))
              cm.clearPrimaryClip();
          } catch (Exception ignored) {
          }
        },
        30000);
    toast(
        label.equals("UPI ID")
            ? "UPI ID copied."
            : "Copied. Clipboard clears after 30 seconds where supported.");
  }

  private void credits() {
    TextView credit =
        text("Made with love ♥ by Vijay Mamta Sahani\n(@momentswithvms)", 12, muted, false);
    credit.setGravity(Gravity.CENTER);
    credit.setPadding(dp(4), dp(10), dp(4), dp(16));
    credit.setOnClickListener(
        v -> {
          try {
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/momentswithvms/")));
          } catch (Exception e) {
            toast("No browser available.");
          }
        });
    add(body, credit);
  }

  private void support(boolean full) {
    if (!full) return;
    gap(body, 24);
    credits();
    TextView donation = text("♥  Donate to developer", 14, accent, true);
    donation.setGravity(Gravity.CENTER);
    donation.setSingleLine(true);
    donation.setMinHeight(dp(48));
    donation.setBackgroundColor(card);
    donation.setContentDescription("Donate to developer");
    donation.setOnClickListener(v -> donate());
    body.addView(donation, new LinearLayout.LayoutParams(-1, dp(48)));
  }

  private void donate() {
    try {
      if (getPackageManager()
          .queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse(PAY)), 0)
          .isEmpty()) {
        donationFallback();
        return;
      }
      startActivity(
          Intent.createChooser(
              new Intent(Intent.ACTION_VIEW, Uri.parse(PAY)), "Choose an app to pay with"));
    } catch (Exception e) {
      donationFallback();
    }
  }

  private void donationFallback() {
    LinearLayout panel = form();
    paragraphOn(
        panel,
        "Pay any amount you like, from any UPI app. Your donation helps keep DocuBuds running and"
            + " ad-free.");
    try {
      ImageView qr = new ImageView(this);
      try (InputStream in = getAssets().open("donate-qr.png")) {
        qr.setImageBitmap(BitmapFactory.decodeStream(in));
      }
      qr.setBackgroundColor(Color.WHITE);
      qr.setContentDescription("Original donation UPI QR code for Vijay Kumar");
      panel.addView(qr, new LinearLayout.LayoutParams(-1, dp(240)));
    } catch (Exception ignored) {
    }
    paragraphOn(panel, "UPI ID: " + UPI);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Donate to the developer")
            .setView(formScroll(panel))
            .setPositiveButton("Copy UPI ID", (x, y) -> copyText("UPI ID", UPI, false))
            .setNegativeButton("Close", null)
            .create();
    watchDialog(d);
    d.show();
  }

  private Dialog sheet(String title, String subtitle, View content) {
    Dialog d = new Dialog(this);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout panel = col();
    panel.setBackground(shape(card, 28));
    panel.setPadding(dp(22), dp(12), dp(22), dp(22));
    View handle = new View(this);
    handle.setBackground(shape(line, 3));
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
    hp.gravity = Gravity.CENTER_HORIZONTAL;
    panel.addView(handle, hp);
    gap(panel, 16);
    LinearLayout top = row();
    TextView heading = text(title, 24, ink, true);
    top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    TextView close = text("×", 27, muted, false);
    close.setGravity(Gravity.CENTER);
    close.setContentDescription("Close sheet");
    top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    close.setOnClickListener(v -> d.dismiss());
    add(panel, top);
    if (subtitle != null && !subtitle.isEmpty()) {
      paragraphOn(panel, subtitle);
    }
    gap(panel, 12);
    content.measure(
        View.MeasureSpec.makeMeasureSpec(
            getResources().getDisplayMetrics().widthPixels - dp(44), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    ScrollView sc = new ScrollView(this);
    sc.setFillViewport(false);
    sc.addView(content);
    int max = (int) (getResources().getDisplayMetrics().heightPixels * .62);
    panel.addView(
        sc,
        new LinearLayout.LayoutParams(
            -1, Math.min(max, Math.max(dp(80), content.getMeasuredHeight()))));
    d.setContentView(panel);
    Window w = d.getWindow();
    w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    w.setGravity(Gravity.BOTTOM);
    WindowManager.LayoutParams attrs = w.getAttributes();
    attrs.dimAmount = .28f;
    w.setAttributes(attrs);
    w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    watchDialog(d);
    d.show();
    w.setLayout(-1, -2);
    panel.setTranslationY(dp(36));
    panel.setAlpha(0);
    panel.animate().translationY(0).alpha(1).setDuration(220).start();
    return d;
  }

  private boolean destructiveLabel(String label) {
    String value = label.toLowerCase(Locale.ROOT);
    return value.contains("delete")
        || value.contains("trash")
        || value.contains("remove attachment");
  }

  private void optionsSheet(
      String title,
      String subtitle,
      String[] labels,
      String[] icons,
      java.util.function.IntConsumer action) {
    LinearLayout list = col();
    Dialog[] dialog = {null};
    ArrayList<Integer> order = new ArrayList<>();
    for (int i = 0; i < labels.length; i++) if (!destructiveLabel(labels[i])) order.add(i);
    for (int i = 0; i < labels.length; i++) if (destructiveLabel(labels[i])) order.add(i);
    for (int slot = 0; slot < order.size(); slot++) {
      int i = order.get(slot);
      final int at = i;
      boolean destructive = destructiveLabel(labels[i]);
      LinearLayout entry = row();
      entry.setMinimumHeight(dp(62));
      entry.setPadding(dp(12), dp(10), dp(12), dp(10));
      entry.setBackground(shape(bg, 16));
      String icon = icons == null ? "doc" : icons[i];
      entry.addView(
          new Icons(this, icon, destructive ? Color.rgb(184, 73, 91) : accent),
          new LinearLayout.LayoutParams(dp(23), dp(23)));
      TextView label = text(labels[i], 16, destructive ? Color.rgb(184, 73, 91) : ink, true);
      label.setPadding(dp(16), 0, 0, 0);
      entry.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      entry.setContentDescription(labels[i]);
      entry.setOnClickListener(
          v -> {
            dialog[0].dismiss();
            safe(() -> action.accept(at));
          });
      add(list, entry);
      gap(list, 8);
    }
    dialog[0] = sheet(title, subtitle, list);
  }

  private int badgeFill(int index) {
    int[] palette =
        settingsDark
            ? new int[] {0xff163C52, 0xff203858, 0xff1E403B, 0xff473B26}
            : new int[] {0xffE5F6FE, 0xffECF2FF, 0xffEAF6F0, 0xffFFF4E3};
    return palette[Math.floorMod(index, palette.length)];
  }

  private int badgeInk(int index) {
    int[] palette =
        settingsDark
            ? new int[] {0xff6CD5FB, 0xff94B8FA, 0xff79CBB4, 0xffE4C17A}
            : new int[] {0xff006FAD, 0xff315FA9, 0xff267B69, 0xff9A6B1A};
    return palette[Math.floorMod(index, palette.length)];
  }

  private int spaceTone(String title) {
    String t = title.toLowerCase(Locale.ROOT);
    if (t.contains("bank") || t.contains("note") || t.contains("health")) return 2;
    if (t.contains("propert")
        || t.contains("home")
        || t.contains("shop")
        || t.contains("education")) return 3;
    if (t.contains("work") || t.contains("insurance")) return 1;
    return 0;
  }

  private void quickActions() {
    LinearLayout group = col();
    group.setBackground(border(card, 20));
    group.setPadding(dp(14), dp(16), dp(14), dp(14));
    add(group, text("Quick actions", 17, ink, true));
    gap(group, 16);
    LinearLayout actions = row();
    actions.setGravity(Gravity.TOP);
    String[] labels = {"Add files", "Scan", "New note", "New space"};
    String[] icons = {"folder", "scan", "note", "plus"};
    Runnable[] run = {
      () -> importFiles(""), () -> capture(""), () -> noteEditor(null), this::createSpace
    };
    for (int i = 0; i < labels.length; i++) {
      final int at = i;
      LinearLayout action = col();
      action.setGravity(Gravity.CENTER);
      FrameLayout badge = new FrameLayout(this);
      badge.setBackground(shape(badgeFill(i), 25));
      badge.addView(
          new Icons(this, icons[i], badgeInk(i)),
          new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
      action.addView(badge, new LinearLayout.LayoutParams(dp(50), dp(50)));
      gap(action, 8);
      TextView label = text(labels[i], 12, ink, true);
      label.setGravity(Gravity.CENTER);
      label.setMaxLines(2);
      add(action, label);
      action.setMinimumHeight(dp(84));
      action.setContentDescription(labels[i]);
      action.setOnClickListener(v -> safe(run[at]));
      actions.addView(action, new LinearLayout.LayoutParams(0, -2, 1));
    }
    add(group, actions);
    add(body, group);
    gap(body, 20);
  }

  private View spaceTile(JSONObject sp, int index) throws Exception {
    String id = sp.getString("id"), title = sp.optString("title");
    boolean compact = space.isEmpty();
    LinearLayout tile = col();
    tile.setGravity(Gravity.CENTER_HORIZONTAL);
    tile.setPadding(dp(3), dp(2), dp(3), dp(10));
    tile.setMinimumHeight(dp(compact ? 132 : 142));
    FrameLayout top = new FrameLayout(this);
    FrameLayout badge = new FrameLayout(this);
    badge.setBackground(shape(badgeFill(spaceTone(title)), 28));
    badge.addView(
        new Icons(this, iconFor(title), badgeInk(spaceTone(title))),
        new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER));
    FrameLayout.LayoutParams bp =
        new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.START | Gravity.CENTER_VERTICAL);
    top.addView(badge, bp);
    View more = iconButton("more", "Options for " + title, () -> spaceMenu(id));
    more.setBackgroundColor(Color.TRANSPARENT);
    FrameLayout.LayoutParams mp =
        new FrameLayout.LayoutParams(dp(38), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
    // The dots occupy only ~2dp inside their wider touch target.
    // Keep their visible edge ~2dp from the 54dp circle.
    mp.setMarginStart(0);
    top.addView(more, mp);
    LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(dp(76), dp(58));
    topParams.gravity = Gravity.CENTER_HORIZONTAL;
    tile.addView(top, topParams);
    gap(tile, 8);
    TextView label = text(title, compact ? 13 : 15, ink, true);
    label.setGravity(Gravity.CENTER);
    label.setMaxLines(2);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    add(tile, label);
    gap(tile, 4);
    int number = count(id);
    TextView count = text(number + (isNotesSpace(id) ? " notes" : " files"), 11, muted, false);
    count.setGravity(Gravity.CENTER);
    add(tile, count);
    tile.setContentDescription(title);
    tile.setOnClickListener(
        v -> {
          space = id;
          query = "";
          render();
        });
    tile.setOnLongClickListener(
        v -> {
          spaceMenu(id);
          return true;
        });
    return tile;
  }

  private void openSearch() {
    LinearLayout panel = col();
    EditText field = input("Search titles, tags and notes", "", false);
    add(panel, field);
    gap(panel, 10);
    LinearLayout results = col();
    ScrollView list = new ScrollView(this);
    list.addView(results);
    panel.addView(list, new LinearLayout.LayoutParams(-1, dp(330)));
    Dialog dialog = sheet("Find it in your vault", "Search stays on this phone.", panel);
    TextView prompt = text("Start typing to find a document, note or space.", 15, muted, false);
    add(results, prompt);
    field.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

          public void afterTextChanged(Editable e) {}

          public void onTextChanged(CharSequence text, int a, int b, int c) {
            results.removeAllViews();
            if (!store.unlocked()) return;
            String q = text.toString().trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) {
              add(results, prompt);
              return;
            }
            try {
              int matches = 0;
              JSONArray spaces = store.root().getJSONArray("spaces");
              for (int i = 0; i < spaces.length(); i++) {
                JSONObject sp = spaces.getJSONObject(i);
                if (!sp.optString("title").toLowerCase(Locale.ROOT).contains(q)) continue;
                add(
                    results,
                    item(
                        sp.optString("title"),
                        "Space",
                        iconFor(sp.optString("title")),
                        accent,
                        () -> {
                          dialog.dismiss();
                          tab = "Vault";
                          record = "";
                          space = sp.optString("id");
                          query = "";
                          render();
                        }));
                matches++;
              }
              JSONArray records = store.root().getJSONArray("records");
              for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i);
                if (r.optBoolean("trash") || r.optBoolean("archived")) continue;
                String hay =
                    r.optString("title")
                        + " "
                        + r.optString("tags")
                        + " "
                        + r.optString("notes")
                        + " "
                        + r.optString("ocr");
                JSONObject fields = r.optJSONObject("fields");
                if (fields != null)
                  for (Iterator<String> keys = fields.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    if (!sensitive(key)) hay += " " + fields.optString(key);
                  }
                if (!hay.toLowerCase(Locale.ROOT).contains(q)) continue;
                add(
                    results,
                    item(
                        r.optString("title"),
                        r.optString("type"),
                        "doc",
                        accent,
                        () -> {
                          dialog.dismiss();
                          record = r.optString("id");
                          tab = r.optString("type").equals("Secure note") ? "Notes" : "Vault";
                          query = "";
                          render();
                        }));
                matches++;
              }
              if (matches == 0)
                add(results, text("No matches yet. Try another word.", 15, muted, false));
            } catch (Exception e) {
              error(e);
            }
          }
        });
    field.requestFocus();
    dialog
        .getWindow()
        .setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    clock.postDelayed(
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT),
        180);
  }

  private boolean isNotesSpace(String id) {
    try {
      JSONObject sp = id.isEmpty() ? null : store.find("spaces", id);
      return sp != null
          && (sp.optString("kind").equals("notes")
              || sp.optString("title").equalsIgnoreCase("Secure notes"));
    } catch (Exception e) {
      return false;
    }
  }

  private void notesPage() throws Exception {
    layout("DocuBuds", false, true);
    add(body, text("Your notes", 30, ink, true));
    paragraph("A thought, a list, a little reminder. Make room for it.");
    notesContent("");
  }

  private void notesContent(String parent) throws Exception {
    ArrayList<JSONObject> notes = new ArrayList<>();
    JSONArray all = store.root().getJSONArray("records");
    for (int i = 0; i < all.length(); i++) {
      JSONObject r = all.getJSONObject(i);
      if (r.optBoolean("trash") || r.optBoolean("archived")) continue;
      if ((r.optString("type").equals("Secure note") || isNotesSpace(r.optString("parent")))
          && (parent.isEmpty() || r.optString("parent").equals(parent))) notes.add(r);
    }
    notes.sort(
        (a, b) ->
            Boolean.compare(b.optBoolean("pinned"), a.optBoolean("pinned")) != 0
                ? Boolean.compare(b.optBoolean("pinned"), a.optBoolean("pinned"))
                : Long.compare(b.optLong("updated"), a.optLong("updated")));
    if (notes.isEmpty()) {
      VaultArt art = new VaultArt(this, false);
      body.addView(art, new LinearLayout.LayoutParams(-1, dp(170)));
      add(body, text("Your next idea belongs here.", 23, ink, true));
      paragraph(
          "Tap + to write your first note. Give it a title, add text or a checklist, and it saves"
              + " right here.");
      return;
    }
    gap(body, 14);
    LinearLayout pair = null;
    int n = 0;
    for (JSONObject note : notes) {
      if (n % 2 == 0) {
        pair = row();
        pair.setGravity(Gravity.TOP);
        add(body, pair);
        gap(body, 12);
      }
      LinearLayout tile =
          new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
              super.onMeasure(widthSpec, heightSpec);
              int excess = getMeasuredHeight() - dp(150);
              if (excess > 0 && getChildCount() > 2 && getChildAt(2) instanceof TextView) {
                TextView snippet = (TextView) getChildAt(2);
                snippet.setMaxHeight(Math.max(0, snippet.getMeasuredHeight() - excess));
                super.onMeasure(widthSpec, heightSpec);
              }
            }
          };
      tile.setOrientation(LinearLayout.VERTICAL);
      tile.setPadding(dp(14), dp(12), dp(14), dp(12));
      tile.setBackground(shape(noteColour(note.optInt("noteColor")), 20));
      TextView title = text(note.optString("title", "Untitled note"), 18, ink, true);
      title.setMaxLines(2);
      add(tile, title);
      gap(tile, 10);
      TextView excerpt =
          text(
              note.optString("notes").isEmpty() ? "Tap to start writing…" : note.optString("notes"),
              14,
              muted,
              false);
      excerpt.setMaxLines(4);
      excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);
      add(tile, excerpt);
      gap(tile, 14);
      add(
          tile,
          text((note.optBoolean("pinned") ? "Pinned · " : "") + noteDate(note), 11, muted, false));
      tile.setContentDescription(note.optString("title"));
      tile.setOnClickListener(
          v -> {
            record = note.optString("id");
            render();
          });
      tile.setOnLongClickListener(
          v -> {
            recordActions(note);
            return true;
          });
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
      if (n % 2 == 0) tp.setMarginEnd(dp(12));
      pair.addView(tile, tp);
      n++;
    }
    if (pair != null && n % 2 == 1)
      pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
  }

  private int noteColour(int i) {
    int[] palette =
        settingsDark
            ? new int[] {0xff17384e, 0xff203b38, 0xff3a352b}
            : new int[] {0xffe9f7fe, 0xffedf6f1, 0xfffff6e8};
    return palette[Math.floorMod(i, palette.length)];
  }

  private String noteDate(JSONObject note) {
    return DateTimeFormatter.ofPattern("d MMM")
        .format(
            Instant.ofEpochMilli(note.optLong("updated", System.currentTimeMillis()))
                .atZone(ZoneId.systemDefault()));
  }

  private void noteDetail(JSONObject r) throws Exception {
    layout("DocuBuds", true, true);
    View more = iconButton("more", "Note actions", () -> recordActions(r));
    header.addView(more, header.getChildCount() - 1, new LinearLayout.LayoutParams(dp(48), dp(48)));
    add(body, text(r.optString("title", "Untitled note"), 30, ink, true));
    paragraph(noteDate(r) + " · Saved on this phone");
    String plain = r.optString("notes");
    if (plain.contains("☐") || plain.contains("☑")) {
      LinearLayout writing = col();
      writing.setBackground(shape(noteColour(r.optInt("noteColor")), 22));
      writing.setPadding(dp(16), dp(18), dp(16), dp(18));
      String[] lines = plain.split("\n", -1);
      int check = 0;
      for (int i = 0; i < lines.length; i++) {
        final int lineIndex = i;
        String line = lines[i];
        String trimmed = line.trim();
        final int checkIndex = check;
        for (char mark : line.toCharArray()) if (mark == '☐' || mark == '☑') check++;
        if (trimmed.startsWith("☐") || trimmed.startsWith("☑")) {
          CheckBox tick = new CheckBox(this);
          tick.setText(trimmed.substring(1).trim());
          tick.setTextColor(ink);
          tick.setTextSize(17);
          tick.setMinHeight(dp(50));
          tick.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
          tick.setChecked(trimmed.startsWith("☑"));
          tick.setOnCheckedChangeListener(
              (button, checked) -> {
                if (!store.unlocked()) return;
                try {
                  rememberVersion(r);
                  lines[lineIndex] = lines[lineIndex].replaceFirst("[☐☑]", checked ? "☑" : "☐");
                  r.put("notes", String.join("\n", lines));
                  if (r.has("noteHtml")) {
                    android.text.SpannableStringBuilder rich =
                        new android.text.SpannableStringBuilder(
                            android.text.Html.fromHtml(
                                r.optString("noteHtml"), android.text.Html.FROM_HTML_MODE_COMPACT));
                    int at = 0;
                    for (int n = 0; n < rich.length(); n++)
                      if (rich.charAt(n) == '☐' || rich.charAt(n) == '☑') {
                        if (at++ == checkIndex) {
                          rich.replace(n, n + 1, checked ? "☑" : "☐");
                          break;
                        }
                      }
                    r.put(
                        "noteHtml",
                        android.text.Html.toHtml(
                            rich, android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE));
                  }
                  r.put("updated", System.currentTimeMillis());
                  store.save();
                } catch (Exception e) {
                  error(e);
                }
              });
          add(writing, tick);
        } else {
          TextView lineText = text(line, 18, ink, false);
          lineText.setPadding(dp(4), dp(5), dp(4), dp(5));
          add(writing, lineText);
        }
      }
      add(body, writing);
    } else {
      TextView content = text("", 18, ink, false);
      content.setText(
          r.has("noteHtml")
              ? android.text.Html.fromHtml(
                  r.optString("noteHtml"), android.text.Html.FROM_HTML_MODE_COMPACT)
              : plain);
      content.setTextIsSelectable(true);
      content.setLineSpacing(dp(5), 1);
      content.setPadding(dp(18), dp(22), dp(18), dp(22));
      content.setMinHeight(dp(220));
      content.setBackground(shape(noteColour(r.optInt("noteColor")), 22));
      add(body, content);
    }
    gap(body, 18);
    add(body, button("Edit note", () -> noteEditor(r), false));
  }

  private void noteEditor(JSONObject existing) {
    try {
      boolean fresh = existing == null;
      JSONObject[] note = {existing};
      String parent = fresh ? space : existing.optString("parent");
      if (fresh && parent.isEmpty()) {
        JSONArray ss = store.root().getJSONArray("spaces");
        for (int i = 0; i < ss.length(); i++)
          if (isNotesSpace(ss.getJSONObject(i).optString("id"))) {
            parent = ss.getJSONObject(i).optString("id");
            break;
          }
      }
      final String destination = parent;
      Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
      LinearLayout page = col();
      page.setPadding(dp(20), dp(38), dp(20), dp(22));
      page.setBackgroundColor(card);
      LinearLayout top = row();
      TextView back = text("‹", 34, ink, false);
      back.setGravity(Gravity.CENTER);
      back.setContentDescription("Save and close note");
      top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
      TextView caption = text(fresh ? "New note" : "Edit note", 17, muted, true);
      top.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
      TextView done = button("Done", d::dismiss, true);
      top.addView(done, new LinearLayout.LayoutParams(dp(90), dp(52)));
      add(page, top);
      back.setOnClickListener(v -> d.dismiss());
      gap(page, 18);
      EditText title = input("Title", fresh ? "" : existing.optString("title"), false);
      title.setTextSize(27);
      title.setBackgroundColor(Color.TRANSPARENT);
      title.setPadding(dp(4), dp(10), dp(4), dp(10));
      add(page, title);
      EditText words = input("Start writing…", "", true);
      words.setTextSize(18);
      words.setBackgroundColor(Color.TRANSPARENT);
      words.setPadding(dp(4), dp(16), dp(4), dp(16));
      words.setGravity(Gravity.TOP);
      if (!fresh)
        words.setText(
            existing.has("noteHtml")
                ? android.text.Html.fromHtml(
                    existing.optString("noteHtml"), android.text.Html.FROM_HTML_MODE_COMPACT)
                : existing.optString("notes"));
      page.addView(words, new LinearLayout.LayoutParams(-1, 0, 1));
      Runnable[] scheduleSave = {null};
      LinearLayout tools = row();
      for (String label : new String[] {"B", "I", "☐"}) {
        TextView tool = text(label, 20, accent, true);
        tool.setGravity(Gravity.CENTER);
        tool.setBackground(shape(lavender, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMarginEnd(dp(8));
        tools.addView(tool, lp);
        tool.setContentDescription(
            label.equals("B")
                ? "Bold selected text"
                : label.equals("I") ? "Italic selected text" : "Insert checklist item");
        tool.setOnClickListener(
            v -> {
              app.touch();
              int start = Math.max(0, words.getSelectionStart()),
                  end = Math.max(start, words.getSelectionEnd());
              if (label.equals("☐")) {
                String marker = (start > 0 ? "\n" : "") + "☐ ";
                words.getText().insert(start, marker);
                words.requestFocus();
                words.setSelection(start + marker.length());
              } else {
                if (start == end) {
                  toast("Select the text you want to format.");
                  return;
                }
                int style =
                    label.equals("B")
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.ITALIC;
                boolean removed = false;
                for (android.text.style.StyleSpan span :
                    words.getText().getSpans(start, end, android.text.style.StyleSpan.class)) {
                  if (span.getStyle() == style) {
                    words.getText().removeSpan(span);
                    removed = true;
                  }
                }
                if (!removed)
                  words
                      .getText()
                      .setSpan(
                          new android.text.style.StyleSpan(style),
                          start,
                          end,
                          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                words.setSelection(end);
              }
              if (scheduleSave[0] != null) {
                clock.removeCallbacks(scheduleSave[0]);
                clock.postDelayed(scheduleSave[0], 700);
              }
            });
      }
      int[] colour = {fresh ? 0 : existing.optInt("noteColor")};
      for (int i = 0; i < 3; i++) {
        final int choice = i;
        TextView swatch = text("●", 28, noteColour(i), false);
        swatch.setGravity(Gravity.CENTER);
        swatch.setContentDescription("Note colour " + (i + 1));
        tools.addView(swatch, new LinearLayout.LayoutParams(dp(42), dp(48)));
        swatch.setOnClickListener(
            v -> {
              colour[0] = choice;
              words.setBackground(shape(noteColour(choice), 16));
              if (scheduleSave[0] != null) {
                app.touch();
                clock.removeCallbacks(scheduleSave[0]);
                clock.postDelayed(scheduleSave[0], 700);
              }
            });
      }
      HorizontalScrollView formatBar = new HorizontalScrollView(this);
      formatBar.setHorizontalScrollBarEnabled(false);
      formatBar.addView(tools);
      add(page, formatBar);
      gap(page, 8);
      TextView status = text("Notes save automatically on this phone", 12, muted, false);
      add(page, status);
      d.setContentView(page);
      d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      boolean[] versioned = {false};
      Runnable save =
          () -> {
            if (!store.unlocked()) return;
            String t = title.getText().toString().trim(), bodyText = words.getText().toString();
            if (note[0] == null && t.isEmpty() && bodyText.trim().isEmpty()) return;
            try {
              if (note[0] == null)
                note[0] = newRecord(t.isEmpty() ? "Untitled note" : t, destination);
              else if (!versioned[0]) {
                rememberVersion(note[0]);
                versioned[0] = true;
              }
              note[0]
                  .put("title", t.isEmpty() ? "Untitled note" : t)
                  .put("type", "Secure note")
                  .put("notes", bodyText)
                  .put(
                      "noteHtml",
                      android.text.Html.toHtml(
                          words.getText(), android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE))
                  .put("noteColor", colour[0])
                  .put("updated", System.currentTimeMillis());
              store.save();
              status.setText(
                  "Saved on this phone · "
                      + (bodyText.trim().isEmpty() ? 0 : bodyText.trim().split("\\s+").length)
                      + " words");
            } catch (Exception e) {
              status.setText("Could not save — keep this note open and try again");
              error(e);
            }
          };
      scheduleSave[0] = save;
      TextWatcher autosave =
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

            public void onTextChanged(CharSequence s, int a, int b, int c) {
              app.touch();
              status.setText("Saving…");
              clock.removeCallbacks(save);
              clock.postDelayed(save, 700);
            }

            public void afterTextChanged(Editable e) {}
          };
      title.addTextChangedListener(autosave);
      words.addTextChangedListener(autosave);
      d.setOnDismissListener(
          x -> {
            clock.removeCallbacks(save);
            save.run();
            if (store.unlocked() && !tearingDown) {
              if (note[0] != null) {
                record = note[0].optString("id");
                tab = "Notes";
              }
              render();
            }
          });
      watchDialog(d);
      d.show();
      if (fresh) {
        words.requestFocus();
        d.getWindow()
            .setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private void unlockSettings() {
    String custom = store.customType();
    optionsSheet(
        "Your way to unlock",
        "Choose an app PIN or pattern. Phone unlock stays available on this device.",
        custom.isEmpty()
            ? new String[] {"Set app PIN", "Set app pattern"}
            : new String[] {"Change app PIN", "Change app pattern", "Use phone unlock only"},
        null,
        i -> {
          afterUnlock =
              () -> {
                if (i == 2)
                  confirm(
                      "Remove app unlock?",
                      "Phone authentication will continue to protect this vault.",
                      () -> {
                        try {
                          store.removeCustomUnlock();
                          render();
                        } catch (Exception e) {
                          error(e);
                        }
                      });
                else configureCredential(i == 0 ? "PIN" : "Pattern");
              };
          authenticate();
        });
  }

  private String credentialLabel(String type) {
    return type.equals("PIN") ? "PIN" : "pattern";
  }

  private EditText pinInput() {
    EditText pin = input("App PIN", "", false);
    pin.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    pin.setTransformationMethod(PasswordTransformationMethod.getInstance());
    pin.setFilters(new InputFilter[] {new InputFilter.LengthFilter(12)});
    return pin;
  }

  private void configureCredential(String type) {
    LinearLayout content = col();
    TextView instruction =
        text(
            type.equals("PIN")
                ? "Choose 6–12 digits. Use a PIN you do not use elsewhere."
                : "Connect at least six dots. You will draw it twice.",
            15,
            muted,
            false);
    add(content, instruction);
    gap(content, 14);
    EditText pin = type.equals("PIN") ? pinInput() : null;
    PatternPad pad = pin == null ? new PatternPad(this, accent) : null;
    if (pin != null) add(content, pin);
    else content.addView(pad, new LinearLayout.LayoutParams(-1, dp(260)));
    gap(content, 14);
    char[][] first = {null};
    Dialog[] dialog = {null};
    TextView next = button("Continue", () -> {}, true);
    add(content, next);
    dialog[0] =
        sheet(
            "Set app " + credentialLabel(type),
            "This unlock works only with this phone’s encrypted vault.",
            content);
    dialog[0].setOnDismissListener(
        x -> {
          if (first[0] != null) Arrays.fill(first[0], (char) 0);
          if (pin != null) pin.getText().clear();
          else pad.clear();
        });
    next.setOnClickListener(
        v -> {
          char[] entered = pin != null ? pin.getText().toString().toCharArray() : pad.value();
          if (entered.length < 6) {
            Arrays.fill(entered, (char) 0);
            instruction.setText(
                type.equals("PIN") ? "Use at least six digits." : "Connect at least six dots.");
            return;
          }
          if (first[0] == null) {
            first[0] = entered;
            if (pin != null) pin.setText("");
            else pad.clear();
            instruction.setText("Confirm your app " + credentialLabel(type) + ".");
            next.setText("Save app " + credentialLabel(type));
            return;
          }
          if (!Arrays.equals(first[0], entered)) {
            Arrays.fill(entered, (char) 0);
            instruction.setText("Those did not match. Try the confirmation again.");
            if (pin != null) pin.setText("");
            else pad.clear();
            return;
          }
          Arrays.fill(entered, (char) 0);
          char[] secret = first[0].clone();
          dialog[0].dismiss();
          background(
              "Protecting your app unlock…",
              () -> {
                try {
                  store.setCustomUnlock(type, secret);
                } finally {
                  Arrays.fill(secret, (char) 0);
                }
              },
              () -> {
                toast("App " + credentialLabel(type) + " is ready");
                render();
              });
        });
  }

  private void customUnlock() {
    if (authShowing) return;
    String type = store.customType();
    if (type.isEmpty()) {
      authenticate();
      return;
    }
    authShowing = true;
    LinearLayout content = col();
    TextView status =
        text(
            type.equals("PIN") ? "Enter your app PIN." : "Draw your app pattern.",
            15,
            muted,
            false);
    add(content, status);
    gap(content, 14);
    EditText pin = type.equals("PIN") ? pinInput() : null;
    PatternPad pad = pin == null ? new PatternPad(this, accent) : null;
    if (pin != null) add(content, pin);
    else content.addView(pad, new LinearLayout.LayoutParams(-1, dp(260)));
    gap(content, 16);
    TextView unlock = button("Unlock vault", () -> {}, true);
    add(content, unlock);
    Dialog dialog =
        sheet("Your app " + credentialLabel(type), "A private entrance to your DocuBuds.", content);
    dialog.setOnDismissListener(
        x -> {
          authShowing = false;
          if (pin != null) pin.getText().clear();
          else pad.clear();
        });
    unlock.setOnClickListener(
        v -> {
          long wait = store.customWaitSeconds();
          if (wait > 0) {
            status.setText("Try again in " + wait + " seconds, or use phone unlock.");
            return;
          }
          char[] secret = pin != null ? pin.getText().toString().toCharArray() : pad.value();
          if (secret.length < 6) {
            Arrays.fill(secret, (char) 0);
            status.setText("Enter your complete app " + credentialLabel(type) + ".");
            return;
          }
          unlock.setEnabled(false);
          status.setText("Unlocking…");
          app.touch();
          app.io.execute(
              () -> {
                Exception failure = null;
                try {
                  store.unlockCustom(secret);
                } catch (Exception e) {
                  failure = e;
                } finally {
                  Arrays.fill(secret, (char) 0);
                }
                Exception result = failure;
                app.main.post(
                    () -> {
                      if (tearingDown || isDestroyed() || !dialog.isShowing()) {
                        store.lock();
                        return;
                      }
                      unlock.setEnabled(true);
                      if (result != null) {
                        store.lock();
                        status.setText(result.getMessage());
                        if (pin != null) pin.setText("");
                        else pad.clear();
                        return;
                      }
                      if (isFinishing()) {
                        store.lock();
                        return;
                      }
                      dialog.dismiss();
                      try {
                        onUnlocked();
                      } catch (Exception e) {
                        store.lock();
                        lockPage();
                        error(e);
                      }
                    });
              });
        });
  }

  public static String totp(String secret, long millis) throws Exception {
    String s = secret.toUpperCase(Locale.ROOT).replaceAll("[\\s=-]", "");
    if (s.length() < 16)
      throw new IllegalArgumentException(
          "Enter a valid Base32 authenticator secret of at least 16 characters");
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0, bits = 0;
    for (char c : s.toCharArray()) {
      int n = alphabet.indexOf(c);
      if (n < 0)
        throw new IllegalArgumentException(
            "Authenticator secret must use Base32 characters A–Z and 2–7");
      buffer = (buffer << 5) | n;
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        out.write((buffer >> bits) & 255);
      }
    }
    byte[] raw = out.toByteArray();
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new javax.crypto.spec.SecretKeySpec(raw, "HmacSHA1"));
    Arrays.fill(raw, (byte) 0);
    byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(millis / 30000).array());
    int offset = hash[hash.length - 1] & 15;
    int bin =
        ((hash[offset] & 127) << 24)
            | ((hash[offset + 1] & 255) << 16)
            | ((hash[offset + 2] & 255) << 8)
            | (hash[offset + 3] & 255);
    return String.format(Locale.ROOT, "%06d", bin % 1000000);
  }
}
