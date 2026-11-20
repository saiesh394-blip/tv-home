package com.home.tvhome;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.os.Handler;
import android.os.Looper;
import android.graphics.RadialGradient;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.util.Calendar;
import java.util.HashMap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import java.util.Locale;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An ad-free TV home screen with a clean, cinematic look:
 * a big blurred backdrop of the selected app, rounded tiles that lift when selected,
 * the app name shown only under the selected tile, and a small clock.
 * Hold OK on any app (or press Menu) to add, remove or reorder favourites.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "tvhome";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_USES = "uses";
    private static final int COLS = 5;
    private static final int BG = 0xFF0D0F14;

    private static final String[] DEFAULT_FAVS = {
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "in.startv.hotstartvonly",
            "com.jio.media.ondemand",
            "com.sonyliv",
            "com.spotify.tv.android"
    };

    static class App {
        String pkg, label;
        ComponentName cn;
        boolean leanback;
        Drawable banner, icon;
        ActivityInfo info;       // for loading a sharp, high-resolution icon
        String inputId;          // set for HDMI inputs (PS5 and other devices)
        boolean console;
        int glow1, glow2;
        boolean colorsReady;
    }

    static class Tag {
        final App app; final boolean fav; final String key; final TextView label;
        View inner, dim, sheen, ring;
        Tag(App app, boolean fav, String key, TextView label) {
            this.app = app; this.fav = fav; this.key = key; this.label = label;
        }
    }

    private PackageManager pm;
    private SharedPreferences prefs;
    private final Map<String, App> apps = new LinkedHashMap<>();
    private ScrollView scroll;
    private LinearLayout favRow, allGrid;
    private ImageView backdropA, backdropB, backdropFront;
    private FrameLayout backdropFrame;
    // Cinematic layout
    private ImageView wallA, wallB, wallFront;
    private TextView creditView;
    private GradientDrawable tintDrawable;
    private View tintView;
    private App pendingHero;
    private final Runnable heroUpdate = new Runnable() {
        @Override public void run() {
            if (pendingHero != null) filterWatchNext(pendingHero.pkg);
            if (pendingHero == null || shelfOn()) return;
            showAppHero(pendingHero);
            if (followColour()) applyColour(pendingHero);
            else showBackdrop(pendingHero);
        }
    };
    private int tintColor;
    private ValueAnimator tintAnim;
    private final Map<String, Integer> tintCache = new HashMap<>();
    private Wallpapers wallpapers;
    private Installer installer;
    // Dock
    private static final int DOCK_ICON = 56;          // dp, icon size at rest
    private static final int DOCK_GAP = 12;           // dp between icons
    private static final float DOCK_MAX = 1.55f;      // how big the selected icon grows
    private FrameLayout dockFrame;
    private View dockBar;
    private TipView dockTip;
    // Colour backdrop
    private View colA, colB, colFront;
    private int shownColour;
    private Bitmap basePhoto;              // the current wallpaper photo, untouched
    private String baseCredit;
    private int photoColour = -1;          // colour baked into the photo on screen (0 = none)
    private int composeGen;
    private final LinkedHashMap<Integer, Bitmap> tinted = new LinkedHashMap<Integer, Bitmap>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> e) { return size() > 5; }
    };
    /** Smooth motion (default): fewer, quicker animations for TVs with a slower graphics chip. */
    private boolean smooth;
    private static final int REQ_STORAGE = 43;
    private LinearLayout hero;
    private TextView heroEyebrow, heroTitle, heroSub;
    private boolean heroGreeting = true, userMoved;
    private int heroGen, page;
    private final View[] tabs = new View[4];
    private final GradientDrawable[] tabBg = new GradientDrawable[4];
    private final TextView[] tabText = new TextView[4];
    private final ImageView[] tabIcon = new ImageView[4];
    private final int[] tabIds = new int[4];
    private HorizontalScrollView favScroll;
    private LinearLayout content;
    private ValueAnimator vAnim, hAnim;
    private String focusKey, backdropPkg;
    private int tileW, tileH, gap, screenH;
    private boolean introDone;
    private ViewGroup mainRoot;
    private View pillView;
    // 6.20: Continue Watching, Recent, folders, icon packs, Send from Phone, remapping, glass
    private LinearLayout cwSection, cwRow, recentSection, recentRow;
    private FrameLayout folderOverlay;
    private LinearLayout folderPanel, folderGrid;
    private TextView folderTitle;
    private ScrollView folderScroll;
    private String openFolder;
    private IconPack iconPack;
    private PhoneUpload phoneUpload;
    private FrameLayout ccFrame;
    private ImageView ccBlur;
    private View ccFirst;
    private final List<GradientDrawable> ccBgs = new ArrayList<>();
    private final List<Runnable> ccRestyle = new ArrayList<>();
    private int ccTint = 0xFF2E3A44;
    // Settings screen
    private FrameLayout settingsOverlay;
    private ImageView settingsBg;
    private LinearLayout settingsList;
    private ScrollView settingsScroll;
    private TextView settingsTitle;
    private boolean settingsOpen;
    private final List<Object[]> pageStack = new ArrayList<>();   // {Runnable page, title}
    private App settingsApp;
    private SharedPreferences.OnSharedPreferenceChangeListener remapListener;
    private final Map<String, Drawable> glassCache = new HashMap<>();
    private Bitmap glassPhoto;
    private static final int REQ_EPG = 46;

    // 6.25: Top Shelf, weather pill and panel, opening animation, setup
    private LinearLayout weatherPill;
    private ImageView weatherIcon, avatarView, heroLogo;
    private TextView weatherTemp, shelfDots, shelfOpenText;
    private Weather.Data weatherData;
    private FrameLayout wxOverlay;
    private LinearLayout wxPanel, shelfActions;
    private ImageView wxBlur;
    private boolean wxOpen;
    private View shelfOpen;
    private List<TopShelf.Card> shelfCards = new ArrayList<>();
    private int shelfIndex = -1, cardGen;
    private boolean shelfLoading, idleFull;
    private String shelfLoadedMode;
    private long shelfLoadedAt, lastKey = System.currentTimeMillis(), lastAdvance;
    private Bitmap pendingWallpaper;
    private String pendingCredit;
    private View launchFrom;
    private boolean photoIsCard;

    // Control Centre
    private FrameLayout ccOverlay;
    private LinearLayout ccPanel;
    private View ccScrim;
    private boolean ccOpen;
    private final Map<String, TextView> ccSubs = new HashMap<>();
    private VolBar volBar;
    private AudioManager audio;

    // Search
    private FrameLayout searchOverlay;
    private LinearLayout searchResults;
    private EditText searchField;
    private boolean searchOpen;
    private View micView;
    private static final int REQ_VOICE = 42;

    // Greeting, weather, night dimming
    private TextView greetTitle, greetSub, weatherView;
    private View nightDim;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateGreeting();
            updateNight();
            if (wallpapers != null && !shelfOn()) wallpapers.refresh(false);
            ui.postDelayed(this, 60000);
        }
    };

    // Motion: a smooth ease for movement, a gentle spring for the selected tile.
    private static final TimeInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final TimeInterpolator SPRING = new OvershootInterpolator(1.1f);
    /** Fast start, long soft landing (like iOS sheets). */
    private static final TimeInterpolator GLIDE = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        UiStyle.load(this, prefs);
        smooth = prefs.getBoolean("smooth_motion", true);
        wallpapers = new Wallpapers(this, prefs, new Wallpapers.Listener() {
            @Override public void onWallpaper(Bitmap b, String credit) {
                if (shelfOn()) { pendingWallpaper = b; pendingCredit = credit; return; }
                showWallpaper(b, credit);
            }
        });

        // Size tiles so five fit across the screen.
        float density = getResources().getDisplayMetrics().density;
        int screenDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        gap = 20;
        int usable = screenDp - 2 * 56;
        tileW = (usable - (COLS - 1) * gap) / COLS;
        tileH = tileW * 3 / 5;

        FrameLayout outer = new FrameLayout(this);
        mainRoot = (ViewGroup) buildUi();
        outer.addView(mainRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nightDim = new View(this);
        nightDim.setBackgroundColor(0xFF000000);
        nightDim.setAlpha(0f);
        nightDim.setFocusable(false);
        nightDim.setClickable(false);
        outer.addView(nightDim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildControlCentre(outer);
        buildSearch(outer);
        buildFolderOverlay(outer);
        buildSettings(outer);
        buildWeatherPanel(outer);
        buildSwitcher(outer);
        if (mainRoot instanceof FrameLayout) buildSidebar((FrameLayout) mainRoot);
        if (getIntent() != null && getIntent().getBooleanExtra("switcher", false)) {
            getIntent().removeExtra("switcher");
            ui.postDelayed(new Runnable() { @Override public void run() { openSwitcher(); } }, 600);
        }
        setContentView(outer);
        String packPkg = prefs.getString("icon_pack", null);
        if (packPkg != null) loadIconPack(packPkg);
        getWindow().setBackgroundDrawable(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        userMoved = false;
        if (Wallpapers.MODE_COLOUR.equals(wallpapers.mode())) wallpapers.setMode(Wallpapers.MODE_SCENERY);
        wallpapers.refresh(false);
        loadApps();
        if (mainRoot instanceof FrameLayout) buildIsland((FrameLayout) mainRoot);
        addConsoleToDock();
        render();
        if (followColour() && basePhoto == null && colFront == null) {
            App f = firstFavApp();
            showColour(f != null ? colourFor(f) : 0xFF2B4C8C);   // until the first photo is ready
        } else if (followColour() && shownColour == 0) {
            App f = firstFavApp();
            if (f != null) applyColour(f);
        }
        showGreeting();
        showBackdrop(null);
        if (!prefs.getBoolean("setup_done", false)) {
            startActivity(new Intent(this, SetupActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        NotifListener.changed = new Runnable() {
            @Override public void run() { ui.post(new Runnable() { @Override public void run() { if (ccOpen) fillNotes(); } }); }
        };
        ui.removeCallbacks(tick);
        tick.run();
        refreshAvatar();
        applyShelfLayout();
        loadShelf(false);
        lastKey = System.currentTimeMillis();
        ui.removeCallbacks(shelfTick);
        ui.postDelayed(shelfTick, 2000);
        showCachedWeather();
        refreshWeather(false);
        loadWatchNext();
        askWatchNextOnce();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        ui.removeCallbacks(shelfTick);
        NotifListener.changed = null;
        stopBluetoothScan();
        btScanning = false;
        if (idleFull) wakeFromIdle();
        cancelVoice();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) { try { recognizer.destroy(); } catch (Exception ignored) { } recognizer = null; }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        long nowHome = System.currentTimeMillis();
        boolean wantSwitcher = intent != null && intent.getBooleanExtra("switcher", false);
        if (!wantSwitcher && prefs.getBoolean("home_double", true) && hasWindowFocus() && nowHome - lastHomeIntent < 450) wantSwitcher = true;
        lastHomeIntent = wantSwitcher ? 0 : nowHome;
        if (wantSwitcher) {
            if (ccOpen) closeControlCentre();
            if (searchOpen) closeSearch();
            if (settingsOpen) closeSettings(false);
            if (wxOpen) closeWeather();
            openSwitcher();
            return;
        }
        if (swOpen) closeSwitcher();
        if (ccOpen) closeControlCentre();
        if (searchOpen) closeSearch();
        if (openFolder != null) closeFolderPanel(false);
        if (settingsOpen) closeSettings(false);
        if (wxOpen) closeWeather();
        if (idleFull) wakeFromIdle();
        goTop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (ccOpen) { closeControlCentre(); return; }
        if (swOpen) { closeSwitcher(); return; }
        if (wxOpen) { closeWeather(); return; }
        if (listening) { cancelVoice(); return; }
        if (searchOpen) { closeSearch(); return; }
        if (openFolder != null) { closeFolderPanel(true); return; }
        if (settingsOpen) { settingsBack(); return; }
        goTop();
    }

    private void goTop() {
        userMoved = false;
        filterWatchNext(null);
        animateScrollY(0);
        animateScrollX(0);
        View first = firstTile();
        if (first != null) first.requestFocus();
        else if (tabs[2] != null) tabs[2].requestFocus();
        showGreeting();
        showBackdrop(null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        lastKey = System.currentTimeMillis();
        if (UiStyle.sounds > 0 && e.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = e.getKeyCode();
            if (kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT
                    || kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN) {
                final View before = getCurrentFocus();
                ui.post(new Runnable() {
                    @Override public void run() { if (getCurrentFocus() != before) UiStyle.tick(MainActivity.this); }
                });
            }
        }
        if (idleFull) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) wakeFromIdle();
            int k = e.getKeyCode();
            if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN && k != KeyEvent.KEYCODE_VOLUME_MUTE) return true;
        }
        if (shelfOpen != null && getCurrentFocus() == shelfOpen && e.getAction() == KeyEvent.ACTION_DOWN
                && (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            lastAdvance = System.currentTimeMillis();
            showCard(shelfIndex + (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
            return true;
        }
        // The remote's mic button: TV Home's own voice search with the colour waves, not Google's screen.
        int k0 = e.getKeyCode();
        if ((k0 == KeyEvent.KEYCODE_SEARCH || k0 == KeyEvent.KEYCODE_VOICE_ASSIST) && movingCell == null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) voiceFromRemote();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = e.getKeyCode();
            if (kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT
                    || kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN) {
                userMoved = true;
            }
        }
        // Move the selection ourselves so Android doesn't play its click on every step.
        if (movingCell == null && e.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = e.getKeyCode();
            int dir = kc == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT
                    : kc == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT
                    : kc == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP
                    : kc == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN : 0;
            View cur = getCurrentFocus();
            if (dir != 0 && cur != null && !(cur instanceof EditText)) {
                View next = FocusFinder.getInstance().findNextFocus(
                        (ViewGroup) getWindow().getDecorView(), cur, dir);
                if (next != null) next.requestFocus(dir);
                return true;
            }
        }
        if (movingCell != null) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                    || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
                return super.dispatchKeyEvent(e);
            }
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) moveBy(-1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) moveBy(1);
                else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                        || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU
                        || k == KeyEvent.KEYCODE_ESCAPE) finishMove();
            }
            return true;   // while moving, everything else is ignored
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------- Move mode ----------

    private View movingCell;
    private String movingPkg;
    private ObjectAnimator wiggle;
    private TextView moveHint;

    private void startMove(String pkg) {
        View tile = findByKey(new ViewGroup[]{favRow}, "f:" + pkg);
        if (tile == null) return;
        resetDock();
        movingPkg = pkg;
        movingCell = (View) tile.getParent();
        tile.requestFocus();
        wiggle = ObjectAnimator.ofFloat(tile, "rotation", -1.4f, 1.4f);
        wiggle.setDuration(UiStyle.ms(150));
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.start();
        tile.animate().scaleX(1.3f).scaleY(1.3f).setInterpolator(EASE).setDuration(UiStyle.ms(200)).start();
        if (moveHint != null) {
            moveHint.setText("\u2190  \u2192  to move     OK to place");
            moveHint.setAlpha(0f);
            moveHint.setVisibility(View.VISIBLE);
            moveHint.animate().alpha(1f).setDuration(UiStyle.ms(250)).start();
        }
    }

    private void moveBy(int dir) {
        int i = favRow.indexOfChild(movingCell);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= favRow.getChildCount() || isSettingsCell(favRow.getChildAt(j))) {
            // Gentle bump at the end of the row.
            movingCell.animate().translationX(dir * dp(10)).setDuration(UiStyle.ms(90)).withEndAction(new Runnable() {
                @Override public void run() {
                    movingCell.animate().translationX(0).setInterpolator(SPRING).setDuration(UiStyle.ms(220)).start();
                }
            }).start();
            return;
        }
        View neighbour = favRow.getChildAt(j);
        int w = movingCell.getWidth();
        // Move the neighbour (not the selected tile) so the selection never drops.
        favRow.removeView(neighbour);
        favRow.addView(neighbour, i);
        neighbour.setTranslationX(dir * w);
        neighbour.animate().translationX(0).setInterpolator(EASE).setDuration(UiStyle.ms(280)).start();
        movingCell.setTranslationX(-dir * w);
        movingCell.animate().translationX(0).setInterpolator(EASE).setDuration(UiStyle.ms(280)).start();

        List<String> favs = favs();
        int fi = favs.indexOf(movingPkg);
        if (fi >= 0 && fi + dir >= 0 && fi + dir < favs.size()) {
            Collections.swap(favs, fi, fi + dir);
            saveFavs(favs);
        }
        favRow.post(new Runnable() {
            @Override public void run() { if (movingCell != null) ensureVisible(movingCell); }
        });
    }

    private void finishMove() {
        if (wiggle != null) wiggle.cancel();
        View tile = ((ViewGroup) movingCell).getChildAt(0);
        tile.animate().rotation(0).setDuration(UiStyle.ms(150)).start();
        if (moveHint != null) moveHint.animate().alpha(0f).setDuration(UiStyle.ms(200)).start();
        focusKey = "f:" + movingPkg;
        final View placed = movingCell;
        movingCell = null;
        movingPkg = null;
        placed.postDelayed(new Runnable() {
            @Override public void run() { applyDock(favRow.indexOfChild(placed)); }
        }, 220);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (ccOpen && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE)) {
            boolean r = super.onKeyDown(keyCode, event);
            volBar.postDelayed(new Runnable() { @Override public void run() { updateVolume(); } }, 150);
            return r;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && ccOpen) { closeControlCentre(); return true; }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof Tag) {
                Tag t = (Tag) f.getTag();
                if (t.app != null) { showOptions(t.app, t.fav); return true; }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------- Layout ----------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1C2438, 0xFF0D0F14}));
        screenH = getResources().getDisplayMetrics().heightPixels;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int side = dp(56);
        int left = prefs.getBoolean("sidebar", false) ? dp(112) : side;

        // App colour backdrop: two full-screen colour layers; only one is drawn once a fade finishes.
        colA = new View(this);
        colB = new View(this);
        for (View v : new View[]{colA, colB}) {
            v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0, 0, 0}));
            v.setAlpha(0f);
            root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }

        // Wallpaper: two layers so one photo can fade into the next.
        wallA = new ImageView(this);
        wallB = new ImageView(this);
        for (ImageView w : new ImageView[]{wallA, wallB}) {
            w.setScaleType(ImageView.ScaleType.CENTER_CROP);
            w.setAlpha(0f);
            root.addView(w, new FrameLayout.LayoutParams(-1, -1));
        }

        // The dark shading for readable text is painted into each photo once (see shadePhoto),
        // so the TV doesn't redraw extra full-screen layers on every frame.

        // A soft tint in the selected app's colour, rising from the bottom edge behind the tiles.
        tintDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0, 0, 0});
        View tint = new View(this);
        tintView = tint;
        tint.setAlpha(0f);
        tint.setBackground(tintDrawable);
        root.addView(tint, new FrameLayout.LayoutParams(-1, (int) (screenH * 0.6f), Gravity.BOTTOM));

        // Hero: a small spaced label, a big title and a one-line tagline.
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        heroEyebrow = new UiStyle.FontText(this);
        heroEyebrow.setTextSize(12);
        heroEyebrow.setLetterSpacing(0.24f);
        heroEyebrow.setTextColor(0xBFFFFFFF);
        heroEyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heroEyebrow.setShadowLayer(dp(8), 0, 0, 0x66000000);
        heroEyebrow.setVisibility(View.GONE);
        hero.addView(heroEyebrow);
        heroLogo = new ImageView(this);
        heroLogo.setAdjustViewBounds(true);
        heroLogo.setScaleType(ImageView.ScaleType.FIT_START);
        heroLogo.setMaxWidth(dp(340));
        heroLogo.setVisibility(View.GONE);
        LinearLayout.LayoutParams hlg = new LinearLayout.LayoutParams(-2, dp(48));
        hlg.setMargins(0, dp(4), 0, dp(2));
        hero.addView(heroLogo, hlg);
        heroTitle = new UiStyle.FontText(this);
        greetTitle = heroTitle;
        heroTitle.setTextSize(34);
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heroTitle.setLetterSpacing(-0.01f);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setShadowLayer(dp(10), 0, 0, 0x66000000);
        hero.addView(heroTitle);
        heroSub = new UiStyle.FontText(this);
        greetSub = heroSub;
        heroSub.setTextSize(17);
        heroSub.setTextColor(0xB3FFFFFF);
        heroSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        heroSub.setShadowLayer(dp(8), 0, 0, 0x66000000);
        heroSub.setPadding(0, dp(4), 0, 0);
        heroSub.setMaxLines(2);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroSub, new LinearLayout.LayoutParams(-1, dp(56)));
        moveHint = new UiStyle.FontText(this);
        moveHint.setTextSize(14);
        moveHint.setTextColor(0xFFFFFFFF);
        moveHint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        moveHint.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable hbg = new GradientDrawable();
        hbg.setCornerRadius(dp(16));
        hbg.setColor(0x33FFFFFF);
        moveHint.setBackground(hbg);
        moveHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.setMargins(0, dp(16), 0, 0);
        hero.addView(moveHint, hl);
        shelfActions = new LinearLayout(this);
        shelfActions.setOrientation(LinearLayout.HORIZONTAL);
        shelfActions.setGravity(Gravity.CENTER_VERTICAL);
        shelfActions.setClipChildren(false);
        shelfActions.setClipToPadding(false);
        shelfOpen = pill("ic_search", "Where to Watch", "Open", new Runnable() {
            @Override public void run() { openCard(); }
        });
        shelfOpenText = (TextView) ((LinearLayout) shelfOpen).getChildAt(1);
        shelfActions.addView(shelfOpen, new LinearLayout.LayoutParams(-2, dp(32)));
        shelfDots = new UiStyle.FontText(this);
        shelfDots.setTextSize(9);
        shelfDots.setLetterSpacing(0.3f);
        shelfDots.setTextColor(0x66FFFFFF);
        shelfDots.setShadowLayer(dp(4), 0, 0, 0x66000000);
        LinearLayout.LayoutParams sdl = new LinearLayout.LayoutParams(-2, -2);
        sdl.setMargins(dp(16), 0, 0, 0);
        shelfActions.addView(shelfDots, sdl);
        shelfActions.setVisibility(View.GONE);
        LinearLayout.LayoutParams sal = new LinearLayout.LayoutParams(-2, -2);
        sal.setMargins(dp(2), dp(8), 0, 0);
        hero.addView(shelfActions, sal);
        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(
                (int) (screenW * 0.62f), -2, Gravity.TOP | Gravity.START);
        heroLp.setMargins(left, (int) (screenH * 0.18f), 0, 0);
        root.addView(hero, heroLp);
        setHeroNow(greetEyebrow(), greetTitleText(), greetLine());

        // Photo credit, small, in the empty top-left corner.
        creditView = new UiStyle.FontText(this);
        creditView.setTextSize(10);
        creditView.setTextColor(0x8CFFFFFF);
        creditView.setShadowLayer(dp(4), 0, 0, 0x99000000);
        creditView.setSingleLine(true);
        creditView.setEllipsize(TextUtils.TruncateAt.END);
        creditView.setMaxWidth((int) (screenW * 0.4f));
        creditView.setVisibility(View.GONE);
        FrameLayout.LayoutParams crl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        crl.setMargins(side, 0, 0, dp(8));
        root.addView(creditView, crl);

        // Weather pill, top left: an icon and the temperature. OK opens the details.
        weatherPill = new LinearLayout(this);
        weatherPill.setOrientation(LinearLayout.HORIZONTAL);
        weatherPill.setGravity(Gravity.CENTER_VERTICAL);
        weatherPill.setPadding(dp(10), 0, dp(14), 0);
        final GradientDrawable wbg = new GradientDrawable();
        wbg.setCornerRadius(dp(18));
        wbg.setColor(0x33FFFFFF);
        weatherPill.setBackground(wbg);
        weatherPill.setFocusable(true);
        weatherPill.setClickable(true);
        weatherPill.setContentDescription("Weather");
        if (Build.VERSION.SDK_INT >= 26) weatherPill.setDefaultFocusHighlightEnabled(false);
        weatherIcon = new ImageView(this);
        weatherPill.addView(weatherIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        weatherTemp = new UiStyle.FontText(this);
        weatherTemp.setTextSize(15);
        weatherTemp.setTextColor(Color.WHITE);
        weatherTemp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        weatherTemp.setPadding(dp(7), 0, 0, 0);
        weatherPill.addView(weatherTemp);
        weatherPill.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                wbg.setColor(has ? 0xFFFFFFFF : 0x33FFFFFF);
                weatherTemp.setTextColor(has ? 0xFF111317 : Color.WHITE);
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(has ? 300 : 200)).start();
            }
        });
        weatherPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openWeather(); }
        });
        weatherPill.setVisibility(View.GONE);
        FrameLayout.LayoutParams wpl = new FrameLayout.LayoutParams(-2, dp(36), Gravity.TOP | Gravity.START);
        wpl.setMargins(side, dp(24), 0, 0);
        root.addView(weatherPill, wpl);

        // All apps in the middle of the screen, scrolling on their own.
        int dockSpace = dp(DOCK_ICON + 34);
        scroll = new ScrollView(this) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
        };
        scroll.setVerticalScrollBarEnabled(false);
        // Rows fade softly into the dock instead of being cut off above it.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(44));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(left - dp(gap / 2), dp(4), side - dp(gap / 2), dp(24));
        cwSection = section("Continue Watching");
        cwRow = (LinearLayout) cwSection.getChildAt(1);
        content.addView(cwSection);
        recentSection = section("Recent");
        recentRow = (LinearLayout) recentSection.getChildAt(1);
        content.addView(recentSection);
        TextView appsLabel = new UiStyle.FontText(this);
        appsLabel.setText("All apps");
        appsLabel.setTextSize(14);
        appsLabel.setTextColor(0xE6FFFFFF);
        appsLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        appsLabel.setPadding(dp(gap / 2), 0, 0, dp(2));
        content.addView(appsLabel);
        allGrid = new LinearLayout(this);
        allGrid.setOrientation(LinearLayout.VERTICAL);
        allGrid.setClipChildren(false);
        allGrid.setClipToPadding(false);
        content.addView(allGrid);
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -1);
        sl.setMargins(0, (int) (screenH * 0.355f), 0, dockSpace);
        root.addView(scroll, sl);

        // The dock: your favourites as icons in a glass bar along the bottom, magnifying like a Mac.
        dockFrame = new FrameLayout(this);
        dockFrame.setClipChildren(false);
        dockFrame.setClipToPadding(false);
        dockBar = new View(this);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(22));
        glass.setColor(0x29FFFFFF);
        glass.setStroke(Math.max(1, dp(1)), 0x38FFFFFF);
        dockBar.setBackground(glass);
        dockFrame.addView(dockBar, new FrameLayout.LayoutParams(dp(200), dp(DOCK_ICON + 18),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setGravity(Gravity.BOTTOM);
        favRow.setClipChildren(false);
        favRow.setClipToPadding(false);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        rl.setMargins(0, 0, 0, dp(9));
        dockFrame.addView(favRow, rl);
        dockTip = new TipView(this);
        dockTip.setAlpha(0f);
        dockTip.setVisibility(prefs.getBoolean("dock_labels", false) ? View.VISIBLE : View.GONE);
        dockFrame.addView(dockTip, new FrameLayout.LayoutParams(dp(180), dp(26), Gravity.BOTTOM | Gravity.START));
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-1, dp(DOCK_ICON * 2 + 40), Gravity.BOTTOM);
        dl.setMargins(0, 0, 0, dp(14));
        root.addView(dockFrame, dl);

        // Top right: Search and Controls pills, weather and the clock.
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        topBar.setPadding(dp(10), dp(10), dp(10), dp(10));
        View search = pill("ic_search", null, "Search", new Runnable() {
            @Override public void run() { openSearch(); }
        });
        search.setId(View.generateViewId());
        tabs[2] = search;
        topBar.addView(search, new LinearLayout.LayoutParams(dp(36), dp(36)));
        View controls = pill("ic_tune", "Controls", "Control Centre", new Runnable() {
            @Override public void run() { openControlCentre(); }
        });
        controls.setId(View.generateViewId());
        tabs[3] = controls;
        pillView = controls;
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.setMargins(dp(10), 0, 0, 0);
        topBar.addView(controls, pl);

        weatherView = new UiStyle.FontText(this);
        weatherView.setTextSize(15);
        weatherView.setTextColor(0xCCFFFFFF);
        weatherView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        weatherView.setShadowLayer(dp(6), 0, 0, 0x80000000);
        weatherView.setVisibility(View.GONE);
        String cached = prefs.getString("weather_text", null);
        if (cached != null) { weatherView.setText(cached); weatherView.setVisibility(View.VISIBLE); }

        TextClock clock = new UiStyle.FontClock(this);
        clock.setFormat12Hour(clockFormat("h:mm"));
        clock.setFormat24Hour(clockFormat("HH:mm"));
        clock.setTextSize(20);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setShadowLayer(dp(6), 0, 0, 0x80000000);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.setMargins(dp(16), 0, 0, 0);
        topBar.addView(clock, cl);
        avatarView = new ImageView(this);
        avatarView.setVisibility(View.GONE);
        LinearLayout.LayoutParams avl = new LinearLayout.LayoutParams(dp(30), dp(30));
        avl.setMargins(dp(14), 0, 0, 0);
        topBar.addView(avatarView, avl);
        FrameLayout.LayoutParams tbl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tbl.setMargins(0, dp(14), side - dp(10), 0);
        root.addView(topBar, tbl);
        topBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return root;
    }

    // ---------- Wallpaper ----------

    /** A new wallpaper photo arrived: keep it, then show it with the current app's colour washed in. */
    private void showWallpaper(Bitmap b, String credit) {
        basePhoto = b;
        baseCredit = credit;
        tinted.clear();
        photoColour = -1;
        presentPhoto(followColour() ? shownColour : 0, 1200);
    }

    /** Shows the photo with colour c baked in (0 = plain). Prepared in the background, then faded in. */
    private void presentPhoto(final int c, final int fadeMs) {
        if (basePhoto == null || c == photoColour) return;
        photoColour = c;
        Bitmap ready = tinted.get(c);
        if (ready != null) { crossfadePhoto(ready, fadeMs); return; }
        final int gen = ++composeGen;
        final Bitmap src = basePhoto;
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = shadePhoto(src, c);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (src != basePhoto) return;
                        tinted.put(c, bmp);
                        if (gen == composeGen) crossfadePhoto(bmp, fadeMs);
                    }
                });
            }
        }).start();
    }

    /**
     * Fades to a new photo without reordering views (which would make the TV re-measure the screen).
     * wallA always sits under wallB: either paint A and fade B away, or paint B and fade it in.
     * Once a fade ends, only one photo is left to draw.
     */
    private void crossfadePhoto(Bitmap bmp, int fadeMs) {
        final ImageView under = wallA, over = wallB;
        over.animate().cancel();
        if (wallFront == over) {
            under.setImageBitmap(bmp);
            under.setAlpha(1f);
            over.animate().alpha(0f).setDuration(UiStyle.ms(fadeMs)).setInterpolator(EASE).withEndAction(new Runnable() {
                @Override public void run() { over.setImageDrawable(null); hideColourFill(); }
            }).start();
            wallFront = under;
        } else {
            over.setImageBitmap(bmp);
            if (wallFront == null) over.setAlpha(0f);
            over.animate().alpha(1f).setDuration(UiStyle.ms(fadeMs)).setInterpolator(EASE).withEndAction(new Runnable() {
                @Override public void run() {
                    under.setAlpha(0f);              // fully covered: stop drawing it
                    under.setImageDrawable(null);
                    hideColourFill();
                }
            }).start();
            wallFront = over;
        }
        ((ViewGroup) under.getParent()).setBackground(null);   // the photo covers the whole screen
        if (baseCredit == null || baseCredit.isEmpty()) {
            creditView.setVisibility(View.GONE);
        } else {
            creditView.setText(baseCredit);
            creditView.setVisibility(View.VISIBLE);
        }
    }

    private void hideColourFill() {
        colA.setAlpha(0f);
        colB.setAlpha(0f);
        colFront = null;
    }

    /** Paints the left, top and bottom shading into the photo itself, once per photo. */
    private Bitmap shadePhoto(Bitmap src, int colour) {
        try {
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            // Crop to the screen shape first so the shading lines up exactly with the screen.
            float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(out);
            c.translate((w - src.getWidth() * scale) / 2f, (h - src.getHeight() * scale) / 2f);
            c.scale(scale, scale);
            c.drawBitmap(src, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            c.setMatrix(null);
            Paint p = new Paint(Paint.DITHER_FLAG);
            if (colour != 0) {
                // Wash the app's colour over the photo: lighter top-left, richer bottom-right.
                float[] hsv = new float[3];
                Color.colorToHSV(colour, hsv);
                int light = Color.HSVToColor(0x80, new float[]{hsv[0], Math.min(1f, hsv[1]), 0.85f});
                int rich = Color.HSVToColor(0xBF, new float[]{hsv[0], Math.min(1f, hsv[1]), 0.45f});
                p.setShader(new LinearGradient(0, 0, w, h, light, rich, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
            }
            p.setShader(new LinearGradient(0, 0, w * 0.72f, 0,
                    new int[]{0x8C000000, 0x40000000, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w * 0.72f, h, p);
            p.setShader(new LinearGradient(0, 0, 0, h * 0.22f, 0x8C000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h * 0.22f, p);
            p.setShader(new LinearGradient(0, h * 0.38f, 0, h,
                    new int[]{0x00000000, 0x99080A12, 0xF2080A12}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.38f, w, h, p);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    private boolean colourMode() {
        return false;   // photos are always shown now; the colour is washed into them (see followColour)
    }

    private boolean followColour() {
        return prefs.getBoolean("follow_colour", true) && !shelfOn();
    }

    /** Applies the app's colour: baked into the photo, or a plain colour fill until a photo is ready. */
    private void applyColour(App a) {
        if (!followColour()) return;
        int c = colourFor(a);
        if (basePhoto != null) {
            shownColour = c;
            presentPhoto(c, smooth ? 300 : 450);
        } else {
            showColour(c);
        }
    }

    private int colourFor(App a) {
        Integer c = tintCache.get(a.pkg);
        if (c == null) {
            extractColors(a);
            c = a.glow1;
            tintCache.put(a.pkg, c);
        }
        return c;
    }

    /** Fills the screen with the app's colour: lighter top-left, deep at the bottom right. */
    private void showColour(int c) {
        if (c == shownColour && colFront != null) return;
        shownColour = c;
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        int top = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 0.85f), 0.78f});
        int mid = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 0.95f), 0.52f});
        int deep = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1]), 0.2f});
        int[] cols = {top, mid, deep};
        int dur = smooth ? 260 : 420;
        if (colFront == null) {
            ((GradientDrawable) colA.getBackground()).setColors(cols);
            colA.animate().alpha(1f).setDuration(UiStyle.ms(dur)).start();
            colFront = colA;
        } else if (colFront == colB) {
            // A sits underneath: paint it, then let B fade away to reveal it.
            ((GradientDrawable) colA.getBackground()).setColors(cols);
            colA.setAlpha(1f);
            colB.animate().cancel();
            colB.animate().alpha(0f).setDuration(UiStyle.ms(dur)).setInterpolator(EASE).start();
            colFront = colA;
        } else {
            ((GradientDrawable) colB.getBackground()).setColors(cols);
            colB.animate().cancel();
            colB.animate().alpha(1f).setDuration(UiStyle.ms(dur)).setInterpolator(EASE).start();
            colFront = colB;
        }
        ((View) colA.getParent()).setBackground(null);
    }

    private void leaveColourMode() {
        colA.setAlpha(0f);
        colB.setAlpha(0f);
        colFront = null;
        shownColour = 0;
    }

    private void wallpaperMenu() {
        final String mode = wallpapers.mode();
        final boolean follow = followColour();
        String packLabel = "None";
        String ipk = prefs.getString("icon_pack", null);
        if (ipk != null) {
            try { packLabel = pm.getApplicationLabel(pm.getApplicationInfo(ipk, 0)).toString(); }
            catch (Exception e) { packLabel = "None"; }
        }
        String name = prefs.getString("user_name", "");
        final String tick = "\u2713  ";
        final String[] items = {
                (Wallpapers.MODE_SCENERY.equals(mode) || Wallpapers.MODE_COLOUR.equals(mode) ? tick : "") + "Wallpaper: Scenery by time of day",
                (Wallpapers.MODE_MINE.equals(mode) ? tick : "") + "Wallpaper: My photos",
                (Wallpapers.MODE_PHONE.equals(mode) ? tick : "") + "Wallpaper: Send from phone\u2026",
                "Wallpaper: Shuffle now",
                (follow ? tick : "") + "Colour: Follow the selected app",
                (!follow ? tick : "") + "Colour: Off (plain photo)",
                (smooth ? tick : "") + "Motion: Smooth (best for this TV)",
                (smooth ? "" : tick) + "Motion: Full effects (fades, colour glow)",
                "Your name: " + (name.isEmpty() ? "none" : name),
                "Icon pack: " + packLabel};
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            wallpapers.setMode(Wallpapers.MODE_SCENERY);
                            wallpapers.refresh(true);
                        } else if (which == 1) {
                            wallpapers.setMode(Wallpapers.MODE_MINE);
                            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                            } else {
                                useMyPhotos();
                            }
                        } else if (which == 2) {
                            sendFromPhone();
                        } else if (which == 3) {
                            Toast.makeText(MainActivity.this, "Finding a new wallpaper\u2026", Toast.LENGTH_SHORT).show();
                            wallpapers.refresh(true);
                        } else if (which == 4 || which == 5) {
                            prefs.edit().putBoolean("follow_colour", which == 4).apply();
                            if (which == 4) {
                                View f = getCurrentFocus();
                                Tag t = f != null && f.getTag() instanceof Tag ? (Tag) f.getTag() : null;
                                App app = t != null && t.app != null ? t.app : firstFavApp();
                                if (app != null) applyColour(app);
                            } else {
                                shownColour = 0;
                                presentPhoto(0, 400);
                            }
                        } else if (which == 6 || which == 7) {
                            setSmooth(which == 6);
                        } else if (which == 8) {
                            askName();
                        } else {
                            chooseIconPack();
                        }
                    }
                })
                .show();
    }

    private void setSmooth(boolean on) {
        smooth = on;
        prefs.edit().putBoolean("smooth_motion", on).apply();
        resetDock();
        showBackdrop(null);
        render();
        Toast.makeText(this, on ? "Smooth motion on" : "Full effects on", Toast.LENGTH_SHORT).show();
    }

    private App firstFavApp() {
        for (String p : favs()) if (apps.containsKey(p)) return apps.get(p);
        return null;
    }

    private void useMyPhotos() {
        if (Wallpapers.countMine() == 0) {
            Toast.makeText(this, "Add photos to Pictures/TV Home on the TV. Showing scenery until then.",
                    Toast.LENGTH_LONG).show();
        }
        wallpapers.refresh(true);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_STORAGE) useMyPhotos();
        if (req == REQ_EPG) loadWatchNext();
        if (req == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) listenInApp();
            else voiceDialog();
        }
    }

    // ---------- Hero text ----------

    private String greetEyebrow() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return "GOOD MORNING";
        if (h >= 12 && h < 17) return "GOOD AFTERNOON";
        if (h >= 17 && h < 22) return "GOOD EVENING";
        return "LATE NIGHT";
    }

    private String greetTitleText() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 17 ? "Good afternoon"
                : h >= 17 && h < 22 ? "Good evening" : "Good night";
        String name = prefs.getString("user_name", "");
        return name.isEmpty() ? g : g + ", " + name;
    }

    private boolean askedName;

    /** "What should TV Home call you?" On first launch, and from Appearance. */
    private void askName() {
        askedName = true;
        final EditText field = new EditText(this);
        field.setHint("Your first name");
        field.setText(prefs.getString("user_name", ""));
        field.setSelectAllOnFocus(true);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(field);
        new AlertDialog.Builder(this)
                .setTitle("What should TV Home call you?")
                .setMessage("It's used in the greeting, like \"Good evening, Alex\".")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        prefs.edit().putString("user_name", field.getText().toString().trim()).apply();
                        heroTitle.setText(greetTitleText());
                    }
                })
                .setNegativeButton("No name", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        prefs.edit().putString("user_name", "").apply();
                        heroTitle.setText(greetTitleText());
                    }
                })
                .show();
    }

    private String greetLine() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 17 && h < 22) return "What would you like to watch tonight?";
        if (h >= 22 || h < 5) return "Something easy to wind down with?";
        return "What would you like to watch?";
    }

    // greetTitleText() always stays as the big title; only the line underneath follows the selected app.
    private void showGreeting() {
        ui.removeCallbacks(heroUpdate);
        pendingHero = null;
        if (shelfOn()) return;
        heroGreeting = true;
        heroTitle.setText(greetTitleText());
        setSub(greetLine());
    }

    private void showAppHero(App a) {
        heroGreeting = false;
        heroTitle.setText(greetTitleText());
        SpannableString line = new SpannableString(a.label + "   \u00B7   " + tagline(a)[1]);
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, a.label.length(), 0);
        line.setSpan(new ForegroundColorSpan(Color.WHITE), 0, a.label.length(), 0);
        setSub(line);
    }

    private int subGen;

    private void setSub(final CharSequence text) {
        if (text.toString().contentEquals(heroSub.getText())) return;
        if (smooth) {
            ++subGen;
            heroSub.animate().cancel();
            heroSub.setAlpha(1f);
            heroSub.setTranslationY(0);
            heroSub.setText(text);
            return;
        }
        final int gen = ++subGen;
        heroSub.animate().cancel();
        heroSub.animate().alpha(0f).setDuration(UiStyle.ms(110)).setInterpolator(EASE).withEndAction(new Runnable() {
            @Override public void run() {
                if (gen != subGen) return;
                heroSub.setText(text);
                heroSub.setTranslationY(dp(6));
                heroSub.animate().alpha(1f).translationY(0).setDuration(UiStyle.ms(300)).setInterpolator(EASE).start();
            }
        }).start();
    }

    private void setHeroNow(String eyebrow, String title, String sub) {
        heroEyebrow.setText(eyebrow);
        heroTitle.setText(title);
        heroSub.setText(sub);
    }

    private void setHero(final String eyebrow, final String title, final String sub) {
        if (title.contentEquals(heroTitle.getText()) && eyebrow.contentEquals(heroEyebrow.getText())
                && sub.contentEquals(heroSub.getText())) return;
        final int gen = ++heroGen;
        hero.animate().cancel();
        hero.animate().alpha(0f).setDuration(UiStyle.ms(130)).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (gen != heroGen) return;
                        setHeroNow(eyebrow, title, sub);
                        hero.setTranslationY(dp(10));
                        hero.animate().alpha(1f).translationY(0)
                                .setDuration(UiStyle.ms(340)).setInterpolator(EASE).start();
                    }
                }).start();
    }

    /** A small label and a one-line tagline for well-known apps. */
    private String[] tagline(App a) {
        if ("tvhome:settings".equals(a.pkg)) return new String[]{"SETTINGS", "Wallpaper, StandBy, icons and more."};
        if (a.inputId != null) return a.console
                ? new String[]{"GAME TIME", "Switch the TV to your console."}
                : new String[]{"INPUT", "Switch the TV to " + a.label + "."};
        String p = a.pkg.toLowerCase(), l = a.label.toLowerCase();
        if (p.contains("youtube.tvmusic") || l.contains("youtube music"))
            return new String[]{"MUSIC FOR THE ROOM", "Albums, mixes and live sessions."};
        if (p.contains("youtube") || l.equals("youtube"))
            return new String[]{"WATCH ANYTHING", "Videos, creators and music in one place."};
        if (p.contains("netflix"))
            return new String[]{"STORIES WORTH STAYING UP FOR", "Pick up right where you left off."};
        if (p.contains("amazonvideo") || l.contains("prime video"))
            return new String[]{"MOVIE NIGHT", "Blockbusters and originals, ready to roll."};
        if (p.contains("hotstar") || l.contains("hotstar") || l.contains("jiocinema"))
            return new String[]{"LIVE AND ON DEMAND", "Cricket, shows and movies in one tap."};
        if (p.contains("sonyliv") || l.contains("sony liv") || l.contains("sonyliv"))
            return new String[]{"SPORTS AND SHOWS", "Catch the match or your next binge."};
        if (p.contains("zee5") || l.contains("zee5"))
            return new String[]{"STORIES FROM HOME", "Shows and films in every language."};
        if (p.contains("spotify") || l.contains("spotify") || l.contains("music") || l.contains("gaana") || l.contains("wynk"))
            return new String[]{"THE SOUNDTRACK TO YOUR EVENING", "Play something that fits the mood."};
        if (l.contains("live tv") || l.equals("tv") || l.contains("channels") || l.contains("input"))
            return new String[]{"ON AIR NOW", "Your channels and inputs, right here."};
        if (l.contains("media") || l.contains("player") || l.contains("vlc") || l.contains("kodi") || l.contains("plex"))
            return new String[]{"YOUR OWN LIBRARY", "Play videos and music from your drives."};
        if (l.contains("miracast") || l.contains("cast") || l.contains("mirror") || l.contains("screen share"))
            return new String[]{"SHARE YOUR SCREEN", "Put your phone on the big screen."};
        if (p.contains("vending") || l.contains("play store"))
            return new String[]{"SOMETHING NEW", "Find apps and games for your TV."};
        if (l.contains("browser") || l.contains("chrome") || l.contains("bro") || l.contains("firefox"))
            return new String[]{"BROWSE THE WEB", "Look anything up on the big screen."};
        if (p.contains("ted") || l.equals("ted"))
            return new String[]{"IDEAS TO EXPLORE", "Talks that change how you see things."};
        if (l.contains("photo") || l.contains("gallery") || l.contains("album"))
            return new String[]{"YOUR MEMORIES", "Relive your favourite moments."};
        if (l.contains("game") || p.contains("game"))
            return new String[]{"GAME TIME", "Grab the remote and play."};
        if (l.contains("setting"))
            return new String[]{"MAKE IT YOURS", "Picture, sound, network and more."};
        if (l.contains("file"))
            return new String[]{"YOUR FILES", "Everything on your TV and drives."};
        return new String[]{"READY WHEN YOU ARE", "Press OK to open " + a.label + "."};
    }

    // ---------- Rendering ----------

    private void render() {
        favRow.removeAllViews();
        List<String> favList = favs();
        for (String p : favList) {
            App a = apps.get(p);
            if (a != null) favRow.addView(dockCell(a));
        }
        favRow.addView(dockCell(settingsApp()));
        if (favRow.getChildCount() == 0) {
            TextView empty = new UiStyle.FontText(this);
            empty.setText("Hold OK on any app and choose Add to favourites to fill your dock");
            empty.setTextSize(13);
            empty.setTextColor(0xB3FFFFFF);
            empty.setPadding(dp(20), dp(18), dp(20), dp(18));
            favRow.addView(empty);
        }
        int n = favRow.getChildCount();
        int barW = favRow.getChildAt(0) instanceof TextView ? dp(600) : n * dp(DOCK_ICON + DOCK_GAP) + dp(16);
        dockBar.getLayoutParams().width = barW;
        dockBar.setScaleX(1f);
        dockBar.setTranslationX(0);

        allGrid.removeAllViews();
        LinearLayout row = null;
        int i = 0;
        Set<String> hide = hidden();
        final Map<String, Integer> uses = uses();
        List<App> ordered = new ArrayList<>(apps.values());
        Collections.sort(ordered, new Comparator<App>() {
            @Override public int compare(App x, App y) {
                int ux = uses.containsKey(x.pkg) ? uses.get(x.pkg) : 0;
                int uy = uses.containsKey(y.pkg) ? uses.get(y.pkg) : 0;
                if (ux != uy) return uy - ux;   // most used first
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        Set<String> favSet = new HashSet<>(favList);
        List<App> inputs = new ArrayList<>();
        // Folders first, each as one tile.
        Map<String, List<String>> folders = folders();
        Set<String> inFolder = new HashSet<>();
        for (Map.Entry<String, List<String>> f : folders.entrySet()) {
            List<App> members = new ArrayList<>();
            for (String fp : f.getValue()) {
                App fa = apps.get(fp);
                if (fa != null && !hide.contains(fp)) { members.add(fa); inFolder.add(fp); }
            }
            if (members.isEmpty()) continue;
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                allGrid.addView(row);
            }
            row.addView(folderCell(f.getKey(), members));
            i++;
        }
        for (App a : ordered) {
            if (hide.contains(a.pkg) || favSet.contains(a.pkg) || inFolder.contains(a.pkg)) continue;
            if (a.inputId != null) { inputs.add(a); continue; }
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                allGrid.addView(row);
            }
            row.addView(cell(a, false));
            i++;
        }
        if (!inputs.isEmpty()) allGrid.addView(inputChips(inputs));
        renderRecents();
        updateUpLinks();
        muteClicks(getWindow().getDecorView());

        View target = focusKey == null ? null : findByKey(allGridAndFavs(), focusKey);
        if (target == null) target = firstTile();
        if (target == null) target = firstGridTile();
        if (target == null) target = tabs[2];
        if (target != null) target.requestFocus();

        if (!introDone) {
            introDone = true;
            hero.setAlpha(0f);
            hero.setTranslationY(dp(14));
            hero.animate().alpha(1f).translationY(0).setDuration(UiStyle.ms(700)).setInterpolator(EASE).start();
            dockFrame.setAlpha(0f);
            dockFrame.setTranslationY(dp(40));
            dockFrame.animate().alpha(1f).translationY(0).setStartDelay(150).setDuration(UiStyle.ms(600))
                    .setInterpolator(EASE).start();
        }
    }

    /** Up from the top row of apps goes to the pills; Down from the pills comes back to the apps. */
    private void updateUpLinks() {
        List<ViewGroup> rows = new ArrayList<>();
        if (cwSection != null && cwSection.getVisibility() == View.VISIBLE && cwRow.getChildCount() > 0) rows.add(cwRow);
        if (recentSection != null && recentSection.getVisibility() == View.VISIBLE && recentRow.getChildCount() > 0) rows.add(recentRow);
        if (allGrid.getChildCount() > 0) rows.add((ViewGroup) allGrid.getChildAt(0));
        View first = null;
        if (!rows.isEmpty() && rows.get(0).getChildAt(0) instanceof ViewGroup) {
            first = ((ViewGroup) rows.get(0).getChildAt(0)).getChildAt(0);
            if (first != null && !first.isFocusable()) first = null;
        }
        if (first == null) first = firstTile();
        if (first != null) {
            if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
            if (tabs[2] != null) tabs[2].setNextFocusDownId(first.getId());
            if (tabs[3] != null) tabs[3].setNextFocusDownId(first.getId());
        }
        if (tabs[2] == null) return;
        for (int r = 0; r < rows.size(); r++) {
            ViewGroup top = rows.get(r);
            for (int k = 0; k < top.getChildCount(); k++) {
                View c = top.getChildAt(k);
                if (c instanceof ViewGroup && ((ViewGroup) c).getChildCount() > 0) {
                    ((ViewGroup) c).getChildAt(0).setNextFocusUpId(r == 0 ? tabs[2].getId() : View.NO_ID);
                }
            }
        }
    }

    /** Turns off Android's click sound on every move inside TV Home. */
    private void muteClicks(View v) {
        v.setSoundEffectsEnabled(false);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) muteClicks(g.getChildAt(i));
        }
    }

    private void intro(View v, int delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(28));
        v.animate().alpha(1f).translationY(0).setStartDelay(delay)
                .setDuration(UiStyle.ms(520)).setInterpolator(EASE).start();
    }

    // ---------- Fluid scrolling ----------

    private void ensureVisible(View cell) {
        View row = (View) cell.getParent();
        if (row == null) return;

        if (row == favRow) return;   // the dock always fits on screen
        if (!isInside(row, content)) return;   // folder panel: its own scroll view handles it
        // All apps: bring the selected row up into view.
        Rect r = new Rect();
        row.getDrawingRect(r);
        content.offsetDescendantRectToMyCoords(row, r);
        int h = scroll.getHeight();
        int sy = scroll.getScrollY();
        int ty = sy;
        int margin = dp(12);
        if (r.top - sy < margin) ty = r.top - margin;
        else if (r.bottom - sy > h - margin) ty = r.bottom - h + margin;
        int maxY = Math.max(0, content.getHeight() - h);
        animateScrollY(Math.max(0, Math.min(ty, maxY)));
    }

    private void animateScrollY(int target) {
        if (vAnim != null) vAnim.cancel();
        int from = scroll.getScrollY();
        if (from == target) return;
        vAnim = ValueAnimator.ofInt(from, target);
        vAnim.setDuration(UiStyle.ms(smooth ? 260 : 460));
        vAnim.setInterpolator(EASE);
        vAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                scroll.scrollTo(0, (Integer) a.getAnimatedValue());
            }
        });
        vAnim.start();
    }

    private void animateScrollX(int target) {
        if (favScroll == null) return;
        if (hAnim != null) hAnim.cancel();
        int from = favScroll.getScrollX();
        if (from == target) return;
        hAnim = ValueAnimator.ofInt(from, target);
        hAnim.setDuration(UiStyle.ms(smooth ? 240 : 420));
        hAnim.setInterpolator(EASE);
        hAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                favScroll.scrollTo((Integer) a.getAnimatedValue(), 0);
            }
        });
        hAnim.start();
    }

    private ViewGroup[] allGridAndFavs() { return new ViewGroup[]{favRow, allGrid}; }

    private View firstTile() {
        if (favRow.getChildCount() == 0) return null;
        View c = favRow.getChildAt(0);
        return c instanceof ViewGroup && ((ViewGroup) c).getChildCount() > 0 ? ((ViewGroup) c).getChildAt(0) : null;
    }

    private View firstGridTile() {
        if (allGrid.getChildCount() == 0) return null;
        ViewGroup row = (ViewGroup) allGrid.getChildAt(0);
        if (row.getChildCount() == 0) return null;
        return ((ViewGroup) row.getChildAt(0)).getChildAt(0);
    }

    private View findByKey(ViewGroup[] roots, String key) {
        for (ViewGroup g : roots) {
            View v = findIn(g, key);
            if (v != null) return v;
        }
        return null;
    }

    private View findIn(ViewGroup g, String key) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v.getTag() instanceof Tag && key.equals(((Tag) v.getTag()).key)) return v;
            if (v instanceof ViewGroup) {
                View r = findIn((ViewGroup) v, key);
                if (r != null) return r;
            }
        }
        return null;
    }

    // ---------- Mac-style dock ----------

    /** One dock icon: a rounded square made from the app's artwork. */
    private View dockCell(final App a) {
        FrameLayout cell = new FrameLayout(this);
        cell.setClipChildren(false);
        final ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap art = dockArt(a, Math.round(dp(DOCK_ICON) * DOCK_MAX));
        if (art != null) icon.setImageBitmap(art); else icon.setImageDrawable(a.icon);
        icon.setFocusable(true);
        icon.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) icon.setDefaultFocusHighlightEnabled(false);
        icon.setPivotX(dp(DOCK_ICON) / 2f);
        icon.setPivotY(dp(DOCK_ICON));          // grow upward from the bar
        icon.setContentDescription(a.label);
        TextView hiddenLabel = new UiStyle.FontText(this);   // keeps the shared tile code happy
        Tag tag = new Tag(a, true, "f:" + a.pkg, hiddenLabel);
        icon.setTag(tag);
        icon.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) {
                    focusKey = "f:" + a.pkg;
                    if (movingCell == null) macDock(favRow.indexOfChild((View) v.getParent()));
                    if (userMoved) {
                        pendingHero = a;
                        ui.removeCallbacks(heroUpdate);
                        ui.postDelayed(heroUpdate, 140);
                    }
                } else {
                    favRow.post(new Runnable() {
                        @Override public void run() {
                            View f = getCurrentFocus();
                            if (f == null || f.getParent() == null || f.getParent().getParent() != favRow) macDock(-1);
                        }
                    });
                }
            }
        });
        icon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchFrom = v; launch(a); }
        });
        icon.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showOptions(a, true); return true; }
        });
        cell.addView(icon, new FrameLayout.LayoutParams(dp(DOCK_ICON), dp(DOCK_ICON), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        cell.setLayoutParams(new LinearLayout.LayoutParams(dp(DOCK_ICON + DOCK_GAP), dp(DOCK_ICON)));
        return cell;
    }

    /** Square icon: the app's banner on its own background colour, with rounded corners baked in. */
    private Bitmap dockArt(App a, int size) {
        try {
            Bitmap art = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(art);
            if ("tvhome:settings".equals(a.pkg)) {
                Bitmap g = Art.gearTile(size);
                c.drawBitmap(g, 0, 0, null);
                g.recycle();
                return roundOff(art, size);
            }
            Drawable packed = iconPack != null && a.inputId == null ? iconPack.get(a.cn) : null;
            if (packed != null) {
                c.drawColor(0xFF2B2F38);
                packed.setBounds(0, 0, size, size);
                packed.draw(c);
                Bitmap pout = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas pc = new Canvas(pout);
                Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                pp.setShader(new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                float pr = size * 0.23f;
                pc.drawRoundRect(new RectF(0, 0, size, size), pr, pr, pp);
                art.recycle();
                return pout;
            }
            Drawable sharp = a.inputId == null ? hiResIcon(a) : null;
            boolean adaptive = Build.VERSION.SDK_INT >= 26
                    && sharp instanceof android.graphics.drawable.AdaptiveIconDrawable;
            Drawable src = adaptive ? sharp : a.banner != null ? a.banner : sharp != null ? sharp : a.icon;
            if (src == null) return null;
            Drawable.ConstantState cs = src.getConstantState();
            Drawable d = cs != null ? cs.newDrawable(getResources()).mutate() : src;
            int iconStyle = prefs.getInt("icon_style", 0);
            if (iconStyle != 0 && a.inputId == null && styledIcon(c, a, d, adaptive, sharp, size, iconStyle)) {
                return roundOff(art, size);
            }
            if (adaptive) {
                // The app's own square icon, full bleed: the same art Android uses on phones.
                android.graphics.drawable.AdaptiveIconDrawable ad = (android.graphics.drawable.AdaptiveIconDrawable) d;
                int ext = size / 4;   // icon layers are 108 units with the visible 72 in the middle
                Drawable bg = ad.getBackground(), fg = ad.getForeground();
                c.drawColor(0xFF2B2F38);
                if (bg != null) { bg.setBounds(-ext, -ext, size + ext, size + ext); bg.draw(c); }
                if (fg != null) { fg.setBounds(-ext, -ext, size + ext, size + ext); fg.draw(c); }
            } else if (a.banner != null) {
                drawBannerSquare(c, d, size);
            } else if (a.inputId != null) {
                c.drawColor(a.console ? 0xFF1F3C88 : 0xFF33363F);
                int iw = Math.round(size * 0.5f);
                d.setBounds((size - iw) / 2, (size - iw) / 2, (size + iw) / 2, (size + iw) / 2);
                d.draw(c);
            } else {
                c.drawColor(0xFF2B2F38);
                int iw = Math.round(size * 0.72f);
                d.setBounds((size - iw) / 2, (size - iw) / 2, (size + iw) / 2, (size + iw) / 2);
                d.draw(c);
            }
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas oc = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setShader(new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            float r = size * 0.23f;
            oc.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
            art.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A sharp copy of the app's icon (Android otherwise hands out a small, soft one on TVs). */
    private Drawable hiResIcon(App a) {
        if (a.info == null) return null;
        try {
            int res = a.info.getIconResource();
            if (res == 0) return null;
            android.content.res.Resources r = pm.getResourcesForApplication(a.info.applicationInfo);
            return r.getDrawableForDensity(res, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * A wide banner in a square: the square is filled with the banner's own top and bottom edge colours,
     * and the banner sits in the middle with those edges faded out, so there's no bar or seam.
     */
    private static void drawBannerSquare(Canvas c, Drawable d, int size) {
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        float ratio = iw > 0 && ih > 0 ? ih / (float) iw : 9 / 16f;
        // Logo small and centred (Hotstar, SonyLIV)? Crop a square from the middle of the banner:
        // the banner's own background, no seams, and a bigger logo.
        if (logoFitsSquare(d, ratio)) {
            int bw = Math.round(size / ratio);
            int left = -(bw - size) / 2;
            d.setBounds(left, 0, left + bw, size);
            d.draw(c);
            return;
        }
        // 1. Edge colours: the average of the banner's top row and of its bottom row.
        Bitmap probe = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Canvas pc = new Canvas(probe);
        pc.drawColor(0xFF20232A);
        d.setBounds(0, 0, 32, 18);
        d.draw(pc);
        int top = rowAverage(probe, 1), bottom = rowAverage(probe, 16);
        probe.recycle();
        Paint fill = new Paint();
        fill.setShader(new LinearGradient(0, 0, 0, size, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, fill);
        Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG);
        // 2. The banner itself, full width, edges faded into the background.
        int bw = size, bh = Math.max(1, Math.round(size * ratio));
        Bitmap bb = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(bb);
        d.setBounds(0, 0, bw, bh);
        d.draw(bc);
        Paint mask = new Paint();
        mask.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mask.setShader(new LinearGradient(0, 0, 0, bh,
                new int[]{0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                new float[]{0f, 0.12f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        bc.drawRect(0, 0, bw, bh, mask);
        c.drawBitmap(bb, 0, (size - bh) / 2f, fp);
        bb.recycle();
    }

    /**
     * Finds the logo in a banner: pixels that stand out from that row's background (taken from the
     * banner's left and right edges). True when every such pixel sits inside the middle square.
     */
    private static boolean logoFitsSquare(Drawable d, float ratio) {
        int w = 96, h = Math.max(8, Math.round(w * ratio));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            Canvas bc = new Canvas(b);
            bc.drawColor(0xFF000000);
            d.setBounds(0, 0, w, h);
            d.draw(bc);
            int minX = w, maxX = -1;
            for (int y = 0; y < h; y++) {
                int l = b.getPixel(1, y), r = b.getPixel(w - 2, y);
                for (int x = 0; x < w; x++) {
                    int px = b.getPixel(x, y);
                    int dl = Math.abs(Color.red(px) - Color.red(l)) + Math.abs(Color.green(px) - Color.green(l))
                            + Math.abs(Color.blue(px) - Color.blue(l));
                    int dr = Math.abs(Color.red(px) - Color.red(r)) + Math.abs(Color.green(px) - Color.green(r))
                            + Math.abs(Color.blue(px) - Color.blue(r));
                    if (Math.min(dl, dr) > 90) { if (x < minX) minX = x; if (x > maxX) maxX = x; }
                }
            }
            if (maxX < 0) return true;                       // plain banner: cropping is safe
            float half = h / 2f * 0.92f;                      // the middle square, with a little margin
            return minX >= w / 2f - half && maxX <= w / 2f + half;
        } finally {
            b.recycle();
        }
    }

    private static int rowAverage(Bitmap b, int y) {
        long r = 0, g = 0, bl = 0;
        int w = b.getWidth();
        for (int x = 0; x < w; x++) {
            int px = b.getPixel(x, y);
            r += Color.red(px); g += Color.green(px); bl += Color.blue(px);
        }
        return Color.rgb((int) (r / w), (int) (g / w), (int) (bl / w));
    }

    /** The colour at the edges of a banner, used to fill the rest of the square icon. */
    private static int edgeColour(Drawable d) {
        Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 32, 18);
        d.draw(c);
        int[] pts = {b.getPixel(1, 1), b.getPixel(30, 1), b.getPixel(1, 16), b.getPixel(30, 16)};
        b.recycle();
        int r = 0, g = 0, bl = 0;
        for (int px : pts) { r += Color.red(px); g += Color.green(px); bl += Color.blue(px); }
        return Color.rgb(r / 4, g / 4, bl / 4);
    }

    private static float dockScale(int d) {
        return 1f + (DOCK_MAX - 1f) * (float) Math.exp(-(d * d) / 2.0);
    }

    /** Magnifies the selected icon and its neighbours in a smooth wave, widening the glass bar to fit. */
    private void macDock(final int focus) {
        if (dockFrame.getWidth() == 0) {   // not laid out yet: try again right after the first layout
            dockFrame.post(new Runnable() { @Override public void run() { macDock(focus); } });
            return;
        }
        int n = favRow.getChildCount();
        if (n == 0 || !(favRow.getChildAt(0) instanceof ViewGroup)) return;
        float w = dp(DOCK_ICON);
        float[] sc = new float[n];
        for (int i = 0; i < n; i++) sc[i] = focus < 0 ? 1f : dockScale(i - focus);
        float[] shift = new float[n];
        if (focus >= 0) {
            for (int i = 0; i < n; i++) {
                if (i == focus) continue;
                int dir = i > focus ? 1 : -1;
                float x = (sc[focus] - 1f) * w / 2f;
                for (int k = focus + dir; k != i; k += dir) x += (sc[k] - 1f) * w;
                x += (sc[i] - 1f) * w / 2f;
                shift[i] = x * dir;
            }
        }
        int dur = smooth ? 150 : 220;
        for (int i = 0; i < n; i++) {
            ViewGroup cell = (ViewGroup) favRow.getChildAt(i);
            View icon = cell.getChildAt(0);
            icon.animate().scaleX(sc[i]).scaleY(sc[i]).setInterpolator(EASE).setDuration(UiStyle.ms(dur)).start();
            cell.animate().translationX(shift[i]).setInterpolator(EASE).setDuration(UiStyle.ms(dur)).start();
            cell.setTranslationZ(focus >= 0 && Math.abs(i - focus) <= 1 ? dp(i == focus ? 2 : 1) : 0);
        }
        // Widen the glass bar by exactly what the icons need.
        float left = shift[0] - (sc[0] - 1f) * w / 2f;
        float right = shift[n - 1] + (sc[n - 1] - 1f) * w / 2f;
        float baseW = dockBar.getWidth() > 0 ? dockBar.getWidth() : dockBar.getLayoutParams().width;
        dockBar.animate().scaleX((baseW - left + right) / baseW).translationX((left + right) / 2f)
                .setInterpolator(EASE).setDuration(UiStyle.ms(dur)).start();
        // The app's name floats just above the magnified icon.
        if (focus < 0) {
            dockTip.animate().alpha(0f).setDuration(UiStyle.ms(120)).start();
            return;
        }
        App a = ((Tag) ((ViewGroup) favRow.getChildAt(focus)).getChildAt(0).getTag()).app;
        dockTip.setText(a.label);
        float cellW = dp(DOCK_ICON + DOCK_GAP);
        float rowLeft = (dockFrame.getWidth() - n * cellW) / 2f;
        float cx = rowLeft + focus * cellW + cellW / 2f + shift[focus];
        float tx = cx - dp(180) / 2f;
        float ty = -(dp(9) + dp(DOCK_ICON) * sc[focus] + dp(8));
        if (dockTip.getAlpha() < 0.05f) {
            dockTip.setTranslationX(tx);
            dockTip.setTranslationY(ty);
        }
        dockTip.animate().alpha(1f).translationX(tx).translationY(ty).setInterpolator(EASE).setDuration(UiStyle.ms(dur)).start();
    }

    // Kept so older code paths (move mode, settings) still work.
    private void applyDock(int focus) { macDock(focus); }
    private void resetDock() { macDock(-1); }

    /** A small rounded label that hugs its text, without re-measuring the screen when the text changes. */
    static class TipView extends View {
        private String text = "";
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        TipView(android.content.Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            bg.setColor(0xD91E2026);
            fg.setColor(Color.WHITE);
            fg.setTextSize(13 * c.getResources().getDisplayMetrics().scaledDensity);
            fg.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            fg.setTextAlign(Paint.Align.CENTER);
        }

        void setText(String t) {
            if (t.equals(text)) return;
            text = t;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (text.isEmpty()) return;
            float tw = fg.measureText(text);
            float pad = getHeight() * 0.5f;
            float w = Math.min(getWidth(), tw + pad * 2);
            r.set((getWidth() - w) / 2f, 0, (getWidth() + w) / 2f, getHeight());
            c.drawRoundRect(r, getHeight() / 2f, getHeight() / 2f, bg);
            Paint.FontMetrics fm = fg.getFontMetrics();
            c.drawText(text, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2f, fg);
        }
    }

    // ---------- Tiles ----------

    /** A tile with its name always shown underneath. */
    private LinearLayout cellShell(FrameLayout tile, TextView label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setPadding(dp(gap / 2), dp(10), dp(gap / 2), dp(4));
        cell.addView(tile, new LinearLayout.LayoutParams(dp(tileW), dp(tileH)));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(tileW), dp(30));
        cell.addView(label, llp);
        return cell;
    }

    private TextView nameLabel(String text) {
        TextView tv = new UiStyle.FontText(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        tv.setAlpha(0.62f);
        return tv;
    }

    private FrameLayout baseTile(int color) {
        FrameLayout t = new FrameLayout(this);
        if (color != 0) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(color);
            t.setBackground(bg);
        }
        t.setFocusable(true);
        if (Build.VERSION.SDK_INT >= 26) t.setDefaultFocusHighlightEnabled(false);
        t.setClickable(true);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                // Keep the selected tile above its neighbours while it's enlarged.
                if (v.getParent() instanceof View) ((View) v.getParent()).setTranslationZ(has ? dp(16) : 0);
                boolean inDock = v.getParent() != null && v.getParent().getParent() == favRow;
                if (inDock && movingCell == null && !smooth) {
                    // Favourites behave like a dock: neighbours grow too and the row spreads apart.
                    if (has) applyDock(favRow.indexOfChild((View) v.getParent()));
                    else favRow.post(new Runnable() {
                        @Override public void run() {
                            View f = getCurrentFocus();
                            if (f == null || f.getParent() == null || f.getParent().getParent() != favRow) resetDock();
                        }
                    });
                } else {
                    float big = smooth ? 1.06f : 1.09f;
                    v.animate().scaleX(has ? big : 1f).scaleY(has ? big : 1f)
                            .setInterpolator(has && !smooth ? SPRING : EASE)
                            .setDuration(UiStyle.ms(smooth ? 140 : has ? 300 : 200)).start();
                }
                Tag tag = (Tag) v.getTag();
                if (tag == null) return;
                if (tag.ring != null) {
                    if (smooth) tag.ring.setAlpha(has ? 1f : 0f);
                    else tag.ring.animate().alpha(has ? 1f : 0f).setDuration(UiStyle.ms(has ? 180 : 140)).start();
                }
                if (tag.dim != null) tag.dim.setAlpha(has ? 0f : 1f);
                if (tag.sheen != null) tag.sheen.animate().alpha(has ? 1f : 0f).setDuration(UiStyle.ms(has ? 300 : 150)).start();
                if (smooth) tag.label.setAlpha(has ? 1f : 0.62f);
                else tag.label.animate().alpha(has ? 1f : 0.62f).setDuration(UiStyle.ms(180)).start();
                if (has) {
                    focusKey = tag.key;
                    if (userMoved) {
                        // Wait until you pause on an app, so holding the button glides past smoothly.
                        pendingHero = tag.app;
                        ui.removeCallbacks(heroUpdate);
                        ui.postDelayed(heroUpdate, 160);
                    }
                    if (v.getParent() instanceof View) ensureVisible((View) v.getParent());
                }
            }
        });
        return t;
    }

    private View cell(final App a, final boolean fav) {
        return cell(a, fav, fav ? "f:" : "a:");
    }

    private View cell(final App a, final boolean fav, String keyPrefix) {
        FrameLayout t;
        if (a.banner != null) {
            t = baseTile(0);   // the artwork is already rounded, so nothing is drawn underneath
            ImageView iv = new ImageView(this);
            Bitmap rounded = roundedArt(a.banner, dp(tileW), dp(tileH), dp(14));
            if (rounded != null) {
                iv.setImageBitmap(rounded);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
            } else {
                iv.setImageDrawable(a.banner);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            t = baseTile(0xE62B2F38);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(a.icon);
            t.addView(ic, new FrameLayout.LayoutParams(dp(tileH / 2), dp(tileH / 2), Gravity.CENTER));
        }
        TextView label = nameLabel(a.label);
        t.setContentDescription(a.label);
        Tag tag = new Tag(a, fav, keyPrefix + a.pkg, label);
        tag.inner = t.getChildAt(0);
        addOverlays(t, tag);
        t.setTag(tag);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                press(v, new Runnable() { @Override public void run() { launchFrom = v; launch(a); } });
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showOptions(a, fav); return true; }
        });
        return cellShell(t, label);
    }

    /** Draws the app's artwork into a picture that already has rounded corners. */
    private Bitmap roundedArt(Drawable src, int w, int h, float radius) {
        try {
            Drawable.ConstantState cs = src.getConstantState();
            Drawable d = cs != null ? cs.newDrawable(getResources()).mutate() : src;
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }

            Bitmap art = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(art);
            float scale = Math.max(w / (float) iw, h / (float) ih);
            c.translate((w - iw * scale) / 2f, (h - ih * scale) / 2f);
            c.scale(scale, scale);
            d.setBounds(0, 0, iw, ih);
            d.draw(c);

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas oc = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setShader(new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            oc.drawRoundRect(new RectF(0, 0, w, h), radius, radius, p);
            art.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private void addOverlays(FrameLayout t, Tag tag) {
        // One overlay per tile, invisible (and so not drawn at all) unless the tile is selected.
        View ring = new View(this);
        GradientDrawable ringBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x30FFFFFF, 0x0AFFFFFF, 0x00FFFFFF});
        ringBg.setCornerRadius(dp(14));
        if (UiStyle.outline > 0) ringBg.setStroke(dp(UiStyle.outline), 0xF2FFFFFF);
        ring.setBackground(ringBg);
        ring.setAlpha(0f);
        t.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        tag.dim = null;
        tag.sheen = null;
        tag.ring = ring;
    }

    private View pill(String icon, String label, String desc, final Runnable action) {
        final LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        final boolean iconOnly = label == null;
        if (iconOnly) { pill.setGravity(Gravity.CENTER); pill.setPadding(0, 0, 0, 0); pill.setMinimumWidth(dp(36)); }
        else pill.setPadding(dp(14), 0, dp(16), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0x33FFFFFF);
        pill.setBackground(bg);
        pill.setFocusable(true);
        if (Build.VERSION.SDK_INT >= 26) pill.setDefaultFocusHighlightEnabled(false);
        pill.setClickable(true);
        pill.setContentDescription(desc);

        final ImageView ic = new ImageView(this);
        ic.setImageResource(drawable(icon));
        ic.setColorFilter(0xFFFFFFFF);
        pill.addView(ic, new LinearLayout.LayoutParams(dp(iconOnly ? 17 : 18), dp(iconOnly ? 17 : 18)));

        final TextView tv = iconOnly ? null : new UiStyle.FontText(this);
        if (tv != null) {
            tv.setText(label);
            tv.setTextSize(14);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setPadding(dp(8), 0, 0, 0);
            pill.addView(tv);
        }
        pill.setMinimumHeight(dp(36));

        pill.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFFFFFFFF : 0x33FFFFFF);
                int fg = has ? 0xFF111317 : 0xFFFFFFFF;
                ic.setColorFilter(fg);
                if (tv != null) tv.setTextColor(fg);
                float up = iconOnly ? 1.14f : 1.08f;
                v.animate().scaleX(has ? up : 1f).scaleY(has ? up : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(has ? 300 : 200)).start();
            }
        });
        pill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(UiStyle.ms(90)).withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(UiStyle.ms(200)).setInterpolator(SPRING).start();
                        action.run();
                    }
                }).start();
            }
        });
        return pill;
    }

    // ---------- Search ----------

    private void buildSearch(FrameLayout outer) {
        searchOverlay = new FrameLayout(this);
        searchOverlay.setVisibility(View.GONE);
        searchOverlay.setBackgroundColor(0xB3000000);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);

        searchField = new EditText(this);
        searchField.setHint("Search apps or YouTube");
        searchField.setHintTextColor(0x80FFFFFF);
        searchField.setTextColor(Color.WHITE);
        searchField.setTextSize(20);
        searchField.setSingleLine(true);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setPadding(dp(22), 0, dp(22), 0);
        final GradientDrawable fbg = new GradientDrawable();
        fbg.setCornerRadius(dp(28));
        fbg.setColor(0x26FFFFFF);
        searchField.setBackground(fbg);
        searchField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                fbg.setStroke(has ? dp(2) : 0, 0xFFFFFFFF);
            }
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) { updateResults(e.toString()); }
        });
        searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent ev) {
                if (searchResults.getChildCount() > 0) searchResults.getChildAt(0).performClick();
                return true;
            }
        });
        bar.addView(searchField, new LinearLayout.LayoutParams(0, dp(56), 1f));

        View mic = pill("ic_mic", "Speak", "Voice search", new Runnable() {
            @Override public void run() { startVoice(); }
        });
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        ml.setMargins(dp(12), 0, 0, 0);
        bar.addView(mic, ml);
        micView = mic;
        panel.addView(bar);

        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setClipChildren(false);
        searchResults.setPadding(0, dp(14), 0, 0);
        panel.addView(searchResults);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                dp(640), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        plp.setMargins(0, (int) (screenH * 0.14f), 0, 0);
        searchOverlay.addView(panel, plp);
        buildVoiceLayer();
        outer.addView(searchOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------- Voice (Siri-style waves) ----------

    private static final int REQ_MIC = 45;
    private FrameLayout voiceLayer;
    private TextView voiceText;
    private SiriWave wave;
    private SpeechRecognizer recognizer;
    private boolean listening, voiceReady;

    private void buildVoiceLayer() {
        voiceLayer = new FrameLayout(this);
        voiceLayer.setVisibility(View.GONE);
        voiceLayer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xB3000000, 0xE6000000}));

        voiceText = new UiStyle.FontText(this);
        voiceText.setTextSize(26);
        voiceText.setTextColor(Color.WHITE);
        voiceText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        voiceText.setGravity(Gravity.CENTER);
        voiceText.setMaxLines(2);
        voiceText.setEllipsize(TextUtils.TruncateAt.START);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(dp(760), -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        tl.setMargins(0, dp(40), 0, 0);
        voiceLayer.addView(voiceText, tl);

        wave = new SiriWave(this);
        voiceLayer.addView(wave, new FrameLayout.LayoutParams(-1, dp(130), Gravity.BOTTOM));

        searchOverlay.addView(voiceLayer, new FrameLayout.LayoutParams(-1, dp(250), Gravity.BOTTOM));
    }

    private void showVoiceLayer(String text) {
        voiceText.setText(text);
        voiceText.setAlpha(0.6f);
        voiceLayer.animate().cancel();
        voiceLayer.setVisibility(View.VISIBLE);
        voiceLayer.setAlpha(0f);
        voiceLayer.setTranslationY(dp(40));
        voiceLayer.animate().alpha(1f).translationY(0).setStartDelay(0).setInterpolator(EASE).setDuration(UiStyle.ms(320)).start();
        wave.start();
    }

    private void hideVoiceLayer(int delay) {
        listening = false;
        wave.stop();
        voiceLayer.animate().cancel();
        voiceLayer.animate().alpha(0f).translationY(dp(30)).setStartDelay(delay).setDuration(UiStyle.ms(280))
                .withEndAction(new Runnable() {
                    @Override public void run() { voiceLayer.setVisibility(View.GONE); }
                }).start();
    }

    private void listenInApp() {
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle b) { voiceReady = true; }
                    @Override public void onBeginningOfSpeech() { voiceReady = true; }
                    @Override public void onRmsChanged(float db) { if (listening) wave.setRms(db); }
                    @Override public void onBufferReceived(byte[] buf) { }
                    @Override public void onEndOfSpeech() { if (listening) wave.thinking(); }
                    @Override public void onEvent(int t, Bundle b) { }
                    @Override public void onPartialResults(Bundle b) {
                        String t = firstResult(b);
                        if (t != null && t.length() > 0 && listening) { voiceText.setText(t); voiceText.setAlpha(1f); }
                    }
                    @Override public void onResults(Bundle b) {
                        String t = firstResult(b);
                        if (t != null && t.length() > 0) {
                            voiceText.setText(t);
                            voiceText.setAlpha(1f);
                            hideVoiceLayer(350);
                            applyVoice(t);
                        } else {
                            voiceText.setText("Didn't catch that");
                            hideVoiceLayer(900);
                        }
                    }
                    @Override public void onError(int err) {
                        if (!listening) return;
                        boolean quiet = err == SpeechRecognizer.ERROR_NO_MATCH
                                || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                        if (!voiceReady && !quiet) {
                            // The TV's speech service won't run inside TV Home: use Google's own screen.
                            hideVoiceLayer(0);
                            voiceDialog();
                            return;
                        }
                        voiceText.setText(quiet ? "Didn't catch that" : "Voice isn't working right now");
                        voiceText.setAlpha(0.8f);
                        hideVoiceLayer(1100);
                    }
                });
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            listening = true;
            voiceReady = false;
            showVoiceLayer("Listening\u2026");
            recognizer.startListening(i);
        } catch (Exception e) {
            if (listening) hideVoiceLayer(0);
            voiceDialog();
        }
    }

    private static String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() ? null : r.get(0);
    }

    private void applyVoice(String t) {
        searchField.setText(t);
        searchField.setSelection(searchField.getText().length());
        if (searchResults.getChildCount() > 0) searchResults.getChildAt(0).requestFocus();
    }

    private void cancelVoice() {
        if (!listening) return;
        try { if (recognizer != null) recognizer.cancel(); } catch (Exception ignored) { }
        hideVoiceLayer(0);
    }

    private void openSearch() {
        if (searchOpen) return;
        searchOpen = true;
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 31) {
            mainRoot.setRenderEffect(RenderEffect.createBlurEffect(dp(16), dp(16), Shader.TileMode.CLAMP));
        }
        searchField.setText("");
        updateResults("");
        searchOverlay.setVisibility(View.VISIBLE);
        searchOverlay.setAlpha(0f);
        searchOverlay.animate().alpha(1f).setDuration(UiStyle.ms(260)).start();
        searchField.requestFocus();
    }

    private void closeSearch() {
        if (!searchOpen) return;
        searchOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        searchOverlay.animate().alpha(0f).setDuration(UiStyle.ms(200)).withEndAction(new Runnable() {
            @Override public void run() { searchOverlay.setVisibility(View.GONE); }
        }).start();
        if (Build.VERSION.SDK_INT >= 31) mainRoot.setRenderEffect(null);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (tabs[2] != null) tabs[2].requestFocus();
    }

    @Override
    public boolean onSearchRequested() {
        voiceFromRemote();
        return true;
    }

    private void voiceFromRemote() {
        if (ccOpen) closeControlCentre();
        if (!searchOpen) openSearch();
        if (micView != null) micView.requestFocus();   // keeps the on-screen keyboard away
        if (!listening) startVoice();
    }

    private void startVoice() {
        if (listening) {   // OK again: stop and use what was heard
            try { recognizer.stopListening(); } catch (Exception e) { cancelVoice(); }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { voiceDialog(); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        listenInApp();
    }

    private void voiceDialog() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to watch?");
            startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input isn't available, type instead", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) applyVoice(r.get(0));
        }
    }

    private void updateResults(final String raw) {
        searchResults.removeAllViews();
        final String q = raw.trim();
        if (q.isEmpty()) return;
        String lq = q.toLowerCase();
        int n = 0;
        for (final App a : apps.values()) {
            if (n >= 4) break;
            if (!a.label.toLowerCase().contains(lq)) continue;
            searchResults.addView(resultRow("ic_apps", "Open " + a.label, new Runnable() {
                @Override public void run() { closeSearch(); launch(a); }
            }));
            n++;
        }
        final App yt = apps.get("com.google.android.youtube.tv");
        searchResults.addView(resultRow("ic_search", "Search YouTube for “" + q + "”", new Runnable() {
            @Override public void run() {
                closeSearch();
                try {
                    Intent i = new Intent(Intent.ACTION_SEARCH).putExtra("query", q);
                    if (yt != null) i.setPackage(yt.pkg);
                    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    start(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))));
                }
            }
        }));
        searchResults.addView(resultRow("ic_apps", "Search Play Store for “" + q + "”", new Runnable() {
            @Override public void run() {
                closeSearch();
                start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q))));
            }
        }));
    }

    private View resultRow(String icon, String text, final Runnable action) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(20), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x14FFFFFF);
        row.setBackground(bg);
        row.setFocusable(true);
        row.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) row.setDefaultFocusHighlightEnabled(false);
        final ImageView ic = new ImageView(this);
        ic.setImageResource(drawable(icon));
        ic.setColorFilter(0xFFFFFFFF);
        row.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
        final TextView tv = new UiStyle.FontText(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(Color.WHITE);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(14), 0, 0, 0);
        row.addView(tv);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFFFFFFFF : 0x14FFFFFF);
                int fg = has ? 0xFF111317 : 0xFFFFFFFF;
                ic.setColorFilter(fg);
                tv.setTextColor(fg);
                v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(240)).start();
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);
        return row;
    }

    // ---------- Time-aware greeting and night dimming ----------

    private void updateGreeting() {
        // Keeps the greeting current (morning, evening...) while it's showing.
        if (heroTitle == null || shelfOn()) return;
        String t = greetTitleText();
        if (!t.contentEquals(heroTitle.getText())) heroTitle.setText(t);
        if (heroGreeting && !greetLine().contentEquals(heroSub.getText())) heroSub.setText(greetLine());
    }

    private void updateNight() {
        if (nightDim == null) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 23 || h < 6;
        float target = night ? 0.28f : 0f;
        if (Math.abs(nightDim.getAlpha() - target) > 0.01f) {
            nightDim.animate().alpha(target).setDuration(UiStyle.ms(3000)).start();
        }
    }

    // ---------- Weather ----------

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "TVHome/1.0");
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow";
        if (code >= 95) return "Storm";
        return "";
    }

    private ImageView makeBackdrop() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setAlpha(0f);
        return iv;
    }

    private void press(final View v, final Runnable then) {
        // A quick dip and spring back to whatever size the tile is at (dock tiles are bigger).
        final float rest = v.getScaleX() > 1.01f ? v.getScaleX() : (smooth ? 1.06f : 1.09f);
        if (smooth) { then.run(); return; }
        v.animate().scaleX(rest * 0.94f).scaleY(rest * 0.94f).setDuration(UiStyle.ms(90)).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(rest).scaleY(rest).setDuration(UiStyle.ms(220))
                                .setInterpolator(SPRING).start();
                        then.run();
                    }
                }).start();
    }

    /** Finds the app's main colours from its artwork. */
    private void extractColors(App a) {
        if (a.colorsReady) return;
        a.colorsReady = true;
        a.glow1 = 0xFF34507A;
        a.glow2 = 0xFF4A3470;
        Drawable src = a.banner != null ? a.banner : a.icon;
        if (src == null) return;
        try {
            Drawable.ConstantState cs = src.getConstantState();
            Drawable d = cs != null ? cs.newDrawable(getResources()).mutate() : src;
            int w = 32, h = 18;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            float[] weight = new float[12];
            float[][] sum = new float[12][3];
            float[] hsv = new float[3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] < 0.28f || hsv[2] < 0.2f) continue;   // skip whites, greys, blacks
                    int bucket = Math.min(11, (int) (hsv[0] / 30f));
                    float wgt = hsv[1] * hsv[2];
                    weight[bucket] += wgt;
                    sum[bucket][0] += Color.red(px) * wgt;
                    sum[bucket][1] += Color.green(px) * wgt;
                    sum[bucket][2] += Color.blue(px) * wgt;
                }
            }
            b.recycle();
            int best = -1, second = -1;
            for (int i = 0; i < 12; i++) {
                if (weight[i] <= 0) continue;
                if (best < 0 || weight[i] > weight[best]) { second = best; best = i; }
                else if (second < 0 || weight[i] > weight[second]) second = i;
            }
            if (best < 0) return;
            a.glow1 = vivid(avg(sum[best], weight[best]), 0f);
            a.glow2 = second >= 0 && weight[second] > weight[best] * 0.15f
                    ? vivid(avg(sum[second], weight[second]), 0f)
                    : vivid(a.glow1, 28f);   // a neighbouring shade of the main colour
        } catch (Throwable ignored) { }
    }

    private static int avg(float[] s, float w) {
        return Color.rgb((int) (s[0] / w), (int) (s[1] / w), (int) (s[2] / w));
    }

    private static int vivid(int color, float hueShift) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.max(hsv[1], 0.65f);
        hsv[2] = 0.75f;
        return Color.HSVToColor(hsv);
    }

    /** A soft, two-colour glow on dark, made from the app's own colours. */
    private Bitmap glowFor(App a) {
        extractColors(a);
        int w = 192, h = 108;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(w * 0.28f, h * 0.55f, w * 0.62f,
                new int[]{(a.glow1 & 0xFFFFFF) | 0xB3000000, (a.glow1 & 0xFFFFFF) | 0x33000000, 0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.82f, h * 0.18f, w * 0.5f,
                new int[]{(a.glow2 & 0xFFFFFF) | 0x8C000000, (a.glow2 & 0xFFFFFF) | 0x26000000, 0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    /** Fades the bottom tint to the selected app's main colour (or away when nothing is selected). */
    private void showBackdrop(App a) {
        if (tintDrawable == null) return;
        if (smooth) {
            tintColor = 0;
            tintView.animate().cancel();
            tintView.setAlpha(0f);
            return;
        }
        int target = 0;
        if (a != null) {
            Integer cached = tintCache.get(a.pkg);
            if (cached == null) {
                extractColors(a);   // worked out once per app, then remembered
                cached = a.glow1;
                tintCache.put(a.pkg, cached);
            }
            target = cached;
        }
        if (target == tintColor) return;
        final int to = target;
        tintColor = target;
        // Cheap on the TV: the gradient is set once and only the layer's opacity is animated.
        tintView.animate().cancel();
        if (to == 0) {
            tintView.animate().alpha(0f).setDuration(UiStyle.ms(250)).setInterpolator(EASE).start();
            return;
        }
        Runnable swap = new Runnable() {
            @Override public void run() {
                tintDrawable.setColors(new int[]{withAlpha(to, 0x66), withAlpha(to, 0x24), 0});
                tintView.animate().alpha(1f).setDuration(UiStyle.ms(320)).setInterpolator(EASE).start();
            }
        };
        if (tintView.getAlpha() > 0.05f) {
            tintView.animate().alpha(0f).setDuration(UiStyle.ms(140)).setInterpolator(EASE).withEndAction(swap).start();
        } else {
            swap.run();
        }
    }

    /** Blends two colours, treating "no colour" (0) as fully transparent. */
    private static int blend(int c1, int c2, float t) {
        float a1 = c1 == 0 ? 0 : 1, a2 = c2 == 0 ? 0 : 1;
        int base1 = c1 == 0 ? c2 : c1, base2 = c2 == 0 ? c1 : c2;
        int r = (int) (Color.red(base1) + (Color.red(base2) - Color.red(base1)) * t);
        int g = (int) (Color.green(base1) + (Color.green(base2) - Color.green(base1)) * t);
        int b = (int) (Color.blue(base1) + (Color.blue(base2) - Color.blue(base1)) * t);
        int a = (int) (255 * (a1 + (a2 - a1) * t));
        return Color.argb(a, r, g, b);
    }

    private static int withAlpha(int c, int alpha) {
        return Color.argb(Color.alpha(c) * alpha / 255, Color.red(c), Color.green(c), Color.blue(c));
    }


    // ---------- Control Centre ----------

    private int drawable(String name) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        return id != 0 ? id : android.R.drawable.ic_menu_preferences;
    }

    // ---------- Control Centre (Apple-style: a compact floating cluster, top right) ----------

    private static final int CC_BIG = 0, CC_WIDE = 1, CC_ROUND = 2;

    private void buildControlCentre(FrameLayout outer) {
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        ccOverlay = new FrameLayout(this);
        ccOverlay.setVisibility(View.INVISIBLE);
        ccOverlay.setClipChildren(false);

        // The whole home screen, softly blurred: made once each time Control Centre opens.
        ccBlur = new ImageView(this);
        ccBlur.setScaleType(ImageView.ScaleType.FIT_XY);
        ccOverlay.addView(ccBlur, new FrameLayout.LayoutParams(-1, -1));
        ccScrim = new View(this);   // kept for older code paths; the darkening is baked into the blur
        ccOverlay.addView(ccScrim, new FrameLayout.LayoutParams(0, 0));

        ccFrame = new FrameLayout(this);
        ccFrame.setClipChildren(false);
        ccFrame.setClipToPadding(false);
        ccPanel = new LinearLayout(this);
        ccPanel.setOrientation(LinearLayout.VERTICAL);
        ccPanel.setClipChildren(false);
        ccPanel.setClipToPadding(false);

        int col = 124, gapDp = 8, full = col * 2 + gapDp;

        // Time and date
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(dp(6), 0, dp(6), dp(12));
        TextClock t = new UiStyle.FontClock(this);
        t.setFormat12Hour(clockFormat("h:mm a"));
        t.setFormat24Hour(clockFormat("HH:mm"));
        t.setTextSize(19);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(dp(6), 0, 0, 0x66000000);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextClock d = new UiStyle.FontClock(this);
        d.setFormat12Hour("EEE d MMM");
        d.setFormat24Hour("EEE d MMM");
        d.setTextSize(13);
        d.setTextColor(0xCCFFFFFF);
        d.setShadowLayer(dp(6), 0, 0, 0x66000000);
        head.addView(d);
        ccPanel.addView(head, new LinearLayout.LayoutParams(dp(full), -2));

        // StandBy (big) beside Wi-Fi and Bluetooth
        LinearLayout b1 = new LinearLayout(this);
        b1.setOrientation(LinearLayout.HORIZONTAL);
        b1.setClipChildren(false);
        View standby = ccBlock("ic_clock", "StandBy", "Standby clock", CC_BIG, false);
        ccFirst = standby;
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(col), dp(128));
        sl.setMargins(0, 0, dp(gapDp), 0);
        b1.addView(standby, sl);
        LinearLayout colRight = new LinearLayout(this);
        colRight.setOrientation(LinearLayout.VERTICAL);
        colRight.setClipChildren(false);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(dp(col), dp(60));
        wl.setMargins(0, 0, 0, dp(gapDp));
        colRight.addView(ccBlock("ic_wifi", "Wi-Fi", "Wi-Fi", CC_WIDE, true), wl);
        colRight.addView(ccBlock("ic_bluetooth", "Bluetooth", "Bluetooth", CC_WIDE, true),
                new LinearLayout.LayoutParams(dp(col), dp(60)));
        b1.addView(colRight);
        LinearLayout.LayoutParams b1l = new LinearLayout.LayoutParams(-2, -2);
        b1l.setMargins(0, 0, 0, dp(gapDp));
        ccPanel.addView(b1, b1l);

        // Your tiles (Settings → Control Centre): wide ones, then round ones
        ccTiles(ccPanel, full, col, gapDp);

        // Volume (shows what the remote's volume buttons do)
        LinearLayout vol = new LinearLayout(this);
        vol.setOrientation(LinearLayout.HORIZONTAL);
        vol.setGravity(Gravity.CENTER_VERTICAL);
        vol.setPadding(dp(16), 0, dp(18), 0);
        GradientDrawable vbg = new GradientDrawable();
        vbg.setCornerRadius(dp(20));
        vbg.setStroke(Math.max(1, dp(1)), 0x2EFFFFFF);
        ccBgs.add(vbg);
        vol.setBackground(vbg);
        ImageView vi = new ImageView(this);
        vi.setImageResource(drawable("ic_sound"));
        vi.setColorFilter(0xFFFFFFFF);
        vol.addView(vi, new LinearLayout.LayoutParams(dp(18), dp(18)));
        volBar = new VolBar(this);
        LinearLayout.LayoutParams vbl = new LinearLayout.LayoutParams(0, dp(16), 1f);
        vbl.setMargins(dp(12), 0, 0, 0);
        vol.addView(volBar, vbl);
        ccPanel.addView(vol, new LinearLayout.LayoutParams(dp(full), dp(40)));

        ccFrame.addView(ccPanel, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        plp.setMargins(0, dp(20), dp(40), 0);
        ccOverlay.addView(ccFrame, plp);
        buildNotes();
        outer.addView(ccOverlay, new FrameLayout.LayoutParams(-1, -1));
        applyCcTint();
    }

    /** One Control Centre tile: a big square, a wide pill (with an optional status line), or a round button. */
    private View ccBlock(String icon, String title, final String action, final int kind, boolean withSub) {
        final ViewGroup tile;
        final GradientDrawable bg = new GradientDrawable();
        bg.setStroke(Math.max(1, dp(1)), 0x2EFFFFFF);
        if (kind == CC_ROUND) bg.setShape(GradientDrawable.OVAL);
        else bg.setCornerRadius(dp(kind == CC_BIG ? 26 : 28));
        ccBgs.add(bg);

        final ImageView ic = new ImageView(this);
        ic.setImageResource(drawable(icon));
        ic.setColorFilter(0xFFFFFFFF);
        final TextView tv = kind == CC_ROUND ? null : new UiStyle.FontText(this);
        final TextView sub = withSub ? new UiStyle.FontText(this) : null;
        if (tv != null) {
            tv.setText(title);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (kind == CC_BIG) {
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER);
            v.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
            tv.setTextSize(15);
            tv.setPadding(0, dp(10), 0, 0);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            v.addView(tv, new LinearLayout.LayoutParams(-2, -2));
            tile = v;
        } else if (kind == CC_WIDE) {
            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);
            h.setPadding(dp(16), 0, dp(12), 0);
            h.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            tv.setTextSize(14);
            texts.addView(tv);
            if (sub != null) {
                sub.setTextSize(12);
                sub.setTextColor(0x99FFFFFF);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                sub.setVisibility(View.GONE);
                texts.addView(sub);
                ccSubs.put(title, sub);
            }
            h.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            tile = h;
        } else {
            FrameLayout f = new FrameLayout(this);
            f.addView(ic, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            tile = f;
        }
        tile.setBackground(bg);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(title);
        if (Build.VERSION.SDK_INT >= 26) tile.setDefaultFocusHighlightEnabled(false);

        final Runnable restyle = new Runnable() {
            @Override public void run() {
                boolean has = tile.isFocused();
                bg.setColor(has ? 0xFFFFFFFF : ccFill());
                int fg = has ? 0xFF111317 : 0xFFFFFFFF;
                ic.setColorFilter(fg);
                if (tv != null) tv.setTextColor(fg);
                if (sub != null) sub.setTextColor(has ? 0x99111317 : 0x99FFFFFF);
            }
        };
        ccRestyle.add(restyle);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                restyle.run();
                float up = kind == CC_ROUND ? 1.12f : 1.05f;
                v.animate().scaleX(has ? up : 1f).scaleY(has ? up : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(has ? 280 : 180)).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (action.equals("Voice search")) {
                    closeControlCentre();
                    openSearch();
                    startVoice();
                } else if (action.equals("More")) {
                    moreMenu();
                } else {
                    ccAction(action);
                }
            }
        });
        return tile;
    }

    /** The tiles' frosted colour, taken from the wallpaper so they belong to it (green photo, green glass). */
    private int ccFill() {
        if (UiStyle.panels == 1) return 0xE61C1D22;
        if (UiStyle.panels == 2) return 0xF5000000;
        return (0xC0 << 24) | (ccTint & 0xFFFFFF);
    }

    private void applyCcTint() {
        if (basePhoto != null) {
            try {
                Bitmap one = Bitmap.createScaledBitmap(basePhoto, 1, 1, true);
                int c = one.getPixel(0, 0);
                one.recycle();
                float[] hsv = new float[3];
                Color.colorToHSV(c, hsv);
                hsv[1] = Math.min(hsv[1], 0.55f);
                hsv[2] = Math.max(0.16f, Math.min(hsv[2] * 0.55f, 0.34f));
                ccTint = Color.HSVToColor(hsv);
            } catch (Throwable ignored) { }
        }
        for (GradientDrawable g : ccBgs) g.setColor(ccFill());
        for (Runnable rs : ccRestyle) rs.run();
    }

    /** "More": the rest of the tools, so the main view stays clean. */
    private void moreMenu() {
        final String[] items = {"Install apps", "Remap buttons", "Hidden apps", "Appearance", "Picture", "Sound", "Storage"};
        closeControlCentre();
        new AlertDialog.Builder(this)
                .setTitle("More")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { ccAction(items[which]); }
                })
                .show();
    }

    /** A small, blurred, slightly darkened picture of the home screen as it is right now. */
    private Drawable ccBackdrop() {
        try {
            int w = mainRoot.getWidth(), h = mainRoot.getHeight();
            if (w == 0 || h == 0) return null;
            int sw = Math.max(8, w / 10), sh = Math.max(8, h / 10);
            Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(sw / (float) w, sh / (float) h);
            mainRoot.draw(c);
            Glass.blur(b);
            new Canvas(b).drawColor(0x40000000);
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), b);
            d.setFilterBitmap(true);
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    private View ccTile(String icon, final String label) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(14), 0, dp(10), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0x1AFFFFFF);
        tile.setBackground(bg);
        tile.setFocusable(true);
        tile.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) tile.setDefaultFocusHighlightEnabled(false);

        final ImageView ic = new ImageView(this);
        ic.setImageResource(drawable(icon));
        ic.setColorFilter(0xFFFFFFFF);
        tile.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        final TextView tv = new UiStyle.FontText(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(0, dp(6), 0, 0);
        tile.addView(tv);
        final TextView sub = new UiStyle.FontText(this);
        sub.setTextSize(11);
        sub.setTextColor(0x99FFFFFF);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setVisibility(View.GONE);
        tile.addView(sub);
        ccSubs.put(label, sub);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFFFFFFFF : 0x1AFFFFFF);
                int fg = has ? 0xFF111317 : 0xFFFFFFFF;
                ic.setColorFilter(fg);
                tv.setTextColor(fg);
                sub.setTextColor(has ? 0x99111317 : 0x99FFFFFF);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(has ? 300 : 200)).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ccAction(label); }
        });
        return tile;
    }

    private View roundButton(String text, String icon, final Runnable action) {
        final FrameLayout b = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x1FFFFFFF);
        b.setBackground(bg);
        b.setFocusable(true);
        b.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) b.setDefaultFocusHighlightEnabled(false);
        final ImageView ic = icon != null ? new ImageView(this) : null;
        final TextView tv = text != null ? new UiStyle.FontText(this) : null;
        if (ic != null) {
            ic.setImageResource(drawable(icon));
            ic.setColorFilter(0xFFFFFFFF);
            b.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        }
        if (tv != null) {
            tv.setText(text);
            tv.setTextSize(22);
            tv.setTextColor(Color.WHITE);
            tv.setIncludeFontPadding(false);
            b.addView(tv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFFFFFFFF : 0x1FFFFFFF);
                int fg = has ? 0xFF111317 : 0xFFFFFFFF;
                if (ic != null) ic.setColorFilter(fg);
                if (tv != null) tv.setTextColor(fg);
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(250)).start();
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void ccAction(String label) {
        if (ccExtra(label)) return;
        if (label.equals("Wi-Fi")) {
            startOr(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } else if (label.equals("Bluetooth")) {
            // Google TV has no plain Bluetooth page: open "Pair remote or accessory" instead.
            try {
                startActivity(new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.accessories.AddAccessoryActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            } catch (Exception e) {
                android.util.Log.w("TVHome", "Remotes & Accessories blocked", e);
                try {
                    startActivity(new Intent("com.google.android.intent.action.CONNECT_INPUT")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                } catch (Exception e2) {
                    android.util.Log.w("TVHome", "CONNECT_INPUT blocked", e2);
                    Toast.makeText(this, "Bluetooth: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else if (label.equals("Picture")) {
            startOr(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        } else if (label.equals("Sound")) {
            startOr(new Intent(Settings.ACTION_SOUND_SETTINGS));
        } else if (label.equals("Apps")) {
            startOr(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        } else if (label.equals("Install apps")) {
            closeControlCentre();
            if (installer == null) installer = new Installer(this);
            installer.showMenu();
            return;
        } else if (label.equals("Appearance")) {
            closeControlCentre();
            openSettings();
            showPage(new Runnable() { @Override public void run() { pageAppearance(); } }, "Appearance");
            return;
        } else if (label.equals("TV Home settings")) {
            closeControlCentre();
            openSettings();
            return;
        } else if (label.equals("Storage")) {
            startOr(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        } else if (label.equals("Screen Mirror")) {
            App cast = null;
            for (App a : apps.values()) {
                String l = a.label.toLowerCase();
                if (l.contains("miracast") || l.contains("mirror") || l.contains("screen share")
                        || l.contains("screencast")) { cast = a; break; }
            }
            if (cast != null) launch(cast); else startOr(new Intent("android.settings.CAST_SETTINGS"));
        } else if (label.equals("Inputs")) {
            App in = null;
            for (App a : apps.values()) {
                String l = a.label.toLowerCase();
                if (l.contains("input") || l.contains("source") || l.contains("live tv")) { in = a; break; }
            }
            if (in != null) launch(in); else startOr(new Intent(Settings.ACTION_SETTINGS));
        } else if (label.equals("Screensaver")) {
            closeControlCentre();
            try {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                startOr(new Intent(Settings.ACTION_DREAM_SETTINGS));
            }
            return;
        } else if (label.equals("Standby clock")) {
            closeControlCentre();
            startActivity(new Intent(this, StandbyActivity.class));
            return;
        } else if (label.equals("Remap buttons")) {
            closeControlCentre();
            remapMenu();
            return;
        } else if (label.equals("Hidden apps")) {
            closeControlCentre();
            showHidden();
            return;
        } else {
            startOr(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void setSub(String label, String text) {
        TextView t = ccSubs.get(label);
        if (t == null) return;
        if (text == null || text.isEmpty()) { t.setVisibility(View.GONE); return; }
        t.setText(text);
        t.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void updateStatus() {
        // Network: Wi-Fi name and signal, or Ethernet.
        String net = "Not connected";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    net = "Ethernet";
                } else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    WifiInfo wi = wm.getConnectionInfo();
                    int level = WifiManager.calculateSignalLevel(wi.getRssi(), 4);
                    String[] q = {"Weak", "Fair", "Good", "Strong"};
                    String ssid = wi.getSSID();
                    if (ssid != null) ssid = ssid.replace("\"", "");
                    if (ssid == null || ssid.isEmpty() || ssid.contains("unknown ssid")) {
                        net = "Connected \u00B7 " + q[Math.max(0, Math.min(3, level))];
                    } else {
                        net = ssid + " \u00B7 " + q[Math.max(0, Math.min(3, level))];
                    }
                } else {
                    net = "Connected";
                }
            }
        } catch (Exception ignored) { }
        setSub("Wi-Fi", net.replace(" \u00B7 ", " \u00B7 "));
        try {
            android.bluetooth.BluetoothAdapter ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            setSub("Bluetooth", ba == null ? "" : ba.isEnabled() ? "On" : "Off");
        } catch (Throwable ignored) { }

        // Storage left on the TV.
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long free = fs.getAvailableBytes();
            long total = fs.getTotalBytes();
            setSub("Storage", formatGb(free) + " free of " + formatGb(total));
        } catch (Exception ignored) { }
    }

    private static String formatGb(long bytes) {
        double gb = bytes / 1e9;
        return gb >= 10 ? Math.round(gb) + " GB" : String.format(java.util.Locale.US, "%.1f GB", gb);
    }

    /** Opens a settings page, or the main Settings if this TV doesn't have that page. */
    private void startOr(Intent i) {
        try {
            // Fresh each time, or Android reopens whatever Settings page was left in the background.
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (Exception e) {
            start(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void volume(int direction) {
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        } catch (Exception ignored) { }
        updateVolume();
    }

    private void updateVolume() {
        if (audio == null || volBar == null) return;
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC);
        volBar.set(muted ? 0f : cur / (float) max);
    }

    private void openControlCentre() {
        if (ccOpen) return;
        ccOpen = true;
        updateStatus();
        updateVolume();
        fillNotes();
        applyCcTint();
        Drawable backdrop = ccBackdrop();
        ccBlur.setImageDrawable(backdrop);
        ccBlur.setBackgroundColor(backdrop == null ? 0x99000000 : 0);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ccOverlay.setVisibility(View.VISIBLE);

        // The home screen melts into a blur...
        ccBlur.animate().cancel();
        ccBlur.setAlpha(0f);
        ccBlur.animate().alpha(1f).setInterpolator(EASE).setDuration(UiStyle.ms(300)).withEndAction(new Runnable() {
            @Override public void run() {
                // Nothing needs drawing behind an opaque blur, so give the graphics chip a rest.
                if (ccOpen && ccBlur.getDrawable() != null) mainRoot.setVisibility(View.INVISIBLE);
            }
        }).start();

        // ...and the tiles drop in from the top-right corner and settle.
        ccFrame.animate().cancel();
        ccFrame.setAlpha(0f);
        ccFrame.setTranslationX(dp(24));
        ccFrame.setTranslationY(-dp(16));
        ccFrame.setScaleX(0.94f);
        ccFrame.setScaleY(0.94f);
        ccFrame.setPivotX(ccFrame.getWidth() > 0 ? ccFrame.getWidth() : dp(256));
        ccFrame.setPivotY(0);
        ccFrame.animate().alpha(1f).translationX(0).translationY(0).scaleX(1f).scaleY(1f)
                .setStartDelay(40).setInterpolator(GLIDE).setDuration(UiStyle.ms(480)).withLayer().start();
        for (int i = 0; i < ccPanel.getChildCount(); i++) {
            View c = ccPanel.getChildAt(i);
            c.animate().cancel();
            c.setAlpha(0f);
            c.setTranslationY(-dp(10));
            c.animate().alpha(1f).translationY(0).setStartDelay(90 + i * 45L)
                    .setInterpolator(GLIDE).setDuration(UiStyle.ms(420)).start();
        }
        ccPanel.postDelayed(new Runnable() {
            @Override public void run() { if (ccOpen && ccFirst != null) ccFirst.requestFocus(); }
        }, 160);
    }

    private void closeControlCentre() {
        if (!ccOpen) return;
        ccOpen = false;
        mainRoot.setVisibility(View.VISIBLE);
        ccBlur.animate().cancel();
        ccBlur.animate().alpha(0f).setInterpolator(EASE).setDuration(UiStyle.ms(260)).start();
        ccFrame.animate().cancel();
        ccFrame.animate().alpha(0f).translationX(dp(16)).translationY(-dp(8)).scaleX(0.97f).scaleY(0.97f)
                .setStartDelay(0).setInterpolator(EASE).setDuration(UiStyle.ms(220)).withLayer()
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!ccOpen) {
                            ccOverlay.setVisibility(View.INVISIBLE);
                            ccBlur.setImageDrawable(null);
                        }
                    }
                }).start();
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (pillView != null) pillView.requestFocus();
    }

    // ================================================================== 6.20 features

    private static boolean isInside(View v, View container) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == container) return true;
        return false;
    }

    /** A titled row in the main list (Continue Watching, Recent). Hidden until it has something. */
    private LinearLayout section(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setVisibility(View.GONE);
        box.setPadding(0, 0, 0, dp(6));
        TextView label = new UiStyle.FontText(this);
        label.setText(title);
        label.setTextSize(14);
        label.setTextColor(0xE6FFFFFF);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setPadding(dp(gap / 2), 0, 0, dp(2));
        box.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        box.addView(row);
        return box;
    }

    // ---------- Recent apps ----------

    private List<String> recents() {
        List<String> r = new ArrayList<>();
        for (String p : prefs.getString("recents", "").split("\n")) if (!p.isEmpty()) r.add(p);
        return r;
    }

    private void addRecent(String pkg) {
        List<String> r = recents();
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > 10) r.remove(r.size() - 1);
        prefs.edit().putString("recents", TextUtils.join("\n", r)).apply();
    }

    /** Recent apps use smaller cards (about two-thirds size) so the row stays light. */
    private void smallCard(View shell) {
        if (!(shell instanceof LinearLayout)) return;
        LinearLayout cell = (LinearLayout) shell;
        int w = Math.round(dp(tileW) * 0.68f), h = Math.round(dp(tileH) * 0.68f);
        cell.setPadding(dp(gap / 2) * 2 / 3, dp(8), dp(gap / 2) * 2 / 3, dp(2));
        for (int i = 0; i < cell.getChildCount(); i++) {
            View ch = cell.getChildAt(i);
            ViewGroup.LayoutParams lp = ch.getLayoutParams();
            lp.width = w;
            if (i == 0) lp.height = h; else lp.height = dp(24);
            ch.setLayoutParams(lp);
        }
    }

    private void renderRecents() {
        if (recentRow == null) return;
        recentRow.removeAllViews();
        Set<String> hide = hidden();
        int n = 0;
        for (String p : recents()) {
            App a = apps.get(p);
            if (a == null || hide.contains(p) || a.inputId != null) continue;
            View c = cell(a, false, "r:");
            smallCard(c);
            recentRow.addView(c);
            if (++n >= COLS + 2) break;
        }
        recentSection.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        muteClicks(recentRow);
    }

    // ---------- Continue Watching (Android TV's Watch Next list) ----------

    static class Wn {
        long id;
        String title, pkg, poster, intent;
        long pos, dur, when;
        Bitmap art;
    }

    private boolean watchNextAllowed() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission("com.android.providers.tv.permission.READ_EPG_DATA") == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission("android.permission.READ_TV_LISTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    private void askWatchNextOnce() {
        if (Build.VERSION.SDK_INT < 26 || watchNextAllowed() || !prefs.contains("user_name")
                || prefs.getBoolean("epg_asked", false)) return;
        prefs.edit().putBoolean("epg_asked", true).apply();
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    requestPermissions(new String[]{"com.android.providers.tv.permission.READ_EPG_DATA",
                            "android.permission.READ_TV_LISTINGS"}, REQ_EPG);
                } catch (Exception ignored) { }
            }
        }, 1500);
    }

    private void loadWatchNext() {
        if (Build.VERSION.SDK_INT < 26 || cwRow == null) return;
        final int artW = dp(tileW), artH = dp(tileH);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Wn> list = new ArrayList<>();
                android.database.Cursor c = null;
                try {
                    c = getContentResolver().query(android.media.tv.TvContract.WatchNextPrograms.CONTENT_URI,
                            null, null, null, null);
                    while (c != null && c.moveToNext()) {
                        Wn w = new Wn();
                        w.id = lng(c, "_id");
                        w.title = str(c, "title");
                        w.pkg = str(c, "package_name");
                        w.poster = str(c, "poster_art_uri");
                        if (w.poster == null) w.poster = str(c, "thumbnail_uri");
                        w.intent = str(c, "intent_uri");
                        w.pos = lng(c, "last_playback_position_millis");
                        w.dur = lng(c, "duration_millis");
                        w.when = lng(c, "last_engagement_time_utc_millis");
                        int bi = c.getColumnIndex("browsable");
                        if (bi >= 0 && c.getInt(bi) == 0) continue;
                        if (w.title == null || w.title.isEmpty()) continue;
                        list.add(w);
                    }
                } catch (Throwable ignored) {
                    // Not allowed on this TV, or nothing shared: the row just stays hidden.
                } finally {
                    if (c != null) c.close();
                }
                Collections.sort(list, new Comparator<Wn>() {
                    @Override public int compare(Wn x, Wn y) { return Long.compare(y.when, x.when); }
                });
                while (list.size() > COLS) list.remove(list.size() - 1);
                for (Wn w : list) w.art = loadPoster(w.poster, artW, artH);
                ui.post(new Runnable() { @Override public void run() { showWatchNext(list); } });
            }
        }).start();
    }

    private static String str(android.database.Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? null : c.getString(i);
    }

    private static long lng(android.database.Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }

    private Bitmap loadPoster(String uri, int w, int h) {
        if (uri == null) return null;
        java.io.InputStream in = null;
        try {
            Uri u = Uri.parse(uri);
            String sc = u.getScheme() == null ? "" : u.getScheme();
            if (sc.startsWith("http")) {
                HttpURLConnection con = (HttpURLConnection) new URL(uri).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(10000);
                in = con.getInputStream();
            } else {
                in = getContentResolver().openInputStream(u);
            }
            byte[] data = readAll(in);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > 8 * 1024 * 1024) break;
        }
        return b.toByteArray();
    }

    private void showWatchNext(List<Wn> list) {
        allWatchNext = new ArrayList<>(list);
        cwShownFor = null;
        cwRow.removeAllViews();
        for (Wn w : list) cwRow.addView(watchCell(w));
        cwSection.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        muteClicks(cwRow);
        updateUpLinks();
    }

    private View watchCell(final Wn w) {
        FrameLayout t = baseTile(0xE62B2F38);
        if (w.art != null) {
            ImageView iv = new ImageView(this);
            Bitmap r = roundedArt(new android.graphics.drawable.BitmapDrawable(getResources(), w.art),
                    dp(tileW), dp(tileH), dp(14));
            if (r != null) iv.setImageBitmap(r); else iv.setImageBitmap(w.art);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView tt = new UiStyle.FontText(this);
            tt.setText(w.title);
            tt.setTextColor(Color.WHITE);
            tt.setTextSize(14);
            tt.setGravity(Gravity.CENTER);
            tt.setPadding(dp(12), 0, dp(12), 0);
            tt.setMaxLines(3);
            t.addView(tt, new FrameLayout.LayoutParams(-1, -1));
        }
        if (w.dur > 0 && w.pos > 0) {
            float f = Math.max(0.03f, Math.min(1f, w.pos / (float) w.dur));
            FrameLayout bar = new FrameLayout(this);
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(dp(2));
            track.setColor(0x59FFFFFF);
            bar.setBackground(track);
            View fill = new View(this);
            GradientDrawable fb = new GradientDrawable();
            fb.setCornerRadius(dp(2));
            fb.setColor(0xFFFFFFFF);
            fill.setBackground(fb);
            int barW = dp(tileW) - dp(24);
            bar.addView(fill, new FrameLayout.LayoutParams(Math.round(barW * f), -1));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(barW, dp(4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            bl.setMargins(0, 0, 0, dp(9));
            t.addView(bar, bl);
        }
        TextView label = nameLabel(w.title);
        App owner = w.pkg == null ? null : apps.get(w.pkg);
        App ta = owner;
        if (ta == null) {
            ta = new App();
            ta.pkg = "wn:" + w.pkg;
            ta.label = w.title;
        }
        Tag tag = new Tag(ta, false, "w:" + w.id, label);
        tag.inner = t.getChildAt(0);
        addOverlays(t, tag);
        t.setTag(tag);
        t.setContentDescription(w.title);
        final App own = owner;
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                press(v, new Runnable() {
                    @Override public void run() {
                        try {
                            Intent i = Intent.parseUri(w.intent, Intent.URI_INTENT_SCHEME);
                            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), openOptions(v));
                            if (own != null) addRecent(own.pkg);
                        } catch (Exception e) {
                            if (own != null) launch(own);
                        }
                    }
                });
            }
        });
        return cellShell(t, label);
    }

    // ---------- Folders ----------

    private Map<String, List<String>> folders() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (String line : prefs.getString("folders", "").split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            List<String> l = new ArrayList<>();
            for (String p : line.substring(tab + 1).split(",")) if (!p.isEmpty()) l.add(p);
            m.put(line.substring(0, tab), l);
        }
        return m;
    }

    private void saveFolders(Map<String, List<String>> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append(e.getKey().replace("\t", " ").replace("\n", " ")).append('\t')
                    .append(TextUtils.join(",", e.getValue())).append('\n');
        }
        prefs.edit().putString("folders", sb.toString()).apply();
    }

    private View folderCell(final String name, List<App> members) {
        FrameLayout t = baseTile(0xE62B2F38);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER);
        int ic = dp(tileH / 3);
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int k = 0; k < 2; k++) {
                int idx = r * 2 + k;
                ImageView iv = new ImageView(this);
                if (idx < members.size()) iv.setImageDrawable(members.get(idx).icon);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ic, ic);
                lp.setMargins(dp(4), dp(3), dp(4), dp(3));
                line.addView(iv, lp);
            }
            grid.addView(line);
        }
        t.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        TextView label = nameLabel(name);
        App fa = new App();
        fa.pkg = "folder:" + name;
        fa.label = name;
        fa.icon = getDrawable(drawable("ic_folder"));
        Tag tag = new Tag(fa, false, "a:folder:" + name, label);
        tag.inner = t.getChildAt(0);
        addOverlays(t, tag);
        t.setTag(tag);
        t.setContentDescription("Folder " + name);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                press(v, new Runnable() { @Override public void run() { openFolderPanel(name); } });
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { folderOptions(name); return true; }
        });
        return cellShell(t, label);
    }

    private void addToFolder(final App a) {
        final Map<String, List<String>> m = folders();
        final List<String> names = new ArrayList<>(m.keySet());
        List<String> items = new ArrayList<>(names);
        items.add("New folder…");
        new AlertDialog.Builder(this)
                .setTitle("Add " + a.label + " to")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which < names.size()) {
                            putInFolder(a.pkg, names.get(which));
                        } else {
                            askText("New folder", "Streaming", "", new TextResult() {
                                @Override public void done(String text) { putInFolder(a.pkg, text); }
                            });
                        }
                    }
                })
                .show();
    }

    private void putInFolder(String pkg, String folder) {
        Map<String, List<String>> m = folders();
        for (List<String> l : m.values()) l.remove(pkg);
        List<String> l = m.get(folder);
        if (l == null) { l = new ArrayList<>(); m.put(folder, l); }
        l.add(pkg);
        saveFolders(m);
        focusKey = "a:folder:" + folder;
        render();
        Toast.makeText(this, "Added to " + folder, Toast.LENGTH_SHORT).show();
    }

    private void removeFromFolder(String pkg) {
        Map<String, List<String>> m = folders();
        for (List<String> l : m.values()) l.remove(pkg);
        saveFolders(m);
        String f = openFolder;
        closeFolderPanel(false);
        render();
        if (f != null && folders().containsKey(f)) openFolderPanel(f);
    }

    private void folderOptions(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"Rename folder", "Remove folder (apps go back to All apps)"},
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                final Map<String, List<String>> m = folders();
                                if (which == 0) {
                                    askText("Rename folder", name, name, new TextResult() {
                                        @Override public void done(String text) {
                                            Map<String, List<String>> out = new LinkedHashMap<>();
                                            for (Map.Entry<String, List<String>> e : m.entrySet()) {
                                                out.put(e.getKey().equals(name) ? text : e.getKey(), e.getValue());
                                            }
                                            saveFolders(out);
                                            focusKey = "a:folder:" + text;
                                            render();
                                        }
                                    });
                                } else {
                                    m.remove(name);
                                    saveFolders(m);
                                    focusKey = null;
                                    render();
                                }
                            }
                        })
                .show();
    }

    interface TextResult { void done(String text); }

    private void askText(String title, String hint, String value, final TextResult result) {
        final EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setSelectAllOnFocus(true);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String t = field.getText().toString().trim();
                        if (!t.isEmpty()) result.done(t);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildFolderOverlay(FrameLayout outer) {
        folderOverlay = new FrameLayout(this);
        folderOverlay.setVisibility(View.GONE);
        folderOverlay.setBackgroundColor(0x99000000);
        folderPanel = new LinearLayout(this);
        folderPanel.setOrientation(LinearLayout.VERTICAL);
        folderPanel.setClipChildren(false);
        folderPanel.setClipToPadding(false);
        folderPanel.setPadding(dp(28), dp(22), dp(28), dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        bg.setColor(0xF0171A21);
        folderPanel.setBackground(bg);
        folderTitle = new UiStyle.FontText(this);
        folderTitle.setTextSize(22);
        folderTitle.setTextColor(Color.WHITE);
        folderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        folderTitle.setPadding(dp(gap / 2), 0, 0, dp(10));
        folderPanel.addView(folderTitle);
        folderScroll = new ScrollView(this);
        folderScroll.setVerticalScrollBarEnabled(false);
        folderScroll.setClipChildren(false);
        folderScroll.setClipToPadding(false);
        folderGrid = new LinearLayout(this);
        folderGrid.setOrientation(LinearLayout.VERTICAL);
        folderGrid.setClipChildren(false);
        folderGrid.setClipToPadding(false);
        folderGrid.setPadding(0, dp(6), 0, dp(6));
        folderScroll.addView(folderGrid, new ViewGroup.LayoutParams(-1, -2));
        folderPanel.addView(folderScroll, new LinearLayout.LayoutParams(-2, -2));
        folderOverlay.addView(folderPanel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        outer.addView(folderOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openFolderPanel(String name) {
        List<String> pk = folders().get(name);
        if (pk == null) return;
        openFolder = name;
        folderTitle.setText(name);
        folderGrid.removeAllViews();
        Set<String> hide = hidden();
        LinearLayout row = null;
        int n = 0, per = 4;
        for (String p : pk) {
            App a = apps.get(p);
            if (a == null || hide.contains(p)) continue;
            if (n % per == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                folderGrid.addView(row);
            }
            row.addView(cell(a, false, "d:"));
            n++;
        }
        int rows = (n + per - 1) / per;
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        folderScroll.getLayoutParams().height = rows > 2 ? maxH : ViewGroup.LayoutParams.WRAP_CONTENT;
        muteClicks(folderOverlay);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        folderOverlay.setVisibility(View.VISIBLE);
        folderOverlay.setAlpha(0f);
        folderOverlay.animate().alpha(1f).setDuration(UiStyle.ms(220)).start();
        folderPanel.setScaleX(0.94f);
        folderPanel.setScaleY(0.94f);
        folderPanel.animate().scaleX(1f).scaleY(1f).setInterpolator(GLIDE).setDuration(UiStyle.ms(420)).start();
        folderScroll.scrollTo(0, 0);
        folderPanel.post(new Runnable() {
            @Override public void run() {
                int[] loc = new int[2];
                folderPanel.getLocationOnScreen(loc);
                Drawable g = glassFor("folder:" + folderPanel.getWidth() + "x" + folderPanel.getHeight(),
                        loc[0], loc[1], folderPanel.getWidth(), folderPanel.getHeight(),
                        new float[]{dp(28), dp(28), dp(28), dp(28)});
                if (g != null) folderPanel.setBackground(g);
                if (folderGrid.getChildCount() > 0) {
                    View first = ((ViewGroup) ((ViewGroup) folderGrid.getChildAt(0)).getChildAt(0)).getChildAt(0);
                    first.requestFocus();
                }
            }
        });
    }

    private void closeFolderPanel(boolean focusTile) {
        if (openFolder == null) return;
        String name = openFolder;
        openFolder = null;
        folderOverlay.animate().cancel();
        folderOverlay.setVisibility(View.GONE);
        folderGrid.removeAllViews();
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (focusTile) {
            View t = findByKey(new ViewGroup[]{allGrid}, "a:folder:" + name);
            if (t != null) t.requestFocus();
        }
    }

    // ---------- Glass ----------

    /** A frosted panel background cut from the current wallpaper. Made once per wallpaper. */
    private Drawable glassFor(String key, int x, int y, int w, int h, float[] radii) {
        if (basePhoto == null) return null;
        if (glassPhoto != basePhoto) { glassCache.clear(); glassPhoto = basePhoto; }
        String k = key + "@" + x + "," + y;
        Drawable d = glassCache.get(k);
        if (d == null) {
            d = Glass.make(getResources(), basePhoto, getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels, x, y, w, h, radii);
            if (d != null) glassCache.put(k, d);
        }
        return d;
    }

    // ---------- Icon packs ----------

    private void chooseIconPack() {
        final Map<String, String> packs = IconPack.installed(pm);
        final List<String> pkgs = new ArrayList<>(packs.keySet());
        List<String> names = new ArrayList<>();
        String cur = prefs.getString("icon_pack", null);
        names.add((cur == null ? "✓  " : "") + "None (the apps' own icons)");
        for (String p : pkgs) names.add((p.equals(cur) ? "✓  " : "") + packs.get(p));
        if (pkgs.isEmpty()) names.add("No icon packs installed. Install one (e.g. from Aurora Store) and come back.");
        new AlertDialog.Builder(this)
                .setTitle("Icon pack for the dock")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            prefs.edit().remove("icon_pack").apply();
                            iconPack = null;
                            render();
                        } else if (which - 1 < pkgs.size()) {
                            String p = pkgs.get(which - 1);
                            prefs.edit().putString("icon_pack", p).apply();
                            Toast.makeText(MainActivity.this, "Loading " + packs.get(p) + "…", Toast.LENGTH_SHORT).show();
                            loadIconPack(p);
                        }
                    }
                })
                .show();
    }

    private void loadIconPack(final String pkg) {
        new Thread(new Runnable() {
            @Override public void run() {
                IconPack p = null;
                try { p = new IconPack(pm, pkg); } catch (Throwable ignored) { }
                final IconPack fp = p;
                ui.post(new Runnable() {
                    @Override public void run() {
                        iconPack = fp;
                        if (fp == null) {
                            prefs.edit().remove("icon_pack").apply();
                            Toast.makeText(MainActivity.this, "Couldn't read that icon pack", Toast.LENGTH_SHORT).show();
                        }
                        render();
                    }
                });
            }
        }).start();
    }

    // ---------- Send from Phone ----------

    private void sendFromPhone() {
        if (phoneUpload != null) phoneUpload.stop();
        final AlertDialog[] dlg = new AlertDialog[1];
        final boolean[] got = new boolean[1];
        phoneUpload = new PhoneUpload(Wallpapers.phoneFile(this), new PhoneUpload.Callback() {
            @Override public void onPhoto(File photo) {
                got[0] = true;
                if (dlg[0] != null) dlg[0].dismiss();
                phoneUpload = null;
                wallpapers.setMode(Wallpapers.MODE_PHONE);
                wallpapers.refresh(true);
                Toast.makeText(MainActivity.this, "Got it! Setting your photo as the wallpaper.", Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        String url = phoneUpload.start();
        if (url == null) {
            phoneUpload = null;
            Toast.makeText(this, "Connect the TV to Wi-Fi first, then try again.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView how = new UiStyle.FontText(this);
        how.setText("On your phone (on the same Wi-Fi), open this in the browser:");
        how.setTextSize(15);
        box.addView(how);
        TextView link = new UiStyle.FontText(this);
        link.setText(url.replace("http://", ""));
        link.setTextSize(26);
        link.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        link.setPadding(0, dp(12), 0, dp(12));
        box.addView(link);
        TextView note = new UiStyle.FontText(this);
        note.setText("Pick a photo and it becomes your wallpaper. The link works once, for 10 minutes.");
        note.setTextSize(13);
        note.setAlpha(0.75f);
        box.addView(note);
        dlg[0] = new AlertDialog.Builder(this)
                .setTitle("Send a photo from your phone")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        dlg[0].setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                if (!got[0] && phoneUpload != null) { phoneUpload.stop(); phoneUpload = null; }
            }
        });
        dlg[0].show();
    }

    // ---------- Button remapping ----------

    private boolean remapEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
        return s != null && s.contains(getPackageName() + "/");
    }

    private List<Integer> remapKeys() {
        List<Integer> l = new ArrayList<>();
        for (String k : prefs.getString("remap_keys", "").split(",")) {
            try { if (!k.isEmpty()) l.add(Integer.parseInt(k)); } catch (Exception ignored) { }
        }
        return l;
    }

    private void saveRemapKeys(List<Integer> l) {
        prefs.edit().putString("remap_keys", TextUtils.join(",", l)).apply();
    }

    private static String keyName(int k) {
        String n = KeyEvent.keyCodeToString(k);
        if (n.startsWith("KEYCODE_")) n = n.substring(8);
        if (n.matches("\\d+")) return "Button " + n;
        StringBuilder sb = new StringBuilder();
        for (String w : n.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase(java.util.Locale.US));
        }
        return sb.toString();
    }

    private String targetLabel(String pkg) {
        App a = apps.get(pkg);
        return a != null ? a.label : pkg;
    }

    private void remapMenu() {
        if (!remapEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Turn on button remapping")
                    .setMessage("TV Home needs accessibility access to see your remote's buttons. It only acts on the "
                            + "buttons you choose and can't see the screen.\n\nTurn on \"TV Home button remapping\" in "
                            + "Settings → Accessibility.\n\nIf it isn't listed there, run this from your computer:\n"
                            + "adb shell settings put secure enabled_accessibility_services "
                            + getPackageName() + "/" + RemapService.class.getName()
                            + "\nadb shell settings put secure accessibility_enabled 1")
                    .setPositiveButton("Open Accessibility", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            startOr(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        final List<Integer> keys = remapKeys();
        List<String> items = new ArrayList<>();
        for (int k : keys) items.add(keyName(k) + "  →  " + targetLabel(prefs.getString("remap_" + k, "")));
        items.add("+  Remap a button");
        new AlertDialog.Builder(this)
                .setTitle("Remap buttons")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == keys.size()) { captureKey(); return; }
                        final int k = keys.get(which);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(keyName(k))
                                .setItems(new String[]{"Change what it opens", "Back to normal"},
                                        new DialogInterface.OnClickListener() {
                                            @Override public void onClick(DialogInterface d2, int w) {
                                                if (w == 0) chooseRemapTarget(k);
                                                else {
                                                    keys.remove(Integer.valueOf(k));
                                                    saveRemapKeys(keys);
                                                    prefs.edit().remove("remap_" + k).apply();
                                                }
                                            }
                                        })
                                .show();
                    }
                })
                .show();
    }

    private void captureKey() {
        prefs.edit().remove("remap_captured").putBoolean("remap_capture", true).apply();
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Press a button on your remote")
                .setMessage("Press the button you want to change, like the Netflix or Prime Video button.\n\n"
                        + "Arrows, OK, Back, Home, volume and power always keep their normal job.")
                .setNegativeButton("Cancel", null)
                .create();
        remapListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                if (!"remap_captured".equals(key) || !sp.contains("remap_captured")) return;
                final int k = sp.getInt("remap_captured", -1);
                sp.unregisterOnSharedPreferenceChangeListener(this);
                remapListener = null;
                ui.post(new Runnable() {
                    @Override public void run() {
                        d.dismiss();
                        if (k > 0) chooseRemapTarget(k);
                    }
                });
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(remapListener);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface di) {
                prefs.edit().putBoolean("remap_capture", false).apply();
                if (remapListener != null) {
                    prefs.unregisterOnSharedPreferenceChangeListener(remapListener);
                    remapListener = null;
                }
            }
        });
        d.show();
    }

    private void chooseRemapTarget(final int k) {
        final List<App> targets = new ArrayList<>();
        for (String p : favs()) { App a = apps.get(p); if (a != null) targets.add(a); }
        List<App> rest = new ArrayList<>(apps.values());
        Collections.sort(rest, new Comparator<App>() {
            @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        for (App a : rest) if (!targets.contains(a)) targets.add(a);
        String[] names = new String[targets.size()];
        for (int i = 0; i < names.length; i++) names[i] = targets.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(keyName(k) + " opens…")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        App a = targets.get(which);
                        prefs.edit().putString("remap_" + k, a.pkg).apply();
                        List<Integer> keys = remapKeys();
                        if (!keys.contains(k)) keys.add(k);
                        saveRemapKeys(keys);
                        Toast.makeText(MainActivity.this, keyName(k) + " now opens " + a.label, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }


    // ================================================================== 6.22: Settings, icon styles

    private App settingsApp() {
        if (settingsApp == null) {
            settingsApp = new App();
            settingsApp.pkg = "tvhome:settings";
            settingsApp.label = "Settings";
            settingsApp.icon = new android.graphics.drawable.BitmapDrawable(getResources(), Art.gearTile(dp(96)));
            settingsApp.glow1 = 0xFF5C6370;
            settingsApp.glow2 = 0xFF3A3F48;
            settingsApp.colorsReady = true;
        }
        return settingsApp;
    }

    private boolean isSettingsCell(View cell) {
        if (!(cell instanceof ViewGroup) || ((ViewGroup) cell).getChildCount() == 0) return false;
        Object t = ((ViewGroup) cell).getChildAt(0).getTag();
        return t instanceof Tag && ((Tag) t).app != null && "tvhome:settings".equals(((Tag) t).app.pkg);
    }

    /** Clips a finished square of art to the dock's rounded shape. */
    private static Bitmap roundOff(Bitmap art, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = size * 0.23f;
        oc.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
        art.recycle();
        return out;
    }

    static final String[] ICON_STYLES = {"Original", "Light (Apple TV)", "Dark", "Glass"};

    /**
     * Icon styles: each app's own logo on a uniform tile, so the dock looks like one set.
     * If a logo would vanish on the tile (white on Light, black on Dark), that app keeps its own colours.
     */
    private boolean styledIcon(Canvas c, App a, Drawable d, boolean adaptive, Drawable sharp, int size, int style) {
        int tile = style == 1 ? 0xFFF5F6F8 : style == 2 ? 0xFF1C1E24 : 0x47FFFFFF;
        Drawable glyph = null;
        boolean full = false;
        if (adaptive) {
            glyph = ((android.graphics.drawable.AdaptiveIconDrawable) d).getForeground();
            full = true;
        } else if (sharp != null) {
            Drawable.ConstantState cs = sharp.getConstantState();
            glyph = cs != null ? cs.newDrawable(getResources()).mutate() : sharp;
        } else if (a.banner == null && a.icon != null) {
            glyph = a.icon;
        }
        if (glyph == null) return false;
        float lum = brightness(glyph);
        if ((style == 1 && lum > 0.82f) || (style == 2 && lum < 0.18f)) return false;
        c.drawColor(tile);
        if (style == 3) {
            Paint sheen = new Paint();
            sheen.setShader(new LinearGradient(0, 0, 0, size * 0.55f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, size, size * 0.55f, sheen);
        }
        if (full) {
            int ext = size / 4;
            glyph.setBounds(-ext, -ext, size + ext, size + ext);
        } else {
            int iw = Math.round(size * 0.66f);
            glyph.setBounds((size - iw) / 2, (size - iw) / 2, (size + iw) / 2, (size + iw) / 2);
        }
        glyph.draw(c);
        return true;
    }

    /** How light a logo's visible pixels are, 0 (black) to 1 (white). */
    private static float brightness(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(-6, -6, 30, 30);
            d.draw(c);
            double sum = 0, n = 0;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                int px = b.getPixel(x, y);
                int al = Color.alpha(px);
                if (al < 100) continue;
                sum += (0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)) / 255.0;
                n++;
            }
            b.recycle();
            return n == 0 ? 0.5f : (float) (sum / n);
        } catch (Throwable t) {
            return 0.5f;
        }
    }

    // ---------- Settings screen (Classic style or Glass style) ----------

    private boolean glassSettings() { return "glass".equals(prefs.getString("settings_style", "classic")); }

    private void buildSettings(FrameLayout outer) {
        settingsOverlay = new FrameLayout(this);
        settingsOverlay.setVisibility(View.GONE);
        settingsBg = new ImageView(this);
        settingsBg.setScaleType(ImageView.ScaleType.FIT_XY);
        settingsOverlay.addView(settingsBg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        ImageView gear = new ImageView(this);
        gear.setImageBitmap(Art.gear(dp(150)));
        left.addView(gear, new LinearLayout.LayoutParams(dp(120), dp(120)));
        TextView big = new UiStyle.FontText(this);
        big.setText("Settings");
        big.setTextSize(38);
        big.setTextColor(Color.WHITE);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        big.setPadding(0, dp(26), 0, dp(4));
        big.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(big, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = new UiStyle.FontText(this);
        sub.setText("TV Home");
        sub.setTextSize(16);
        sub.setTextColor(0x99FFFFFF);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(sub, new LinearLayout.LayoutParams(-2, -2));
        split.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(0, dp(40), dp(56), dp(24));
        right.setClipChildren(false);
        right.setClipToPadding(false);
        settingsTitle = new UiStyle.FontText(this);
        settingsTitle.setTextSize(20);
        settingsTitle.setTextColor(Color.WHITE);
        settingsTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        settingsTitle.setPadding(dp(20), 0, 0, dp(14));
        right.addView(settingsTitle);
        settingsScroll = new ScrollView(this);
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.setClipChildren(false);
        settingsScroll.setClipToPadding(false);
        settingsScroll.setPadding(dp(8), dp(4), dp(8), dp(24));
        settingsList = new LinearLayout(this);
        settingsList.setOrientation(LinearLayout.VERTICAL);
        settingsList.setClipChildren(false);
        settingsList.setClipToPadding(false);
        settingsScroll.addView(settingsList, new ViewGroup.LayoutParams(-1, -2));
        right.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        split.addView(right, new LinearLayout.LayoutParams(0, -1, 1.25f));
        settingsOverlay.addView(split, new FrameLayout.LayoutParams(-1, -1));
        outer.addView(settingsOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openSettings() {
        if (!settingsOpen) {
            settingsOpen = true;
            if (glassSettings()) {
                settingsBg.setImageDrawable(ccBackdrop());
                settingsOverlay.setBackgroundColor(0);
            } else {
                settingsBg.setImageDrawable(null);
                settingsOverlay.setBackgroundColor(UiStyle.panels == 2 ? 0xFF000000 : 0xFF0B0B0F);
            }
            mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            settingsOverlay.setVisibility(View.VISIBLE);
            settingsOverlay.setAlpha(0f);
            settingsOverlay.animate().alpha(1f).setInterpolator(EASE).setDuration(UiStyle.ms(260)).withEndAction(new Runnable() {
                @Override public void run() { if (settingsOpen) mainRoot.setVisibility(View.INVISIBLE); }
            }).start();
        }
        pageStack.clear();
        showPage(new Runnable() { @Override public void run() { pageMain(); } }, "");
    }

    private void closeSettings(boolean focusDock) {
        if (!settingsOpen) return;
        settingsOpen = false;
        pageStack.clear();
        mainRoot.setVisibility(View.VISIBLE);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        settingsOverlay.animate().cancel();
        settingsOverlay.animate().alpha(0f).setDuration(UiStyle.ms(200)).withEndAction(new Runnable() {
            @Override public void run() {
                if (!settingsOpen) { settingsOverlay.setVisibility(View.GONE); settingsBg.setImageDrawable(null); }
            }
        }).start();
        if (focusDock) {
            View f = findByKey(new ViewGroup[]{favRow}, "f:tvhome:settings");
            if (f != null) f.requestFocus(); else if (pillView != null) pillView.requestFocus();
        }
    }

    private void showPage(Runnable page, String title) {
        pageStack.add(new Object[]{page, title, 0});
        renderPage(0);
    }

    private void settingsBack() {
        if (pageStack.size() <= 1) { closeSettings(true); return; }
        pageStack.remove(pageStack.size() - 1);
        renderPage((Integer) pageStack.get(pageStack.size() - 1)[2]);
    }

    /** Rebuilds the current page (to show new values) keeping the selection on the same row. */
    private void refreshPage() {
        int idx = 0;
        View f = getCurrentFocus();
        if (f != null && f.getParent() == settingsList) idx = settingsList.indexOfChild(f);
        renderPage(idx);
    }

    private void renderPage(int focusIndex) {
        if (pageStack.isEmpty()) return;
        Object[] top = pageStack.get(pageStack.size() - 1);
        String title = (String) top[1];
        settingsTitle.setText(title);
        settingsTitle.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);
        settingsList.removeAllViews();
        ((Runnable) top[0]).run();
        muteClicks(settingsList);
        settingsList.setAlpha(0f);
        settingsList.setTranslationX(dp(24));
        settingsList.animate().alpha(1f).translationX(0).setInterpolator(GLIDE).setDuration(UiStyle.ms(320)).start();
        final int fi = Math.max(0, Math.min(focusIndex, settingsList.getChildCount() - 1));
        settingsList.post(new Runnable() {
            @Override public void run() {
                if (settingsList.getChildCount() > 0) settingsList.getChildAt(fi).requestFocus();
            }
        });
    }

    /** Opens a sub-page, remembering which row you came from. */
    private void subPage(String title, Runnable page) {
        View f = getCurrentFocus();
        if (f != null && f.getParent() == settingsList && !pageStack.isEmpty()) {
            pageStack.get(pageStack.size() - 1)[2] = settingsList.indexOfChild(f);
        }
        showPage(page, title);
    }

    private void row(String icon, int tint, String title, String value, boolean chevron, final Runnable action) {
        final boolean glass = glassSettings();
        final LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), 0, dp(18), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        final int rest = glass ? 0x33FFFFFF : 0xFF1F2026;
        bg.setColor(rest);
        if (glass) bg.setStroke(Math.max(1, dp(1)), 0x26FFFFFF);
        r.setBackground(bg);
        r.setFocusable(true);
        r.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) r.setDefaultFocusHighlightEnabled(false);

        if (icon != null) {
            FrameLayout box = new FrameLayout(this);
            GradientDrawable ib = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF34373F, 0xFF1D1F24});
            ib.setCornerRadius(dp(8));
            box.setBackground(ib);
            ImageView ic = new ImageView(this);
            ic.setImageResource(drawable(icon));
            ic.setColorFilter(tint);
            box.addView(ic, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(32), dp(32));
            bl.setMargins(0, 0, dp(14), 0);
            r.addView(box, bl);
        } else {
            r.setPadding(dp(24), 0, dp(18), 0);
        }
        final TextView t = new UiStyle.FontText(this);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView v = new UiStyle.FontText(this);
        v.setText(value == null ? "" : value);
        v.setTextSize(15);
        v.setTextColor(0x8CFFFFFF);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth(dp(220));
        r.addView(v);
        final TextView ch = new UiStyle.FontText(this);
        ch.setText(chevron ? "  ›" : "");
        ch.setTextSize(20);
        ch.setTextColor(0x8CFFFFFF);
        r.addView(ch);

        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                bg.setColor(has ? 0xFFF2F3F5 : rest);
                t.setTextColor(has ? 0xFF111317 : Color.WHITE);
                v.setTextColor(has ? 0x99111317 : 0x8CFFFFFF);
                ch.setTextColor(has ? 0x99111317 : 0x8CFFFFFF);
                view.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                        .setInterpolator(has ? SPRING : EASE).setDuration(UiStyle.ms(has ? 260 : 160)).start();
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, 0, 0, dp(9));
        settingsList.addView(r, lp);
    }

    /** A page of choices with a tick on the current one. */
    private void choices(String[] options, int selected, final ChoiceResult result) {
        for (int i = 0; i < options.length; i++) {
            final int k = i;
            row(null, 0, options[i], i == selected ? "✓" : "", false, new Runnable() {
                @Override public void run() { result.chose(k); refreshPage(); }
            });
        }
    }

    interface ChoiceResult { void chose(int index); }

    private void pageMain() {
        String name = prefs.getString("user_name", "");
        row("ic_search", 0xFF8E8CFF, "Search Settings", "", false, new Runnable() {
            @Override public void run() { searchSettings(); }
        });
        row("ic_wallpaper", 0xFF5AC8FA, "Appearance", "", true, new Runnable() {
            @Override public void run() { subPage("Appearance", new Runnable() { @Override public void run() { pageAppearance(); } }); }
        });
        row("ic_tune", 0xFFBF5AF2, "Look & Style", "", true, new Runnable() {
            @Override public void run() { subPage("Look & Style", new Runnable() { @Override public void run() { pageLook(); } }); }
        });
        row("ic_apps", 0xFF64D2FF, "Control Centre", ccKeys().size() + " tiles", true, new Runnable() {
            @Override public void run() { subPage("Control Centre", new Runnable() { @Override public void run() { pageControlCentre(); } }); }
        });
        row("ic_person", 0xFF0A84FF, "Startup & Override", prefs.getBoolean("catch_home", false) ? "Catching Home" : "", true, new Runnable() {
            @Override public void run() { subPage("Startup & Override", new Runnable() { @Override public void run() { pageStartup(); } }); }
        });
        row("ic_remote", 0xFF0A84FF, "TV Controls", "", true, new Runnable() {
            @Override public void run() { subPage("TV Controls", new Runnable() { @Override public void run() { pageControls(); } }); }
        });
        row("ic_picture", 0xFFFF453A, "Home Screen", shelfLabel(), true, new Runnable() {
            @Override public void run() { subPage("Home Screen", new Runnable() { @Override public void run() { pageHomeScreen(); } }); }
        });
        row("ic_person", 0xFFFF9F0A, "Profile", name.isEmpty() ? "Add your name" : name, true, new Runnable() {
            @Override public void run() {
                startActivity(new Intent(MainActivity.this, SetupActivity.class).putExtra("step", 1).putExtra("single", true));
            }
        });
        row("ic_weather", 0xFF64D2FF, "Weather", prefs.getBoolean("weather_pill", true)
                ? prefs.getString("weather_city", "On") : "Off", true, new Runnable() {
            @Override public void run() { subPage("Weather", new Runnable() { @Override public void run() { pageWeather(); } }); }
        });
        row("ic_clock", 0xFFFFD60A, "StandBy", StandbyActivity.STYLES[prefs.getInt("standby_style", 0)], true, new Runnable() {
            @Override public void run() { subPage("StandBy", new Runnable() { @Override public void run() { pageStandby(); } }); }
        });
        row("ic_remote", 0xFFD1D1D6, "Button Remap", "", true, new Runnable() {
            @Override public void run() { remapMenu(); }
        });
        row("ic_apps", 0xFF8E8CFF, "Apps", "", true, new Runnable() {
            @Override public void run() { subPage("Apps", new Runnable() { @Override public void run() { pageApps(); } }); }
        });
        row("ic_picture", 0xFF0A84FF, "Display", "", false, new Runnable() {
            @Override public void run() { ccAction("Picture"); }
        });
        row("ic_sound", 0xFFFF375F, "Sound", "", false, new Runnable() {
            @Override public void run() { ccAction("Sound"); }
        });
        row("ic_input", 0xFF30D158, "Inputs", "", false, new Runnable() {
            @Override public void run() { ccAction("Inputs"); }
        });
        row("ic_tune", 0xFFBF5AF2, "Settings Style", glassSettings() ? "Glass UI" : "Classic UI", true, new Runnable() {
            @Override public void run() { subPage("Settings Style", new Runnable() { @Override public void run() { pageStyle(); } }); }
        });
        row("ic_download", 0xFF64D2FF, "Back Up & Restore", "", true, new Runnable() {
            @Override public void run() { subPage("Profile & Backup", new Runnable() { @Override public void run() { pageBackup(); } }); }
        });
        row("ic_apps", 0xFFD1D1D6, "About TV Home", versionName(), true, new Runnable() {
            @Override public void run() { subPage("About TV Home", new Runnable() { @Override public void run() { pageAbout(); } }); }
        });
        row("ic_download", 0xFF30D158, "Setup & Home App", prefs.getBoolean("adb_setup_ok", false) ? "Done" : "", true, new Runnable() {
            @Override public void run() { subPage("Setup & Home App", new Runnable() { @Override public void run() { pageSetup(); } }); }
        });
        row("gear", 0xFFD1D1D6, "All Settings", "", false, new Runnable() {
            @Override public void run() { ccAction("All settings"); }
        });
    }

    private void pageAppearance() {
        String mode = wallpapers.mode();
        String wp = wallpaperLabel();
        String packLabel = "None";
        String ipk = prefs.getString("icon_pack", null);
        if (ipk != null) {
            try { packLabel = pm.getApplicationLabel(pm.getApplicationInfo(ipk, 0)).toString(); } catch (Exception ignored) { }
        }
        row("ic_wallpaper", 0xFF5AC8FA, "Wallpaper", wp, true, new Runnable() {
            @Override public void run() { subPage("Wallpaper", new Runnable() { @Override public void run() { pageWallpaper(); } }); }
        });
        row("ic_picture", 0xFFFF9F0A, "Colour", followColour() ? "Follows the app" : "Off", true, new Runnable() {
            @Override public void run() {
                subPage("Colour", new Runnable() {
                    @Override public void run() {
                        choices(new String[]{"Follow the selected app", "Off (plain photo)"}, followColour() ? 0 : 1, new ChoiceResult() {
                            @Override public void chose(int k) {
                                prefs.edit().putBoolean("follow_colour", k == 0).apply();
                                if (k == 0) { App f = firstFavApp(); if (f != null) applyColour(f); }
                                else { shownColour = 0; presentPhoto(0, 400); }
                            }
                        });
                    }
                });
            }
        });
        row("ic_tune", 0xFF30D158, "Motion", smooth ? "Smooth" : "Full effects", true, new Runnable() {
            @Override public void run() {
                subPage("Motion", new Runnable() {
                    @Override public void run() {
                        choices(new String[]{"Smooth (best for this TV)", "Full effects (fades, colour glow)"}, smooth ? 0 : 1, new ChoiceResult() {
                            @Override public void chose(int k) { setSmooth(k == 0); }
                        });
                    }
                });
            }
        });
        row("ic_apps", 0xFF8E8CFF, "Icon Style", ICON_STYLES[prefs.getInt("icon_style", 0)], true, new Runnable() {
            @Override public void run() {
                subPage("Icon Style", new Runnable() {
                    @Override public void run() {
                        choices(ICON_STYLES, prefs.getInt("icon_style", 0), new ChoiceResult() {
                            @Override public void chose(int k) { prefs.edit().putInt("icon_style", k).apply(); render(); }
                        });
                    }
                });
            }
        });
        row("ic_folder", 0xFF64D2FF, "Icon Pack", packLabel, true, new Runnable() {
            @Override public void run() { chooseIconPack(); }
        });
        row("ic_apps", 0xFFFFD60A, "App Opening", OPEN_NAMES[openStyle()], true, new Runnable() {
            @Override public void run() {
                subPage("App Opening", new Runnable() {
                    @Override public void run() {
                        choices(OPEN_NAMES, openStyle(), new ChoiceResult() {
                            @Override public void chose(int k) { prefs.edit().putString("open_anim", OPEN_KEYS[k]).apply(); }
                        });
                        row("ic_tune", 0xFF30D158, "Opening Speed", SPEED_NAMES[prefs.getInt("open_speed", 1)], true, new Runnable() {
                            @Override public void run() {
                                subPage("Opening Speed", new Runnable() {
                                    @Override public void run() {
                                        choices(SPEED_NAMES, prefs.getInt("open_speed", 1), new ChoiceResult() {
                                            @Override public void chose(int k) { prefs.edit().putInt("open_speed", k).apply(); }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void pageStandby() {
        choices(StandbyActivity.STYLES, prefs.getInt("standby_style", 0), new ChoiceResult() {
            @Override public void chose(int k) { prefs.edit().putInt("standby_style", k).apply(); }
        });
        row("ic_clock", 0xFFFFD60A, "Show StandBy now", "", false, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, StandbyActivity.class)); }
        });
    }

    private void pageApps() {
        row("ic_download", 0xFF30D158, "Install Apps", "", true, new Runnable() {
            @Override public void run() { ccAction("Install apps"); }
        });
        row("ic_hidden", 0xFFD1D1D6, "Hidden Apps", hidden().isEmpty() ? "None" : String.valueOf(hidden().size()), true, new Runnable() {
            @Override public void run() { ccAction("Hidden apps"); }
        });
    }

    private void pageStyle() {
        choices(new String[]{"Glass UI", "Classic UI"}, glassSettings() ? 0 : 1, new ChoiceResult() {
            @Override public void chose(int k) {
                prefs.edit().putString("settings_style", k == 0 ? "glass" : "classic").apply();
                if (k == 0) { settingsBg.setImageDrawable(ccBackdropUnderSettings()); settingsOverlay.setBackgroundColor(0); }
                else { settingsBg.setImageDrawable(null); settingsOverlay.setBackgroundColor(UiStyle.panels == 2 ? 0xFF000000 : 0xFF0B0B0F); }
            }
        });
    }

    /** The home screen is hidden while Settings is open, so show it briefly to blur it. */
    private Drawable ccBackdropUnderSettings() {
        mainRoot.setVisibility(View.VISIBLE);
        Drawable d = ccBackdrop();
        mainRoot.setVisibility(View.INVISIBLE);
        return d;
    }

    /** A slim rounded volume bar. */
    static class VolBar extends View {
        private float level;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        VolBar(android.content.Context c) {
            super(c);
            track.setColor(0x33FFFFFF);
            fill.setColor(0xFFFFFFFF);
        }

        void set(float l) {
            final float from = level;
            final float to = Math.max(0f, Math.min(1f, l));
            ValueAnimator a = ValueAnimator.ofFloat(from, to);
            a.setDuration(UiStyle.ms(220));
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) {
                    level = (Float) va.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight() / 4f;
            float top = (getHeight() - h) / 2f;
            r.set(0, top, getWidth(), top + h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            r.set(0, top, Math.max(h, getWidth() * level), top + h);
            c.drawRoundRect(r, h / 2, h / 2, fill);
        }
    }

    // ---------- Apps & favourites ----------

    private void loadApps() {
        apps.clear();
        List<App> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addFrom(Intent.CATEGORY_LEANBACK_LAUNCHER, true, list, seen);
        addFrom(Intent.CATEGORY_LAUNCHER, false, list, seen);
        addInputs(list);
        Collections.sort(list, new Comparator<App>() {
            @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        for (App a : list) apps.put(a.pkg, a);
    }

    private void addFrom(String category, boolean leanback, List<App> out, Set<String> seen) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName.equals(getPackageName()) || !seen.add(ai.packageName)) continue;
            // The gear tile already opens Settings, so hide the system Settings app.
            if (ai.packageName.equals("com.android.tv.settings") || ai.packageName.equals("com.android.settings")) continue;
            CharSequence lbl = ri.loadLabel(pm);
            if (lbl != null && lbl.toString().trim().equalsIgnoreCase("Settings")) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.cn = new ComponentName(ai.packageName, ai.name);
            a.info = ai;
            a.leanback = leanback;
            CharSequence l = ri.loadLabel(pm);
            a.label = l == null ? a.pkg : l.toString();
            try {
                a.banner = ai.loadBanner(pm);
                if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
            } catch (Exception ignored) { }
            a.icon = ri.loadIcon(pm);
            out.add(a);
        }
    }

    /** A slim row of input buttons (HDMI 1, HDMI 2, PS5) under All apps. */
    private View inputChips(List<App> inputs) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(dp(gap / 2), dp(6), 0, dp(4));
        TextView title = new UiStyle.FontText(this);
        title.setText("Inputs");
        title.setTextSize(14);
        title.setTextColor(0xE6FFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, 0, 0, dp(8));
        box.addView(title);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setClipChildren(false);
        chips.setClipToPadding(false);
        for (final App a : inputs) {
            View chip = pill(a.console ? "ic_gamepad" : "ic_input", a.label, a.label, new Runnable() {
                @Override public void run() { launch(a); }
            });
            chip.setTag(new Tag(a, false, "i:" + a.pkg, new UiStyle.FontText(this)));
            final View.OnFocusChangeListener look = chip.getOnFocusChangeListener();
            chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    look.onFocusChange(v, has);
                    if (has) {
                        focusKey = "i:" + a.pkg;
                        ensureVisible(v);
                        if (userMoved) {
                            pendingHero = a;
                            ui.removeCallbacks(heroUpdate);
                            ui.postDelayed(heroUpdate, 140);
                        }
                    }
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { showOptions(a, false); return true; }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, dp(40));
            cl.setMargins(0, 0, dp(12), 0);
            chips.addView(chip, cl);
        }
        box.addView(chips);
        return box;
    }

    /** HDMI inputs (your PS5 and so on) as tiles that switch the TV straight to that input. */
    private void addInputs(List<App> out) {
        try {
            android.media.tv.TvInputManager tim =
                    (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (tim == null) return;
            List<android.media.tv.TvInputInfo> all = tim.getTvInputList();
            Set<String> parents = new HashSet<>();
            for (android.media.tv.TvInputInfo i : all) if (i.getParentId() != null) parents.add(i.getParentId());
            for (android.media.tv.TvInputInfo i : all) {
                if (i.getType() != android.media.tv.TvInputInfo.TYPE_HDMI || !i.isPassthroughInput()) continue;
                if (parents.contains(i.getId())) continue;   // a named device (e.g. PS5) stands in for its port
                try { if (i.isHidden(this)) continue; } catch (Throwable ignored) { }
                App a = new App();
                a.pkg = "input:" + i.getId();
                a.inputId = i.getId();
                a.leanback = true;
                String label = prefs.getString("label_" + a.pkg, null);
                if (label == null) {
                    CharSequence custom = null;
                    try { custom = i.loadCustomLabel(this); } catch (Throwable ignored) { }
                    CharSequence l = custom != null && custom.length() > 0 ? custom : i.loadLabel(this);
                    label = l == null || l.length() == 0 ? "HDMI" : l.toString();
                }
                a.label = label;
                a.console = i.getParentId() != null || isConsoleName(label);
                a.icon = getDrawable(drawable(a.console ? "ic_gamepad" : "ic_input"));
                out.add(a);
            }
        } catch (Throwable ignored) {
            // Not a TV with inputs: nothing to add.
        }
    }

    private static boolean isConsoleName(String l) {
        String s = l.toLowerCase();
        return s.contains("ps5") || s.contains("ps4") || s.contains("playstation") || s.contains("xbox")
                || s.contains("switch") || s.contains("console") || s.contains("game");
    }

    /** Switches the TV to an HDMI input. With HDMI-CEC on, this can also wake the PS5. */
    private void switchInput(App a) {
        countUse(a.pkg);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.media.tv.TvContract.buildChannelUriForPassthroughInput(a.inputId));
            // Start Live TV fresh: if it's already open in the background, Android would otherwise just
            // bring it back on the last input and never hand it this one.
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't switch to " + a.label + " from here. Opening the TV's inputs instead.",
                    Toast.LENGTH_LONG).show();
            for (App x : apps.values()) {
                String l = x.label.toLowerCase();
                if (x.inputId == null && (l.contains("live tv") || l.contains("input") || l.contains("source"))) {
                    launch(x);
                    return;
                }
            }
        }
    }

    /** Give an input your own name, like "PS5". */
    private void renameInput(final App a) {
        final EditText field = new EditText(this);
        field.setText(a.label);
        field.setSelectAllOnFocus(true);
        field.setSingleLine(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(field);
        new AlertDialog.Builder(this)
                .setTitle("Rename " + a.label)
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = field.getText().toString().trim();
                        if (n.isEmpty()) prefs.edit().remove("label_" + a.pkg).apply();
                        else prefs.edit().putString("label_" + a.pkg, n).apply();
                        loadApps();
                        render();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Once: if a console is plugged in and named (PS5), put it in the dock. */
    private void addConsoleToDock() {
        if (prefs.getBoolean("console_fav_added", false)) return;
        for (App a : apps.values()) {
            if (a.inputId == null || !a.console) continue;
            prefs.edit().putBoolean("console_fav_added", true).apply();
            List<String> f = favs();
            if (!f.contains(a.pkg) && f.size() < MAX_FAVS) { f.add(a.pkg); saveFavs(f); }
            return;
        }
    }

    private static final int MAX_FAVS = 8;

    private List<String> favs() {
        String s = prefs.getString(KEY_FAVS, null);
        List<String> r = new ArrayList<>();
        // One-time tidy-up: earlier versions could leave almost every app in favourites.
        if (s != null && !prefs.getBoolean("favs_tidied_53", false)) {
            prefs.edit().putBoolean("favs_tidied_53", true).apply();
            int count = 0;
            for (String p : s.split("\n")) if (!p.isEmpty() && apps.containsKey(p)) count++;
            if (count > MAX_FAVS) s = null;
        }
        if (s == null) {
            for (String p : DEFAULT_FAVS) if (apps.containsKey(p)) r.add(p);
            if (r.isEmpty()) {
                for (String p : apps.keySet()) { r.add(p); if (r.size() == 5) break; }
            }
            if (!apps.isEmpty() && r.isEmpty()) {
                for (String p : apps.keySet()) { r.add(p); if (r.size() == 5) break; }
            }
            saveFavs(r);
            return r;
        }
        for (String p : s.split("\n")) {
            if (!p.isEmpty() && apps.containsKey(p) && !r.contains(p)) r.add(p);
        }
        return r;
    }

    private void saveFavs(List<String> l) {
        prefs.edit().putString(KEY_FAVS, TextUtils.join("\n", l)).apply();
    }

    private void showOptions(final App a, final boolean fav) {
        if (a.pkg.startsWith("tvhome:")) return;
        final List<String> favs = favs();
        final boolean isFav = favs.contains(a.pkg);
        final Set<String> hidden = hidden();
        final List<String> items = new ArrayList<>();
        if (fav) {
            items.add("Move");
            items.add("Remove from favourites");
        } else {
            items.add(isFav ? "Remove from favourites" : "Add to favourites");
        }
        if (openFolder != null && folders().containsKey(openFolder)
                && folders().get(openFolder).contains(a.pkg)) items.add("Remove from folder");
        else if (a.inputId == null) items.add("Add to folder\u2026");
        items.add("Hide app");
        items.add(a.inputId != null ? "Rename" : "App info");
        if (!hidden.isEmpty()) items.add("Show hidden apps (" + hidden.size() + ")");

        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int i = favs.indexOf(a.pkg);
                        String c = items.get(which);
                        if (c.equals("Move")) {
                            startMove(a.pkg);
                        } else if (c.equals("Remove from favourites")) {
                            favs.remove(a.pkg); saveFavs(favs);
                            if (fav) focusKey = null;
                            render();
                        } else if (c.equals("Add to favourites")) {
                            if (favs.size() >= MAX_FAVS) {
                                Toast.makeText(MainActivity.this, "Favourites hold " + MAX_FAVS
                                        + " apps. Remove one first.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            favs.add(a.pkg); saveFavs(favs); render();
                            Toast.makeText(MainActivity.this, a.label + " added to favourites", Toast.LENGTH_SHORT).show();
                        } else if (c.equals("Hide app")) {
                            hidden.add(a.pkg); saveHidden(hidden);
                            if (favs.remove(a.pkg)) saveFavs(favs);
                            focusKey = null;
                            render();
                            Toast.makeText(MainActivity.this, a.label + " hidden", Toast.LENGTH_SHORT).show();
                        } else if (c.equals("Add to folder\u2026")) {
                            addToFolder(a);
                        } else if (c.equals("Remove from folder")) {
                            removeFromFolder(a.pkg);
                        } else if (c.equals("Rename")) {
                            renameInput(a);
                        } else if (c.equals("App info")) {
                            start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", a.pkg, null)));
                        } else if (c.startsWith("Show hidden apps")) {
                            showHidden();
                        }
                    }
                })
                .show();
    }

    private void showHidden() {
        final Set<String> hidden = hidden();
        final List<String> pkgs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (String p : hidden) {
            App app = apps.get(p);
            if (app == null) continue;
            pkgs.add(p);
            names.add(app.label);
        }
        if (pkgs.isEmpty()) {
            saveHidden(new HashSet<String>());
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Hidden apps \u2014 select one to show it again")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        hidden.remove(pkgs.get(which));
                        saveHidden(hidden);
                        focusKey = "a:" + pkgs.get(which);
                        render();
                        Toast.makeText(MainActivity.this, names.get(which) + " is back", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private Set<String> hidden() {
        Set<String> r = new HashSet<>();
        String s = prefs.getString(KEY_HIDDEN, "");
        for (String p : s.split("\n")) if (!p.isEmpty()) r.add(p);
        return r;
    }

    private void saveHidden(Set<String> h) {
        prefs.edit().putString(KEY_HIDDEN, TextUtils.join("\n", h)).apply();
    }

    private Map<String, Integer> uses() {
        Map<String, Integer> m = new HashMap<>();
        for (String line : prefs.getString(KEY_USES, "").split("\n")) {
            int eq = line.lastIndexOf('=');
            if (eq <= 0) continue;
            try { m.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return m;
    }

    private void countUse(String pkg) {
        Map<String, Integer> m = uses();
        m.put(pkg, (m.containsKey(pkg) ? m.get(pkg) : 0) + 1);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString(KEY_USES, sb.toString()).apply();
    }

    private void launch(App a) {
        if ("tvhome:settings".equals(a.pkg)) { launchFrom = null; openSettings(); return; }
        addRecent(a.pkg);
        if (openFolder != null) closeFolderPanel(false);
        if (a.inputId != null) { switchInput(a); return; }
        countUse(a.pkg);
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(i);
    }

    private void start(Intent i) {
        View from = launchFrom;
        launchFrom = null;
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), openOptions(from));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ================================================================== 6.25 features

    // ---------- Opening animation ----------

    static final String[] OPEN_KEYS = {"zoom", "reveal", "fade", "rise", "pop", "off"};
    static final String[] OPEN_NAMES = {"Zoom from icon", "Reveal from icon", "Fade", "Rise", "Pop", "Off (TV default)"};
    static final String[] SPEED_NAMES = {"Fast", "Normal", "Relaxed"};
    private static final String[] SPEED_KEYS = {"fast", "normal", "slow"};

    private int openStyle() {
        String k = prefs.getString("open_anim", "zoom");
        for (int i = 0; i < OPEN_KEYS.length; i++) if (OPEN_KEYS[i].equals(k)) return i;
        return 0;
    }

    /** How the app being opened appears: growing out of its icon, or a fade, rise or pop. */
    private Bundle openOptions(View from) {
        String style = OPEN_KEYS[openStyle()];
        if ("off".equals(style)) return null;
        try {
            if (from != null && from.isAttachedToWindow() && from.getWidth() > 0) {
                if ("zoom".equals(style)) {
                    return ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
                }
                if ("reveal".equals(style) && Build.VERSION.SDK_INT >= 23) {
                    return ActivityOptions.makeClipRevealAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
                }
            }
            if ("zoom".equals(style) || "reveal".equals(style)) style = "pop";
            String speed = SPEED_KEYS[Math.max(0, Math.min(2, prefs.getInt("open_speed", 1)))];
            int enter = getResources().getIdentifier("open_" + style + "_" + speed, "anim", getPackageName());
            int exit = getResources().getIdentifier("open_hold_" + speed, "anim", getPackageName());
            if (enter == 0) return null;
            return ActivityOptions.makeCustomAnimation(this, enter, exit).toBundle();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- Profile picture ----------

    private void refreshAvatar() {
        if (avatarView == null) return;
        String name = prefs.getString("user_name", "");
        String symbol = prefs.getString("avatar", "");
        if (name.isEmpty() && symbol.isEmpty()) { avatarView.setVisibility(View.GONE); return; }
        int ci = Math.max(0, Math.min(SetupActivity.AVATAR_COLOURS.length - 1, prefs.getInt("avatar_colour", 0)));
        avatarView.setImageBitmap(Art.avatar(symbol, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(),
                SetupActivity.AVATAR_COLOURS[ci], dp(30)));
        avatarView.setVisibility(View.VISIBLE);
    }

    // ---------- Top Shelf ----------

    private String shelfMode() { return prefs.getString("shelf_mode", TopShelf.MODE_OFF); }

    private boolean shelfOn() { return prefs != null && !TopShelf.MODE_OFF.equals(shelfMode()); }

    private String shelfLabel() {
        String m = shelfMode();
        return TopShelf.MODE_WATCH.equals(m) ? "Movies & Shows" : TopShelf.MODE_NEWS.equals(m) ? "News" : "Wallpaper";
    }

    private boolean overlayOpen() {
        return ccOpen || searchOpen || openFolder != null || settingsOpen || wxOpen || swOpen || movingCell != null || listening;
    }

    /** Moves the text up and the rows down a little while big artwork is showing. */
    private void applyShelfLayout() {
        if (hero == null) return;
        boolean on = shelfOn();
        FrameLayout.LayoutParams hl = (FrameLayout.LayoutParams) hero.getLayoutParams();
        int top = (int) (screenH * (on ? 0.135f : 0.18f));
        if (hl.topMargin != top) { hl.topMargin = top; hero.setLayoutParams(hl); }
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) scroll.getLayoutParams();
        int stop = (int) (screenH * (on ? 0.45f : 0.355f));
        if (sl.topMargin != stop) { sl.topMargin = stop; scroll.setLayoutParams(sl); }
        LinearLayout.LayoutParams subl = (LinearLayout.LayoutParams) heroSub.getLayoutParams();
        int subH = on ? -2 : dp(56);
        if (subl.height != subH) { subl.height = subH; heroSub.setLayoutParams(subl); }
        heroSub.setMaxLines(on ? 3 : 2);
        heroSub.setTextSize(on ? 14 : 17);
        shelfActions.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) {
            ++cardGen;
            shelfIndex = -1;
            heroEyebrow.setVisibility(View.GONE);
            heroLogo.setVisibility(View.GONE);
            heroLogo.setImageDrawable(null);
            heroTitle.setVisibility(View.VISIBLE);
            heroTitle.setTextSize(34);
            heroTitle.setSingleLine(true);
            heroSub.setVisibility(View.VISIBLE);
            if (idleFull) wakeFromIdle();
            showGreeting();
            shelfLoadedMode = null;
            if (photoIsCard) {
                photoIsCard = false;
                if (pendingWallpaper != null) {
                    Bitmap b = pendingWallpaper;
                    pendingWallpaper = null;
                    showWallpaper(b, pendingCredit);
                } else {
                    wallpapers.refresh(true);
                }
            }
        } else {
            heroEyebrow.setVisibility(View.VISIBLE);
            if (shelfIndex < 0) {
                heroLogo.setVisibility(View.GONE);
                heroTitle.setVisibility(View.VISIBLE);
                heroTitle.setTextSize(28);
                heroEyebrow.setText(TopShelf.MODE_NEWS.equals(shelfMode()) ? "NEWS" : "POPULAR NOW");
                heroTitle.setText(TopShelf.MODE_NEWS.equals(shelfMode()) ? "Getting the headlines…" : "Finding something to watch…");
                heroSub.setText("");
            }
        }
    }

    private void loadShelf(boolean force) {
        if (!shelfOn() || shelfLoading) return;
        final String mode = shelfMode();
        long age = System.currentTimeMillis() - shelfLoadedAt;
        long maxAge = TopShelf.MODE_NEWS.equals(mode) ? prefs.getInt("news_every", 30) * 60000L : 6 * 3600000L;
        if (!force && mode.equals(shelfLoadedMode) && !shelfCards.isEmpty() && age < maxAge) return;
        shelfLoading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final List<TopShelf.Card> list = TopShelf.MODE_NEWS.equals(mode)
                        ? TopShelf.news(newsIds()) : TopShelf.watch(prefs.getInt("shelf_kind", 0), prefs.getString("shelf_genre", ""));
                ui.post(new Runnable() {
                    @Override public void run() {
                        shelfLoading = false;
                        if (!mode.equals(shelfMode())) { loadShelf(true); return; }
                        if (list.isEmpty()) {
                            if (shelfCards.isEmpty()) {
                                heroTitle.setText("Couldn't reach the service");
                                heroSub.setText("Check the internet connection. TV Home will try again.");
                            }
                            shelfLoadedAt = System.currentTimeMillis() - 25 * 60000L;
                            return;
                        }
                        shelfCards = list;
                        shelfLoadedMode = mode;
                        shelfLoadedAt = System.currentTimeMillis();
                        lastAdvance = System.currentTimeMillis();
                        showCard(0);
                    }
                });
            }
        }).start();
    }

    private TopShelf.Card currentCard() {
        return shelfIndex >= 0 && shelfIndex < shelfCards.size() ? shelfCards.get(shelfIndex) : null;
    }

    /** Shows one card: its artwork fills the screen and its title, logo and details sit top left. */
    private void showCard(int index) {
        final int n = shelfCards.size();
        if (n == 0 || !shelfOn()) return;
        final int i = ((index % n) + n) % n;
        final TopShelf.Card c = shelfCards.get(i);
        final int gen = ++cardGen;
        final int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bg = TopShelf.picture(MainActivity.this, c.image, w, h, false);
                final Bitmap logo = c.logo != null && !c.logo.isEmpty()
                        ? TopShelf.picture(MainActivity.this, c.logo, dp(340), dp(48), true) : null;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (gen != cardGen || !shelfOn()) return;
                        if (bg == null) {
                            // This picture didn't load: drop the card and try the next one.
                            if (i < shelfCards.size()) shelfCards.remove(i);
                            if (!shelfCards.isEmpty()) showCard(i);
                            return;
                        }
                        shelfIndex = i;
                        if (!photoIsCard && pendingWallpaper == null && basePhoto != null) {
                            pendingWallpaper = basePhoto;     // your photo, to bring back later
                            pendingCredit = baseCredit;
                        }
                        photoIsCard = true;
                        showWallpaper(bg, "");
                        cardText(c, logo);
                        updateDots();
                        prefetch(i + 1);
                    }
                });
            }
        }).start();
    }

    private void prefetch(final int index) {
        final int n = shelfCards.size();
        if (n < 2) return;
        final TopShelf.Card c = shelfCards.get(index % n);
        final int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap b = TopShelf.picture(MainActivity.this, c.image, w, h, false);
                if (b != null) b.recycle();   // just warms the cache
            }
        }).start();
    }

    private void cardText(final TopShelf.Card c, final Bitmap logo) {
        final boolean news = "news".equals(c.kind);
        final int gen = ++heroGen;
        hero.animate().cancel();
        hero.animate().alpha(0f).setDuration(UiStyle.ms(smooth ? 120 : 180)).setInterpolator(EASE).withEndAction(new Runnable() {
            @Override public void run() {
                if (gen != heroGen) return;
                heroEyebrow.setText(c.eyebrow);
                heroEyebrow.setVisibility(View.VISIBLE);
                if (logo != null) {
                    heroLogo.setImageBitmap(logo);
                    heroLogo.setVisibility(View.VISIBLE);
                    heroTitle.setVisibility(View.GONE);
                } else {
                    heroLogo.setVisibility(View.GONE);
                    heroLogo.setImageDrawable(null);
                    heroTitle.setVisibility(View.VISIBLE);
                    heroTitle.setText(c.title);
                    heroTitle.setTextSize(news ? 24 : 34);
                    heroTitle.setSingleLine(!news);
                    heroTitle.setMaxLines(news ? 2 : 1);
                }
                StringBuilder sub = new StringBuilder();
                if (news) {
                    String ago = TopShelf.ago(c.time);
                    if (!ago.isEmpty()) sub.append(ago);
                    if (c.desc != null && !c.desc.isEmpty() && !c.desc.equals(c.title)) {
                        if (sub.length() > 0) sub.append("  ·  ");
                        sub.append(c.desc);
                    }
                }
                // Movies and shows: just the title (or logo) over the poster, no summary.
                heroSub.setText(sub.toString());
                heroSub.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);
                heroSub.setMaxLines(news ? 2 : 3);
                shelfOpenText.setText(news ? "Read Story" : "Where to Watch");
                hero.setTranslationY(dp(8));
                hero.animate().alpha(1f).translationY(0).setDuration(UiStyle.ms(smooth ? 260 : 380)).setInterpolator(EASE).start();
            }
        }).start();
    }

    private void updateDots() {
        int n = shelfCards.size();
        if (n <= 1) { shelfDots.setText(""); return; }
        int show = Math.min(n, 12);
        int first = Math.max(0, Math.min(shelfIndex - show / 2, n - show));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < show; i++) sb.append('●');
        SpannableString ss = new SpannableString(sb.toString());
        int cur = shelfIndex - first;
        if (cur >= 0 && cur < show) ss.setSpan(new ForegroundColorSpan(Color.WHITE), cur, cur + 1, 0);
        shelfDots.setText(ss);
    }

    /** OK on the card: where to watch it (the TV's own search), or the news story. */
    private void openCard() {
        final TopShelf.Card c = currentCard();
        if (c == null) return;
        if ("news".equals(c.kind)) { readStory(c); return; }
        if (c != null) { whereToWatch(c); return; }
        try {
            startActivity(new Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, c.title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, c.title)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(this, "Search isn't available on this TV", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showStory(TopShelf.Card c) {
        TextView body = new UiStyle.FontText(this);
        String ago = TopShelf.ago(c.time);
        body.setText((c.desc == null || c.desc.isEmpty() ? "" : c.desc + "\n\n") + c.source + (ago.isEmpty() ? "" : "  ·  " + ago));
        body.setTextSize(17);
        body.setLineSpacing(0, 1.25f);
        body.setPadding(dp(24), dp(8), dp(24), 0);
        new AlertDialog.Builder(this)
                .setTitle(c.title)
                .setView(body)
                .setPositiveButton("Close", null)
                .show();
    }

    private final Runnable shelfTick = new Runnable() {
        @Override public void run() {
            ui.postDelayed(this, 2000);
            if (!shelfOn() || shelfCards.isEmpty() || overlayOpen() || !hasWindowFocus()) return;
            long now = System.currentTimeMillis();
            loadShelf(false);
            if (prefs.getBoolean("shelf_idle", true) && !idleFull && now - lastKey > 30000) enterIdle();
            long every = prefs.getInt("shelf_every", 8) * 1000L;
            if (prefs.getBoolean("shelf_auto", true) && now - lastKey > 4000 && now - lastAdvance >= every) {
                lastAdvance = now;
                showCard(shelfIndex + 1);
            }
        }
    };

    /** After a while without the remote, the rows and dock fade away so only the artwork shows. */
    private void enterIdle() {
        idleFull = true;
        scroll.animate().cancel();
        dockFrame.animate().cancel();
        scroll.animate().alpha(0f).setDuration(UiStyle.ms(700)).setInterpolator(EASE).withLayer().start();
        dockFrame.animate().alpha(0f).setDuration(UiStyle.ms(700)).setInterpolator(EASE).withLayer().start();
        shelfActions.animate().alpha(0f).setDuration(UiStyle.ms(700)).start();
        weatherPill.animate().alpha(0.6f).setDuration(UiStyle.ms(700)).start();
    }

    private void wakeFromIdle() {
        idleFull = false;
        scroll.animate().cancel();
        dockFrame.animate().cancel();
        scroll.animate().alpha(1f).setDuration(UiStyle.ms(300)).setInterpolator(EASE).withLayer().start();
        dockFrame.animate().alpha(1f).setDuration(UiStyle.ms(300)).setInterpolator(EASE).withLayer().start();
        shelfActions.animate().alpha(1f).setDuration(UiStyle.ms(300)).start();
        weatherPill.animate().alpha(1f).setDuration(UiStyle.ms(300)).start();
    }

    private void pageHomeScreen() {
        final String[] names = {"Wallpaper", "Movies & Shows", "News"};
        final String[] keys = {TopShelf.MODE_OFF, TopShelf.MODE_WATCH, TopShelf.MODE_NEWS};
        String m = shelfMode();
        int sel = TopShelf.MODE_WATCH.equals(m) ? 1 : TopShelf.MODE_NEWS.equals(m) ? 2 : 0;
        row("ic_picture", 0xFFFF453A, "Top Shelf", names[sel], true, new Runnable() {
            @Override public void run() {
                subPage("Top Shelf", new Runnable() {
                    @Override public void run() {
                        String cur = shelfMode();
                        int s = TopShelf.MODE_WATCH.equals(cur) ? 1 : TopShelf.MODE_NEWS.equals(cur) ? 2 : 0;
                        choices(new String[]{"Wallpaper (photos)", "Movies & Shows (popular now)", "News (newsrooms you choose)"}, s,
                                new ChoiceResult() {
                                    @Override public void chose(int k) {
                                        prefs.edit().putString("shelf_mode", keys[k]).apply();
                                        shelfCards = new ArrayList<>();
                                        shelfIndex = -1;
                                        ++cardGen;
                                        applyShelfLayout();
                                        loadShelf(true);
                                    }
                                });
                    }
                });
            }
        });
        shelfExtras();
        if (sel == 0) return;
        final int[] secs = {6, 8, 12};
        int every = prefs.getInt("shelf_every", 8);
        row("ic_clock", 0xFFFFD60A, "Change Every", every + " seconds", true, new Runnable() {
            @Override public void run() {
                subPage("Change Every", new Runnable() {
                    @Override public void run() {
                        int e = prefs.getInt("shelf_every", 8);
                        choices(new String[]{"6 seconds", "8 seconds", "12 seconds"}, e == 6 ? 0 : e == 12 ? 2 : 1,
                                new ChoiceResult() {
                                    @Override public void chose(int k) { prefs.edit().putInt("shelf_every", secs[k]).apply(); }
                                });
                    }
                });
            }
        });
        final boolean auto = prefs.getBoolean("shelf_auto", true);
        row("ic_tune", 0xFF30D158, "Auto Shuffle", auto ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("shelf_auto", !prefs.getBoolean("shelf_auto", true)).apply(); refreshPage(); }
        });
        final boolean idle = prefs.getBoolean("shelf_idle", true);
        row("ic_moon", 0xFFBF5AF2, "Full Screen When Idle", idle ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("shelf_idle", !prefs.getBoolean("shelf_idle", true)).apply(); refreshPage(); }
        });
        row("ic_download", 0xFF0A84FF, "Refresh Now", "", false, new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, "Refreshing…", Toast.LENGTH_SHORT).show();
                loadShelf(true);
            }
        });
    }

    // ---------- Weather ----------

    private void showCachedWeather() {
        if (weatherPill == null) return;
        boolean on = prefs.getBoolean("weather_pill", true);
        if (!on || !prefs.contains("wx_temp")) { weatherPill.setVisibility(View.GONE); return; }
        weatherIcon.setImageBitmap(Art.weather(prefs.getInt("wx_code", 0), prefs.getBoolean("wx_day", true), dp(22)));
        weatherTemp.setText(prefs.getInt("wx_temp", 0) + "°");
        if (weatherPill.getVisibility() != View.VISIBLE) {
            weatherPill.setAlpha(0f);
            weatherPill.setVisibility(View.VISIBLE);
            weatherPill.animate().alpha(idleFull ? 0.6f : 1f).setDuration(UiStyle.ms(500)).start();
        }
    }

    private boolean weatherLoading;

    private void refreshWeather(boolean force) {
        long last = prefs.getLong("weather_time", 0);
        if (weatherLoading || (!force && weatherData != null && System.currentTimeMillis() - last < 20 * 60 * 1000L)) return;
        weatherLoading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Weather.Data d = Weather.fetch(prefs);
                    prefs.edit().putLong("weather_time", System.currentTimeMillis())
                            .putString("weather_text", d.temp + "°  " + Weather.describe(d.code))
                            .putInt("wx_temp", d.temp).putInt("wx_code", d.code).putBoolean("wx_day", d.day).apply();
                    ui.post(new Runnable() {
                        @Override public void run() {
                            weatherLoading = false;
                            weatherData = d;
                            showCachedWeather();
                            if (wxOpen) fillWeatherPanel();
                        }
                    });
                } catch (Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            weatherLoading = false;
                            if (wxOpen && weatherData == null) fillWeatherPanel();
                        }
                    });
                }
            }
        }).start();
    }

    private void buildWeatherPanel(FrameLayout outer) {
        wxOverlay = new FrameLayout(this);
        wxOverlay.setVisibility(View.INVISIBLE);
        wxBlur = new ImageView(this);
        wxBlur.setScaleType(ImageView.ScaleType.FIT_XY);
        wxOverlay.addView(wxBlur, new FrameLayout.LayoutParams(-1, -1));
        wxPanel = new LinearLayout(this);
        wxPanel.setOrientation(LinearLayout.VERTICAL);
        wxPanel.setPadding(dp(22), dp(18), dp(22), dp(18));
        wxPanel.setClipChildren(false);
        wxPanel.setClipToPadding(false);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(372), -2, Gravity.TOP | Gravity.START);
        pl.setMargins(dp(40), dp(20), 0, 0);
        wxOverlay.addView(wxPanel, pl);
        outer.addView(wxOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openWeather() {
        if (wxOpen) return;
        wxOpen = true;
        applyCcTint();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        bg.setColor(ccFill());
        bg.setStroke(Math.max(1, dp(1)), 0x2EFFFFFF);
        wxPanel.setBackground(bg);
        fillWeatherPanel();
        Drawable backdrop = ccBackdrop();
        wxBlur.setImageDrawable(backdrop);
        wxBlur.setBackgroundColor(backdrop == null ? 0x99000000 : 0);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        wxOverlay.setVisibility(View.VISIBLE);
        wxBlur.setAlpha(0f);
        wxBlur.animate().alpha(1f).setInterpolator(EASE).setDuration(UiStyle.ms(300)).withEndAction(new Runnable() {
            @Override public void run() { if (wxOpen && wxBlur.getDrawable() != null) mainRoot.setVisibility(View.INVISIBLE); }
        }).start();
        wxPanel.setAlpha(0f);
        wxPanel.setTranslationX(-dp(24));
        wxPanel.setTranslationY(-dp(16));
        wxPanel.setScaleX(0.94f);
        wxPanel.setScaleY(0.94f);
        wxPanel.setPivotX(0);
        wxPanel.setPivotY(0);
        wxPanel.animate().alpha(1f).translationX(0).translationY(0).scaleX(1f).scaleY(1f)
                .setStartDelay(40).setInterpolator(GLIDE).setDuration(UiStyle.ms(480)).withLayer().start();
        refreshWeather(System.currentTimeMillis() - prefs.getLong("weather_time", 0) > 10 * 60 * 1000L);
    }

    private void closeWeather() {
        if (!wxOpen) return;
        wxOpen = false;
        mainRoot.setVisibility(View.VISIBLE);
        wxBlur.animate().cancel();
        wxBlur.animate().alpha(0f).setInterpolator(EASE).setDuration(UiStyle.ms(260)).start();
        wxPanel.animate().cancel();
        wxPanel.animate().alpha(0f).translationX(-dp(16)).translationY(-dp(8)).scaleX(0.97f).scaleY(0.97f)
                .setStartDelay(0).setInterpolator(EASE).setDuration(UiStyle.ms(220)).withLayer()
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!wxOpen) { wxOverlay.setVisibility(View.INVISIBLE); wxBlur.setImageDrawable(null); }
                    }
                }).start();
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (weatherPill != null && weatherPill.getVisibility() == View.VISIBLE) weatherPill.requestFocus();
    }

    private TextView wxText(String t, int sp, int colour, boolean medium) {
        TextView v = new UiStyle.FontText(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(colour);
        v.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    private void fillWeatherPanel() {
        wxPanel.removeAllViews();
        Weather.Data d = weatherData;
        String city = prefs.getString("weather_city", "");
        wxPanel.addView(wxText(city.isEmpty() ? "My Location" : city, 18, Color.WHITE, true));
        wxPanel.addView(wxText(prefs.getBoolean("weather_fixed", false) ? "Chosen city" : "From this TV's internet connection",
                12, 0x99FFFFFF, false));
        if (d == null) {
            TextView wait = wxText("Getting the weather…", 16, 0xCCFFFFFF, false);
            wait.setPadding(0, dp(24), 0, dp(24));
            wxPanel.addView(wait);
        } else {
            LinearLayout big = new LinearLayout(this);
            big.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = wxText(d.temp + "°", 52, Color.WHITE, false);
            t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            big.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView ic = new ImageView(this);
            ic.setImageBitmap(Art.weather(d.code, d.day, dp(56)));
            big.addView(ic, new LinearLayout.LayoutParams(dp(56), dp(56)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.setMargins(0, dp(2), 0, 0);
            wxPanel.addView(big, bl);
            wxPanel.addView(wxText(Weather.describe(d.code), 17, Color.WHITE, true));
            wxPanel.addView(wxText("Feels like " + d.feels + "°   ·   H " + d.high + "°  L " + d.low + "°",
                    14, 0xB3FFFFFF, false));

            String[][] tiles = {
                    {"UV INDEX", d.uv < 0 ? "–" : String.valueOf(d.uv), Weather.uvWord(d.uv)},
                    {"AIR QUALITY", d.aqi < 0 ? "–" : String.valueOf(d.aqi), Weather.aqiWord(d.aqi)},
                    {"HUMIDITY", d.humidity + "%", ""},
                    {"WIND", d.wind + " km/h", d.wind >= 40 ? "Strong" : d.wind >= 20 ? "Moderate" : "Light"},
                    {"SUNRISE", d.sunrise, ""},
                    {"SUNSET", d.sunset, ""}};
            for (int r = 0; r < 2; r++) {
                LinearLayout line = new LinearLayout(this);
                for (int c = 0; c < 3; c++) {
                    String[] tt = tiles[r * 3 + c];
                    LinearLayout tile = new LinearLayout(this);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setPadding(dp(10), dp(7), dp(8), dp(7));
                    GradientDrawable tb = new GradientDrawable();
                    tb.setCornerRadius(dp(14));
                    tb.setColor(0x1FFFFFFF);
                    tile.setBackground(tb);
                    TextView lab = wxText(tt[0], 10, 0x99FFFFFF, true);
                    lab.setLetterSpacing(0.08f);
                    tile.addView(lab);
                    tile.addView(wxText(tt[1], 16, Color.WHITE, true));
                    tile.addView(wxText(tt[2].isEmpty() ? " " : tt[2], 11, 0x99FFFFFF, false));
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
                    if (c < 2) tl.setMargins(0, 0, dp(8), 0);
                    line.addView(tile, tl);
                }
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
                ll.setMargins(0, dp(r == 0 ? 14 : 8), 0, 0);
                wxPanel.addView(line, ll);
            }

            TextView nh = wxText("NEXT 6 HOURS", 10, 0x99FFFFFF, true);
            nh.setLetterSpacing(0.08f);
            nh.setPadding(0, dp(14), 0, dp(6));
            wxPanel.addView(nh);
            LinearLayout hrs = new LinearLayout(this);
            for (Weather.Hour x : d.hours) {
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.addView(wxText(x.label, 12, 0xCCFFFFFF, true));
                ImageView hi = new ImageView(this);
                hi.setImageBitmap(Art.weather(x.code, x.day, dp(26)));
                LinearLayout.LayoutParams hil = new LinearLayout.LayoutParams(dp(26), dp(26));
                hil.setMargins(0, dp(3), 0, dp(3));
                col.addView(hi, hil);
                col.addView(wxText(x.temp + "°", 15, Color.WHITE, true));
                col.addView(wxText(x.rain >= 20 ? x.rain + "%" : " ", 11, 0xFF64D2FF, true));
                hrs.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            wxPanel.addView(hrs, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        View choose = pill("ic_search", "Choose City", "Choose City", new Runnable() {
            @Override public void run() { chooseCity(); }
        });
        buttons.addView(choose, new LinearLayout.LayoutParams(-2, dp(34)));
        View mine = pill("ic_wifi", "My Location", "Use My Location", new Runnable() {
            @Override public void run() { useMyLocation(); }
        });
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, dp(34));
        ml.setMargins(dp(10), 0, 0, 0);
        buttons.addView(mine, ml);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(-2, -2);
        bl2.setMargins(0, dp(14), 0, 0);
        wxPanel.addView(buttons, bl2);
        muteClicks(wxPanel);
        choose.requestFocus();
    }

    private void chooseCity() {
        askText("Choose a City", "City name", "", new TextResult() {
            @Override public void done(final String q) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        List<Weather.Place> found;
                        try { found = Weather.search(q); } catch (Exception e) { found = new ArrayList<>(); }
                        final List<Weather.Place> list = found;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                if (list.isEmpty()) {
                                    Toast.makeText(MainActivity.this, "No city found for “" + q + "”", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String[] names = new String[list.size()];
                                for (int i = 0; i < names.length; i++) {
                                    Weather.Place p = list.get(i);
                                    names[i] = p.name + (p.detail.isEmpty() ? "" : "  —  " + p.detail);
                                }
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Choose a City")
                                        .setItems(names, new DialogInterface.OnClickListener() {
                                            @Override public void onClick(DialogInterface d, int which) {
                                                Weather.Place p = list.get(which);
                                                prefs.edit().putBoolean("weather_fixed", true)
                                                        .putFloat("lat", (float) p.lat).putFloat("lon", (float) p.lon)
                                                        .putString("weather_city", p.name).apply();
                                                weatherData = null;
                                                if (wxOpen) fillWeatherPanel();
                                                refreshWeather(true);
                                                if (settingsOpen) refreshPage();
                                            }
                                        })
                                        .show();
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private void useMyLocation() {
        prefs.edit().putBoolean("weather_fixed", false).remove("loc_time").remove("weather_city").apply();
        weatherData = null;
        if (wxOpen) fillWeatherPanel();
        refreshWeather(true);
        if (settingsOpen) refreshPage();
    }

    private void pageWeather() {
        final boolean on = prefs.getBoolean("weather_pill", true);
        row("ic_weather", 0xFF64D2FF, "Weather Pill", on ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                prefs.edit().putBoolean("weather_pill", !prefs.getBoolean("weather_pill", true)).apply();
                showCachedWeather();
                refreshPage();
            }
        });
        String city = prefs.getString("weather_city", "");
        row("ic_search", 0xFFFF9F0A, "City", (prefs.getBoolean("weather_fixed", false) ? "" : "Auto · ")
                + (city.isEmpty() ? "My Location" : city), true, new Runnable() {
            @Override public void run() { chooseCity(); }
        });
        row("ic_wifi", 0xFF30D158, "Use My Location", "", false, new Runnable() {
            @Override public void run() {
                useMyLocation();
                Toast.makeText(MainActivity.this, "Using this TV's location", Toast.LENGTH_SHORT).show();
            }
        });
        row("ic_weather", 0xFF5AC8FA, "Weather Details", "", false, new Runnable() {
            @Override public void run() { closeSettings(false); openWeather(); }
        });
    }

    // ---------- Setup ----------

    private void pageSetup() {
        row("ic_download", 0xFF30D158, "Make TV Home the Home App", prefs.getBoolean("adb_setup_ok", false) ? "Done" : "", true, new Runnable() {
            @Override public void run() {
                startActivity(new Intent(MainActivity.this, SetupActivity.class).putExtra("step", 2));
            }
        });
        row("ic_remote", 0xFFD1D1D6, "Turn On Button Remapping", "", false, new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, "Connecting to this TV… if it asks, tick Always allow.", Toast.LENGTH_LONG).show();
                new Thread(new Runnable() {
                    @Override public void run() {
                        String msg;
                        try {
                            AdbClient.runSetup(MainActivity.this, null, true);
                            msg = "Button remapping is on";
                        } catch (Exception e) {
                            msg = "Couldn't connect. Turn on debugging in Developer options, then try again.";
                        }
                        final String m = msg;
                        ui.post(new Runnable() {
                            @Override public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); }
                        });
                    }
                }).start();
            }
        });
        row("ic_person", 0xFFFF9F0A, "Run Setup Again", "", true, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, SetupActivity.class)); }
        });
    }

    // ================================================================== Build 1: look & wallpapers

    private static final String[] CLOCK_NAMES = {"Follow the TV", "12-hour", "24-Hour Time"};

    private String clockFormat(String asked) {
        int c = prefs.getInt("clock_24", 0);
        if (c == 1) return asked.contains("a") ? "h:mm a" : "h:mm";
        if (c == 2) return "HH:mm";
        return asked;
    }

    /** Changes that need the screen rebuilt (fonts, sizes, colours) restart TV Home quietly. */
    private void restyle() {
        UiStyle.load(this, prefs);
        Toast.makeText(this, "Applying…", Toast.LENGTH_SHORT).show();
        ui.postDelayed(new Runnable() { @Override public void run() { recreate(); } }, 250);
    }

    private void pageLook() {
        row("ic_tune", 0xFFBF5AF2, "Font", prefs.getBoolean("custom_font", false)
                ? prefs.getString("font_name", "Custom") : "TV's own font", true, new Runnable() {
            @Override public void run() { subPage("Font", new Runnable() { @Override public void run() { pageFont(); } }); }
        });
        lookChoice("ic_picture", 0xFFFF9F0A, "Font Colour", UiStyle.FONT_COLOURS, "font_colour", 0, true);
        lookChoice("ic_tune", 0xFF30D158, "Text Size", UiStyle.TEXT_SIZES, "text_size", 1, true);
        lookChoice("ic_apps", 0xFF0A84FF, "Focus Outline", UiStyle.OUTLINES, "focus_outline", 2, true);
        lookChoice("ic_tune", 0xFFFFD60A, "Animation Speed", UiStyle.ANIM_SPEEDS, "anim_speed", 1, false);
        lookChoice("ic_sound", 0xFFFF375F, "Interface Sounds", UiStyle.SOUNDS, "ui_sounds", 0, false);
        lookChoice("ic_folder", 0xFF64D2FF, "Panels", UiStyle.PANELS, "panels", 0, true);
        lookChoice("ic_clock", 0xFFFFD60A, "Clock", CLOCK_NAMES, "clock_24", 0, true);
        row("ic_apps", 0xFF0A84FF, "Sidebar & Dynamic Island", (sidebarOn() ? "Sidebar" : "") + (prefs.getBoolean("island", false)
                ? (sidebarOn() ? " · " : "") + "Island" : ""), true, new Runnable() {
            @Override public void run() { subPage("Sidebar & Dynamic Island", new Runnable() { @Override public void run() { pageLayout(); } }); }
        });
        final boolean labels = prefs.getBoolean("dock_labels", false);
        row("ic_apps", 0xFF8E8CFF, "App Names in the Dock", labels ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                boolean on = !prefs.getBoolean("dock_labels", false);
                prefs.edit().putBoolean("dock_labels", on).apply();
                dockTip.setVisibility(on ? View.VISIBLE : View.GONE);
                refreshPage();
            }
        });
    }

    /** A Look &amp; Style setting with a list of choices. Some need the screen rebuilt. */
    private void lookChoice(String icon, int tint, final String title, final String[] names, final String key,
                            final int def, final boolean rebuild) {
        int cur = Math.max(0, Math.min(names.length - 1, prefs.getInt(key, def)));
        row(icon, tint, title, names[cur], true, new Runnable() {
            @Override public void run() {
                subPage(title, new Runnable() {
                    @Override public void run() {
                        choices(names, Math.max(0, Math.min(names.length - 1, prefs.getInt(key, def))), new ChoiceResult() {
                            @Override public void chose(int k) {
                                prefs.edit().putInt(key, k).apply();
                                UiStyle.load(MainActivity.this, prefs);
                                if (key.equals("ui_sounds")) UiStyle.tick(MainActivity.this);
                                if (rebuild) restyle();
                            }
                        });
                    }
                });
            }
        });
    }

    // ---------- Fonts ----------

    private void pageFont() {
        final boolean custom = prefs.getBoolean("custom_font", false);
        row(null, 0, "TV's own font", custom ? "" : "✓", false, new Runnable() {
            @Override public void run() {
                if (!prefs.getBoolean("custom_font", false)) return;
                prefs.edit().putBoolean("custom_font", false).apply();
                restyle();
            }
        });
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            row("ic_folder", 0xFF64D2FF, "Allow Access to Find Fonts", "", false, new Runnable() {
                @Override public void run() {
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                }
            });
            return;
        }
        final List<java.io.File> fonts = findFonts();
        final String current = prefs.getString("font_name", "");
        for (final java.io.File f : fonts) {
            row(null, 0, f.getName().replaceAll("(?i)\\.(ttf|otf)$", ""),
                    custom && f.getName().equals(current) ? "✓" : f.getParentFile().getName(), false, new Runnable() {
                @Override public void run() { installFont(f); }
            });
        }
        if (fonts.isEmpty()) {
            row(null, 0, "No fonts found", "", false, new Runnable() { @Override public void run() { } });
        }
        TextView help = new UiStyle.FontText(this);
        help.setText("Put a .ttf or .otf file in the TV's Download folder, in Pictures/TV Home, or on a USB drive, "
                + "and it appears here. From a computer:  adb push MyFont.ttf /sdcard/Download/");
        help.setTextSize(14);
        help.setTextColor(0x99FFFFFF);
        help.setLineSpacing(0, 1.2f);
        help.setPadding(dp(16), dp(14), dp(16), 0);
        settingsList.addView(help);
    }

    private List<java.io.File> findFonts() {
        List<java.io.File> out = new ArrayList<>();
        List<java.io.File> roots = new ArrayList<>();
        roots.add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS));
        roots.add(Wallpapers.myFolder());
        roots.add(new java.io.File(android.os.Environment.getExternalStorageDirectory(), "Fonts"));
        java.io.File[] vols = new java.io.File("/storage").listFiles();
        if (vols != null) for (java.io.File v : vols) if (!v.getName().equals("emulated") && !v.getName().equals("self")) roots.add(v);
        for (java.io.File r : roots) collectFonts(r, out, 2);
        return out;
    }

    private void collectFonts(java.io.File dir, List<java.io.File> out, int depth) {
        java.io.File[] fs = dir.listFiles();
        if (fs == null) return;
        for (java.io.File f : fs) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (f.isFile() && (n.endsWith(".ttf") || n.endsWith(".otf")) && f.length() < 20 * 1024 * 1024) out.add(f);
            else if (f.isDirectory() && depth > 0 && !n.startsWith(".")) collectFonts(f, out, depth - 1);
            if (out.size() > 40) return;
        }
    }

    private void installFont(java.io.File f) {
        try {
            Typeface test = Typeface.createFromFile(f);
            if (test == null) throw new Exception("bad font");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.FileOutputStream out = new java.io.FileOutputStream(UiStyle.fontFile(this));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            prefs.edit().putBoolean("custom_font", true).putString("font_name", f.getName()).apply();
            restyle();
        } catch (Throwable t) {
            Toast.makeText(this, "Couldn't use that font file", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Wallpapers ----------

    private String wallpaperLabel() {
        String mode = wallpapers.mode();
        if (Wallpapers.MODE_MINE.equals(mode)) return "My photos";
        if (Wallpapers.MODE_PHONE.equals(mode)) return "From phone";
        if (Wallpapers.MODE_ONLINE.equals(mode)) return Wallpapers.sourceName(prefs.getString("wp_source", "bing"));
        if (Wallpapers.MODE_GRADIENT.equals(mode)) return "Gradient";
        if (Wallpapers.MODE_EMOJI.equals(mode)) return "Emoji";
        if (Wallpapers.MODE_FRACTAL.equals(mode)) return "Fractal Glass";
        return "Scenery";
    }

    private void useWallpaper(String mode) {
        wallpapers.setMode(mode);
        prefs.edit().putInt("wp_cfg", prefs.getInt("wp_cfg", 0) + 1).apply();
        wallpapers.refresh(true);
        refreshPage();
    }

    private void pageWallpaper() {
        final String mode = wallpapers.mode();
        row(null, 0, "Scenery by time of day", Wallpapers.MODE_SCENERY.equals(mode) ? "✓" : "", false, new Runnable() {
            @Override public void run() { useWallpaper(Wallpapers.MODE_SCENERY); }
        });
        row("ic_wallpaper", 0xFF5AC8FA, "Online Wallpapers", Wallpapers.MODE_ONLINE.equals(mode)
                ? "✓  " + Wallpapers.sourceName(prefs.getString("wp_source", "bing")) : "", true, new Runnable() {
            @Override public void run() { subPage("Online Wallpapers", new Runnable() { @Override public void run() { pageOnline(); } }); }
        });
        row("ic_picture", 0xFFFF9F0A, "Gradient & Solid Colour", Wallpapers.MODE_GRADIENT.equals(mode) ? "✓" : "", true, new Runnable() {
            @Override public void run() { subPage("Gradient & Solid Colour", new Runnable() { @Override public void run() { pageGradient(); } }); }
        });
        row("ic_person", 0xFFFFD60A, "Emoji Wallpapers", Wallpapers.MODE_EMOJI.equals(mode) ? "✓" : "", true, new Runnable() {
            @Override public void run() { subPage("Emoji Wallpapers", new Runnable() { @Override public void run() { pageEmoji(); } }); }
        });
        row("ic_tune", 0xFFBF5AF2, "Fractal Glass", Wallpapers.MODE_FRACTAL.equals(mode) ? "✓" : "", true, new Runnable() {
            @Override public void run() { subPage("Fractal Glass", new Runnable() { @Override public void run() { pageFractal(); } }); }
        });
        row(null, 0, "My photos", Wallpapers.MODE_MINE.equals(mode) ? "✓" : "", false, new Runnable() {
            @Override public void run() {
                wallpapers.setMode(Wallpapers.MODE_MINE);
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                } else {
                    useMyPhotos();
                }
                refreshPage();
            }
        });
        row(null, 0, "Send from phone…", Wallpapers.MODE_PHONE.equals(mode) ? "✓" : "", false, new Runnable() {
            @Override public void run() { sendFromPhone(); }
        });
        everyRow();
        row(null, 0, "Refresh Photos", "", false, new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, "Finding a new wallpaper…", Toast.LENGTH_SHORT).show();
                wallpapers.refresh(true);
            }
        });
    }

    private void everyRow() {
        int e = prefs.getInt("wp_every", 0), sel = 0;
        for (int i = 0; i < Wallpapers.EVERY.length; i++) if (Wallpapers.EVERY[i] == e) sel = i;
        row("ic_clock", 0xFFFFD60A, "Change Photo Every", Wallpapers.EVERY_NAMES[sel], true, new Runnable() {
            @Override public void run() {
                subPage("Change Photo Every", new Runnable() {
                    @Override public void run() {
                        int e2 = prefs.getInt("wp_every", 0), s2 = 0;
                        for (int i = 0; i < Wallpapers.EVERY.length; i++) if (Wallpapers.EVERY[i] == e2) s2 = i;
                        choices(Wallpapers.EVERY_NAMES, s2, new ChoiceResult() {
                            @Override public void chose(int k) { prefs.edit().putInt("wp_every", Wallpapers.EVERY[k]).apply(); }
                        });
                    }
                });
            }
        });
    }

    private void pageOnline() {
        final boolean active = Wallpapers.MODE_ONLINE.equals(wallpapers.mode());
        row("ic_wallpaper", 0xFF30D158, active ? "In Use" : "Use Online Wallpapers", active ? "✓" : "", false, new Runnable() {
            @Override public void run() { useWallpaper(Wallpapers.MODE_ONLINE); }
        });
        final String source = prefs.getString("wp_source", "bing");
        row("ic_picture", 0xFF5AC8FA, "Photo Source", Wallpapers.sourceName(source), true, new Runnable() {
            @Override public void run() {
                subPage("Photo Source", new Runnable() {
                    @Override public void run() {
                        String[] names = new String[Wallpapers.SOURCES.length];
                        int sel = 0;
                        for (int i = 0; i < names.length; i++) {
                            names[i] = Wallpapers.SOURCES[i][1];
                            if (Wallpapers.SOURCES[i][0].equals(prefs.getString("wp_source", "bing"))) sel = i;
                        }
                        choices(names, sel, new ChoiceResult() {
                            @Override public void chose(int k) {
                                prefs.edit().putString("wp_source", Wallpapers.SOURCES[k][0]).apply();
                                useWallpaper(Wallpapers.MODE_ONLINE);
                            }
                        });
                    }
                });
            }
        });
        if (!"bing".equals(source)) {
            row("ic_search", 0xFFFF9F0A, "Topic", prefs.getString("wp_topic", "Nature"), true, new Runnable() {
                @Override public void run() {
                    subPage("Topic", new Runnable() {
                        @Override public void run() {
                            String cur = prefs.getString("wp_topic", "Nature");
                            int sel = -1;
                            for (int i = 0; i < Wallpapers.TOPICS.length; i++) if (Wallpapers.TOPICS[i].equals(cur)) sel = i;
                            choices(Wallpapers.TOPICS, sel, new ChoiceResult() {
                                @Override public void chose(int k) {
                                    prefs.edit().putString("wp_topic", Wallpapers.TOPICS[k]).apply();
                                    useWallpaper(Wallpapers.MODE_ONLINE);
                                }
                            });
                            row("ic_search", 0xFF30D158, "Custom Topic…", sel < 0 ? "✓  " + cur : "", false, new Runnable() {
                                @Override public void run() {
                                    askText("Custom Topic", "For example: misty mountains", "", new TextResult() {
                                        @Override public void done(String t) {
                                            if (t.trim().length() < 2) { Toast.makeText(MainActivity.this, "Not a valid topic", Toast.LENGTH_SHORT).show(); return; }
                                            prefs.edit().putString("wp_topic", t.trim()).apply();
                                            useWallpaper(Wallpapers.MODE_ONLINE);
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            });
        }
        if ("unsplash".equals(source)) {
            String key = prefs.getString("unsplash_key", "");
            row("ic_tune", 0xFF8E8CFF, "Unsplash Key", key.isEmpty() ? "None (random photos)" : "Saved", false, new Runnable() {
                @Override public void run() {
                    askText("Unsplash Access Key", "From unsplash.com/developers (free)", prefs.getString("unsplash_key", ""), new TextResult() {
                        @Override public void done(String t) {
                            prefs.edit().putString("unsplash_key", t.trim()).apply();
                            useWallpaper(Wallpapers.MODE_ONLINE);
                        }
                    });
                }
            });
        }
        row("ic_picture", 0xFF0A84FF, "Picture Quality", prefs.getInt("wp_quality", 0) == 1 ? "4K (Ultra HD)" : "HD", true, new Runnable() {
            @Override public void run() {
                subPage("Picture Quality", new Runnable() {
                    @Override public void run() {
                        choices(new String[]{"HD (best for this TV)", "4K (Ultra HD, slower to download)"}, prefs.getInt("wp_quality", 0),
                                new ChoiceResult() {
                                    @Override public void chose(int k) {
                                        prefs.edit().putInt("wp_quality", k).apply();
                                        if (Wallpapers.MODE_ONLINE.equals(wallpapers.mode())) useWallpaper(Wallpapers.MODE_ONLINE);
                                    }
                                });
                    }
                });
            }
        });
        everyRow();
    }

    private static final String[] PALETTE_NAMES = {"Midnight Blue", "Navy", "Deep Purple", "Violet", "Magenta", "Rose",
            "Crimson", "Orange", "Amber", "Gold", "Olive", "Forest Green", "Teal", "Sky Blue", "Graphite", "Black", "White"};
    private static final int[] PALETTE = {0xFF0B1330, 0xFF14213D, 0xFF2A1458, 0xFF5B2A86, 0xFF9C2A8C, 0xFFE04F9F,
            0xFFA3123A, 0xFFF06A3C, 0xFFF2A900, 0xFFC9952C, 0xFF5A6B2A, 0xFF1D4D2B, 0xFF1E7F7A, 0xFF4FA3E0, 0xFF2A2D33,
            0xFF000000, 0xFFF2F2F2};

    private void pageGradient() {
        final boolean active = Wallpapers.MODE_GRADIENT.equals(wallpapers.mode());
        final int preset = prefs.getInt("grad_preset", 0);
        for (int i = 0; i < WallArt.PRESETS.length; i++) {
            final int k = i;
            row(null, 0, WallArt.PRESETS[i][0], active && preset == i ? "✓" : "", false, new Runnable() {
                @Override public void run() { prefs.edit().putInt("grad_preset", k).apply(); useWallpaper(Wallpapers.MODE_GRADIENT); }
            });
        }
        row("ic_tune", 0xFFFF9F0A, "Your Own Colours", active && preset < 0 ? "✓" : "", true, new Runnable() {
            @Override public void run() {
                prefs.edit().putInt("grad_preset", -1).apply();
                useWallpaper(Wallpapers.MODE_GRADIENT);
                subPage("Your Own Colours", new Runnable() { @Override public void run() { pageGradientCustom(); } });
            }
        });
    }

    private void pageGradientCustom() {
        final int count = Math.max(1, Math.min(3, prefs.getInt("grad_count", 2)));
        row("ic_tune", 0xFF30D158, "Number of Colours", count == 1 ? "1 (Solid Colour)" : String.valueOf(count), true, new Runnable() {
            @Override public void run() {
                subPage("Number of Colours", new Runnable() {
                    @Override public void run() {
                        choices(new String[]{"1 (Solid Colour)", "2", "3"}, count - 1, new ChoiceResult() {
                            @Override public void chose(int k) {
                                prefs.edit().putInt("grad_count", k + 1).apply();
                                useWallpaper(Wallpapers.MODE_GRADIENT);
                            }
                        });
                    }
                });
            }
        });
        final String[] keys = {"grad_c1", "grad_c2", "grad_c3"};
        final int[] defs = {0xFF14213D, 0xFF5B2A86, 0xFFE04F9F};
        for (int c = 0; c < count; c++) {
            final String key = keys[c];
            final int def = defs[c];
            int cur = prefs.getInt(key, def), sel = -1;
            for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == cur) sel = i;
            row("ic_picture", cur, "Colour " + (c + 1), sel >= 0 ? PALETTE_NAMES[sel] : "Custom", true, new Runnable() {
                @Override public void run() {
                    subPage("Colour", new Runnable() {
                        @Override public void run() {
                            int cur2 = prefs.getInt(key, def), sel2 = -1;
                            for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == cur2) sel2 = i;
                            choices(PALETTE_NAMES, sel2, new ChoiceResult() {
                                @Override public void chose(int k) {
                                    prefs.edit().putInt(key, PALETTE[k]).apply();
                                    useWallpaper(Wallpapers.MODE_GRADIENT);
                                }
                            });
                        }
                    });
                }
            });
        }
        if (count > 1) {
            int st = Math.max(0, Math.min(WallArt.STYLES.length - 1, prefs.getInt("grad_style", 0)));
            row("ic_tune", 0xFF0A84FF, "Gradient Style", WallArt.STYLES[st], true, new Runnable() {
                @Override public void run() {
                    subPage("Gradient Style", new Runnable() {
                        @Override public void run() {
                            choices(WallArt.STYLES, prefs.getInt("grad_style", 0), new ChoiceResult() {
                                @Override public void chose(int k) {
                                    prefs.edit().putInt("grad_style", k).apply();
                                    useWallpaper(Wallpapers.MODE_GRADIENT);
                                }
                            });
                        }
                    });
                }
            });
        }
    }

    private void pageEmoji() {
        final boolean active = Wallpapers.MODE_EMOJI.equals(wallpapers.mode());
        final int sel = prefs.getInt("emoji_index", 0);
        String[] sizes = {"Small", "Medium", "Large"};
        final int size = Math.max(0, Math.min(2, prefs.getInt("emoji_size", 1)));
        row("ic_tune", 0xFF30D158, "Size", sizes[size], true, new Runnable() {
            @Override public void run() {
                subPage("Size", new Runnable() {
                    @Override public void run() {
                        choices(new String[]{"Small", "Medium", "Large"}, prefs.getInt("emoji_size", 1), new ChoiceResult() {
                            @Override public void chose(int k) {
                                prefs.edit().putInt("emoji_size", k).apply();
                                if (Wallpapers.MODE_EMOJI.equals(wallpapers.mode())) useWallpaper(Wallpapers.MODE_EMOJI);
                            }
                        });
                    }
                });
            }
        });
        row("ic_person", 0xFFFFD60A, "Your Own Emoji…", active && sel < 0 ? "✓  " + prefs.getString("emoji_custom", "") : "", false, new Runnable() {
            @Override public void run() {
                askText("Your Own Emoji", "Type or paste an emoji", prefs.getString("emoji_custom", ""), new TextResult() {
                    @Override public void done(String t) {
                        t = t.trim();
                        if (t.isEmpty()) return;
                        int end = t.offsetByCodePoints(0, Math.min(t.codePointCount(0, t.length()), 4));
                        prefs.edit().putInt("emoji_index", -1).putString("emoji_custom", t.substring(0, end)).apply();
                        useWallpaper(Wallpapers.MODE_EMOJI);
                    }
                });
            }
        });
        for (int i = 0; i < WallArt.EMOJI.length; i++) {
            final int k = i;
            row(null, 0, WallArt.EMOJI[i][1] + "   " + WallArt.EMOJI[i][0], active && sel == i ? "✓" : "", false, new Runnable() {
                @Override public void run() { prefs.edit().putInt("emoji_index", k).apply(); useWallpaper(Wallpapers.MODE_EMOJI); }
            });
        }
    }

    private void pageFractal() {
        final boolean active = Wallpapers.MODE_FRACTAL.equals(wallpapers.mode());
        final int ribs = prefs.getInt("fractal_ribs", 18);
        row("ic_tune", 0xFF30D158, "Glass Ribs", ribs <= 12 ? "Wide" : ribs >= 28 ? "Fine" : "Medium", true, new Runnable() {
            @Override public void run() {
                subPage("Glass Ribs", new Runnable() {
                    @Override public void run() {
                        final int[] vals = {12, 18, 28};
                        int r = prefs.getInt("fractal_ribs", 18);
                        choices(new String[]{"Wide", "Medium", "Fine"}, r <= 12 ? 0 : r >= 28 ? 2 : 1, new ChoiceResult() {
                            @Override public void chose(int k) {
                                prefs.edit().putInt("fractal_ribs", vals[k]).apply();
                                if (Wallpapers.MODE_FRACTAL.equals(wallpapers.mode())) useWallpaper(Wallpapers.MODE_FRACTAL);
                            }
                        });
                    }
                });
            }
        });
        int sel = prefs.getInt("fractal_preset", 0);
        for (int i = 0; i < WallArt.PRESETS.length; i++) {
            final int k = i;
            row(null, 0, WallArt.PRESETS[i][0], active && sel == i ? "✓" : "", false, new Runnable() {
                @Override public void run() { prefs.edit().putInt("fractal_preset", k).apply(); useWallpaper(Wallpapers.MODE_FRACTAL); }
            });
        }
    }

    // ================================================================== Build 2: TV controls

    private List<TvTools.Network> wifiScan, wifiSaved;
    private String wifiStatus;
    private final List<android.bluetooth.BluetoothDevice> btFound = new ArrayList<>();
    private boolean btScanning;
    private android.content.BroadcastReceiver btReceiver;
    private String adTest;

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void note(String text) {
        TextView help = new UiStyle.FontText(this);
        help.setText(text);
        help.setTextSize(14);
        help.setTextColor(0x99FFFFFF);
        help.setLineSpacing(0, 1.2f);
        help.setPadding(dp(16), dp(10), dp(16), dp(4));
        settingsList.addView(help);
    }

    private void header(String text) {
        TextView h = new UiStyle.FontText(this);
        h.setText(text.toUpperCase(Locale.ROOT));
        h.setTextSize(12);
        h.setLetterSpacing(0.08f);
        h.setTextColor(0x80FFFFFF);
        h.setPadding(dp(16), dp(14), dp(16), dp(2));
        settingsList.addView(h);
    }

    private void confirm(String title, String message, String yes, final Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(yes, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { action.run(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Runs commands on the TV and shows a short result. */
    private void tvRun(final String done, final Runnable after, String... commands) {
        TvTools.run(this, new TvTools.Result() {
            @Override public void done(String out, Exception e) {
                if (e != null) toast(TvTools.why(e));
                else if (out != null && (out.toLowerCase(Locale.ROOT).contains("exception") || out.toLowerCase(Locale.ROOT).startsWith("error")
                        || out.toLowerCase(Locale.ROOT).contains("failure"))) toast("That could not be changed: " + out.split("\n")[0]);
                else if (done != null) toast(done);
                if (after != null) after.run();
            }
        }, commands);
    }

    private void pageControls() {
        String wifi = TvTools.currentWifi(this);
        row("ic_wifi", 0xFF0A84FF, "Wi-Fi", wifi == null ? "" : wifi, true, new Runnable() {
            @Override public void run() { subPage("Wi-Fi", new Runnable() { @Override public void run() { pageWifi(); } }); }
        });
        row("ic_bluetooth", 0xFF0A84FF, "Bluetooth", "", true, new Runnable() {
            @Override public void run() { subPage("Bluetooth", new Runnable() { @Override public void run() { pageBluetooth(); } }); }
        });
        row("ic_hidden", 0xFF30D158, "Private DNS & Ad Blocking", TvTools.dnsName(TvTools.dnsNow(this)), true, new Runnable() {
            @Override public void run() { subPage("Private DNS & Ad Blocking", new Runnable() { @Override public void run() { pageDns(); } }); }
        });
        row("ic_apps", 0xFFFF9F0A, "TV Debloat", "", true, new Runnable() {
            @Override public void run() { subPage("TV Debloat", new Runnable() { @Override public void run() { pageDebloat(); } }); }
        });
        row("ic_moon", 0xFFBF5AF2, "Power & Sleep", "", true, new Runnable() {
            @Override public void run() { subPage("Power & Sleep", new Runnable() { @Override public void run() { pagePower(); } }); }
        });
        row("ic_clock", 0xFFFFD60A, "Date & Time", "", true, new Runnable() {
            @Override public void run() { subPage("Date & Time", new Runnable() { @Override public void run() { pageDateTime(); } }); }
        });
        row("ic_tune", 0xFF64D2FF, "System Text Size", "", true, new Runnable() {
            @Override public void run() { subPage("System Text Size", new Runnable() { @Override public void run() { pageFontScale(); } }); }
        });
        row("ic_storage", 0xFFD1D1D6, "Device Info", Build.MODEL, true, new Runnable() {
            @Override public void run() { subPage("Device Info", new Runnable() { @Override public void run() { pageDevice(); } }); }
        });
        row("ic_hidden", 0xFFFF375F, "VPN", "", true, new Runnable() {
            @Override public void run() { subPage("VPN", new Runnable() { @Override public void run() { pageVpn(); } }); }
        });
        row("gear", 0xFFD1D1D6, "The TV's Own Settings", "", true, new Runnable() {
            @Override public void run() { subPage("The TV's Own Settings", new Runnable() { @Override public void run() { pageSystemSettings(); } }); }
        });
        row("ic_remote", 0xFF8E8CFF, "ADB Tools", TvTools.adbOn(this) ? "Debugging on" : "Debugging off", true, new Runnable() {
            @Override public void run() { subPage("ADB Tools", new Runnable() { @Override public void run() { pageAdb(); } }); }
        });
        final boolean admin = isAdmin();
        row("ic_person", 0xFFFF9F0A, "Device Administrator", admin ? "On" : "Off", false, new Runnable() {
            @Override public void run() { toggleAdmin(); }
        });
    }

    // ---------- Wi-Fi ----------

    private void pageWifi() {
        String cur = TvTools.currentWifi(this);
        row("ic_wifi", 0xFF30D158, "Connected Network", cur == null ? "None" : cur, false, new Runnable() {
            @Override public void run() { }
        });
        row("ic_search", 0xFF0A84FF, wifiStatus != null ? wifiStatus : "Scan for Networks", "", false, new Runnable() {
            @Override public void run() { scanWifi(); }
        });
        if (wifiScan != null) {
            header("Available Networks");
            if (wifiScan.isEmpty()) note("No networks found");
            for (final TvTools.Network n : wifiScan) {
                String bars = n.level >= -55 ? "Strong" : n.level >= -67 ? "Good" : n.level >= -78 ? "Fair" : "Weak";
                row(null, 0, n.ssid, (n.ssid.equals(cur) ? "Connected  ·  " : "") + bars + ("open".equals(n.security) ? "" : "  ·  Secured"),
                        false, new Runnable() {
                    @Override public void run() { joinWifi(n); }
                });
            }
        }
        row("ic_wifi", 0xFFFF9F0A, "Saved Networks", "", true, new Runnable() {
            @Override public void run() { subPage("Saved Networks", new Runnable() { @Override public void run() { pageSavedWifi(); } }); }
        });
        row("ic_search", 0xFF64D2FF, "Add Network…", "", false, new Runnable() {
            @Override public void run() {
                askText("Add Network", "Network name (SSID)", "", new TextResult() {
                    @Override public void done(String ssid) {
                        TvTools.Network n = new TvTools.Network();
                        n.ssid = ssid.trim();
                        n.security = "wpa2";
                        if (!n.ssid.isEmpty()) joinWifi(n);
                    }
                });
            }
        });
        row("gear", 0xFFD1D1D6, "Wi-Fi Settings", "", true, new Runnable() {
            @Override public void run() { startOr(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        });
    }

    private void scanWifi() {
        wifiStatus = "Scanning…";
        refreshPage();
        TvTools.run(this, new TvTools.Result() {
            @Override public void done(String out, Exception e) {
                wifiStatus = null;
                if (e != null) { toast(TvTools.why(e)); refreshPage(); return; }
                wifiScan = TvTools.parseScan(out);
                refreshPage();
            }
        }, "cmd wifi start-scan; sleep 4; cmd wifi list-scan-results");
    }

    private void joinWifi(final TvTools.Network n) {
        if ("open".equals(n.security)) {
            tvRun("Connecting to " + n.ssid + "…", null, "cmd wifi connect-network " + TvTools.q(n.ssid) + " open");
            return;
        }
        askText("Password for " + n.ssid, "Wi-Fi password", "", new TextResult() {
            @Override public void done(String pw) {
                String type = "wpa3".equals(n.security) ? "wpa3" : "wep".equals(n.security) ? "wep" : "wpa2";
                tvRun("Connecting to " + n.ssid + "…", new Runnable() {
                    @Override public void run() { ui.postDelayed(new Runnable() { @Override public void run() { refreshPage(); } }, 5000); }
                }, "cmd wifi connect-network " + TvTools.q(n.ssid) + " " + type + " " + TvTools.q(pw));
            }
        });
    }

    private void pageSavedWifi() {
        if (wifiSaved == null) {
            note("Fetching…");
            TvTools.run(this, new TvTools.Result() {
                @Override public void done(String out, Exception e) {
                    if (e != null) { toast(TvTools.why(e)); wifiSaved = new ArrayList<>(); }
                    else wifiSaved = TvTools.parseSaved(out);
                    refreshPage();
                }
            }, "cmd wifi list-networks");
            return;
        }
        if (wifiSaved.isEmpty()) note("No saved networks, or this TV doesn't list them.");
        for (final TvTools.Network n : wifiSaved) {
            row(null, 0, n.ssid, n.security, false, new Runnable() {
                @Override public void run() {
                    confirm("Forget " + n.ssid + "?", "The TV will stop joining this network until you enter the password again.",
                            "Forget Network", new Runnable() {
                                @Override public void run() {
                                    tvRun("Forgotten", new Runnable() {
                                        @Override public void run() { wifiSaved = null; refreshPage(); }
                                    }, "cmd wifi forget-network " + n.id);
                                }
                            });
                }
            });
        }
    }

    // ---------- Bluetooth ----------

    private void pageBluetooth() {
        final android.bluetooth.BluetoothAdapter a = TvTools.bt();
        if (a == null) { note("This TV has no Bluetooth."); return; }
        header("Paired");
        List<android.bluetooth.BluetoothDevice> paired = TvTools.paired();
        if (paired.isEmpty()) note("None yet");
        for (final android.bluetooth.BluetoothDevice d : paired) {
            row("ic_bluetooth", 0xFF0A84FF, TvTools.name(d), "Paired", false, new Runnable() {
                @Override public void run() {
                    confirm("Forget " + TvTools.name(d) + "?", "To use it again, put it in pairing mode and search.", "Forget Device",
                            new Runnable() {
                                @Override public void run() {
                                    if (!TvTools.forget(d)) toast("Forget it in the TV's Bluetooth settings");
                                    ui.postDelayed(new Runnable() { @Override public void run() { refreshPage(); } }, 800);
                                }
                            });
                }
            });
        }
        header("Nearby Devices");
        row("ic_search", 0xFF30D158, btScanning ? "Searching…" : "Search and Pair", "", false, new Runnable() {
            @Override public void run() { scanBluetooth(); }
        });
        for (final android.bluetooth.BluetoothDevice d : btFound) {
            boolean isPaired = false;
            try { isPaired = d.getBondState() == android.bluetooth.BluetoothDevice.BOND_BONDED; } catch (Throwable ignored) { }
            if (isPaired) continue;
            row(null, 0, TvTools.name(d), "Pair", false, new Runnable() {
                @Override public void run() {
                    try {
                        a.cancelDiscovery();
                        d.createBond();
                        toast("Pairing with " + TvTools.name(d) + "…");
                    } catch (Throwable t) { toast("Pair it in the TV's Bluetooth settings"); }
                }
            });
        }
        row("gear", 0xFFD1D1D6, "Remotes & Accessories (TV Settings)", "", true, new Runnable() {
            @Override public void run() { ccAction("Bluetooth"); }
        });
    }

    private void scanBluetooth() {
        final android.bluetooth.BluetoothAdapter a = TvTools.bt();
        if (a == null) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            toast("Allow Bluetooth access to search for devices.");
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 61);
            return;
        }
        try {
            if (!a.isEnabled()) a.enable();
            stopBluetoothScan();
            btFound.clear();
            btReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, Intent i) {
                    if (android.bluetooth.BluetoothDevice.ACTION_FOUND.equals(i.getAction())) {
                        android.bluetooth.BluetoothDevice d = i.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);
                        if (d != null && !btFound.contains(d)) { btFound.add(d); if (settingsOpen) refreshPage(); }
                    } else if (android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(i.getAction())) {
                        btScanning = false;
                        if (btFound.isEmpty()) toast("No devices found. Put the device in pairing mode and try again.");
                        stopBluetoothScan();
                        if (settingsOpen) refreshPage();
                    } else if (android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(i.getAction())) {
                        if (settingsOpen) refreshPage();
                    }
                }
            };
            android.content.IntentFilter f = new android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND);
            f.addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            f.addAction(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            registerReceiver(btReceiver, f);
            btScanning = a.startDiscovery();
            refreshPage();
        } catch (Throwable t) {
            toast("Search and pair in the TV's Bluetooth settings");
        }
    }

    private void stopBluetoothScan() {
        if (btReceiver != null) {
            try { unregisterReceiver(btReceiver); } catch (Throwable ignored) { }
            btReceiver = null;
        }
    }

    // ---------- Private DNS ----------

    private void pageDns() {
        String cur = TvTools.dnsNow(this);
        boolean known = false;
        for (final String[] d : TvTools.DNS) {
            boolean on = d[1].equals(cur);
            known |= on;
            row(null, 0, d[0], on ? "✓" : d[2], false, new Runnable() {
                @Override public void run() { applyDns(d[1]); }
            });
        }
        row("ic_tune", 0xFF64D2FF, "Custom DNS…", !known ? "✓  " + cur : "", false, new Runnable() {
            @Override public void run() {
                askText("Custom DNS", "e.g. dns.google or one.one.one.one", "", new TextResult() {
                    @Override public void done(String t) {
                        t = t.trim().toLowerCase(Locale.ROOT);
                        if (t.matches("^[0-9.:]+$")) { toast("Android needs a hostname, like dns.google, not an IP address"); return; }
                        if (!t.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")) { toast("That is not a valid hostname"); return; }
                        applyDns(t);
                    }
                });
            }
        });
        header("Ad Blocking Test");
        row("ic_search", 0xFF30D158, adTest == null ? "Test Ad Blocking" : adTest, adTest == null ? "" : "Test Again", false, new Runnable() {
            @Override public void run() {
                adTest = "Testing…";
                refreshPage();
                new Thread(new Runnable() {
                    @Override public void run() {
                        final int[] r = TvTools.testAds();
                        ui.post(new Runnable() {
                            @Override public void run() {
                                adTest = r[0] == 0 ? "Not blocked (0 of " + r[1] + " ad domains)"
                                        : r[0] + " of " + r[1] + " ad domains blocked";
                                refreshPage();
                            }
                        });
                    }
                }).start();
            }
        });
        note("Private DNS (DNS over TLS) works for the whole TV, in every app. It blocks ads on websites and in many apps; "
                + "it can't remove ads that come from the same address as the video, such as YouTube's.");
    }

    private void applyDns(final String host) {
        TvTools.setDns(this, host, new TvTools.Result() {
            @Override public void done(String out, Exception e) {
                if (e != null) toast("Could not change the DNS. " + TvTools.why(e));
                else toast(host.isEmpty() ? "Private DNS: Automatic" : "Private DNS: " + TvTools.dnsName(host));
                adTest = null;
                refreshPage();
            }
        });
    }

    // ---------- TV Debloat ----------

    private void pageDebloat() {
        row("ic_apps", 0xFF30D158, "Downloaded Apps", "", true, new Runnable() {
            @Override public void run() { subPage("Downloaded Apps", new Runnable() { @Override public void run() { pageAppList(false); } }); }
        });
        row("ic_apps", 0xFFFF9F0A, "System Apps", "", true, new Runnable() {
            @Override public void run() { subPage("System Apps", new Runnable() { @Override public void run() { pageAppList(true); } }); }
        });
        java.util.Set<String> removed = prefs.getStringSet("removed_pkgs", new java.util.HashSet<String>());
        row("ic_download", 0xFF0A84FF, "Restore Removed Apps", removed.isEmpty() ? "None" : String.valueOf(removed.size()), true, new Runnable() {
            @Override public void run() { subPage("Restore Removed Apps", new Runnable() { @Override public void run() { pageRestoreApps(); } }); }
        });
        note("Disable switches an app off and keeps everything: Enable brings it back at once. Remove takes a system app "
                + "away for you (a factory reset, or Restore here, brings it back). Remove one app at a time and check the TV still works.");
    }

    private void pageAppList(final boolean system) {
        for (final TvTools.AppItem a : TvTools.apps(this, system)) {
            row(null, 0, a.label, (a.enabled ? "" : "Disabled  ·  ") + a.verdict, false, new Runnable() {
                @Override public void run() { appActions(a); }
            });
        }
    }

    private void appActions(final TvTools.AppItem a) {
        final List<String> opts = new ArrayList<>();
        final boolean needed = "Needed by the TV".equals(a.verdict);
        if (!needed) opts.add(a.enabled ? "Disable (can be switched back on)" : "Enable Again");
        if (!needed) opts.add(a.system ? "Remove" : "Uninstall");
        opts.add("Open App Info");
        String why = needed ? "Needed by the TV: removing it can stop the TV working, so TV Home doesn't offer it."
                : "Safe to remove".equals(a.verdict) ? "A pre-installed extra that most people do not use."
                : "Caution".equals(a.verdict) ? "A system app. Other apps or TV features may depend on it."
                : "An app you downloaded.";
        new AlertDialog.Builder(this).setTitle(a.label + "\n" + why)
                .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String o = opts.get(which);
                        final Runnable back = new Runnable() { @Override public void run() { refreshPage(); } };
                        if (o.startsWith("Disable")) {
                            tvRun(a.label + " disabled", back, "pm disable-user --user 0 " + a.pkg);
                        } else if (o.startsWith("Enable")) {
                            tvRun(a.label + " enabled", back, "pm enable --user 0 " + a.pkg);
                        } else if (o.equals("Remove")) {
                            confirm("Remove " + a.label + "?", "Restore Removed Apps (or a factory reset) brings it back.", "Remove", new Runnable() {
                                @Override public void run() {
                                    java.util.Set<String> s = new java.util.HashSet<>(prefs.getStringSet("removed_pkgs", new java.util.HashSet<String>()));
                                    s.add(a.pkg + "\t" + a.label);
                                    prefs.edit().putStringSet("removed_pkgs", s).apply();
                                    tvRun(a.label + " removed", back, "pm uninstall -k --user 0 " + a.pkg);
                                }
                            });
                        } else if (o.equals("Uninstall")) {
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                            } catch (Exception e) {
                                tvRun(a.label + " uninstalled", back, "pm uninstall " + a.pkg);
                            }
                        } else {
                            startOr(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                        }
                    }
                }).show();
    }

    private void pageRestoreApps() {
        final java.util.Set<String> removed = prefs.getStringSet("removed_pkgs", new java.util.HashSet<String>());
        if (removed.isEmpty()) { note("Nothing has been removed from here yet."); return; }
        row("ic_download", 0xFF30D158, "Restore All", "", false, new Runnable() {
            @Override public void run() {
                List<String> cmds = new ArrayList<>();
                for (String s : removed) cmds.add("pm install-existing --user 0 " + s.split("\t")[0]);
                tvRun("Restored", new Runnable() {
                    @Override public void run() { prefs.edit().remove("removed_pkgs").apply(); refreshPage(); }
                }, cmds.toArray(new String[0]));
            }
        });
        for (final String s : removed) {
            final String[] p = s.split("\t");
            row(null, 0, p.length > 1 ? p[1] : p[0], "Restore", false, new Runnable() {
                @Override public void run() {
                    tvRun("Restored " + (p.length > 1 ? p[1] : p[0]), new Runnable() {
                        @Override public void run() {
                            java.util.Set<String> n = new java.util.HashSet<>(prefs.getStringSet("removed_pkgs", new java.util.HashSet<String>()));
                            n.remove(s);
                            prefs.edit().putStringSet("removed_pkgs", n).apply();
                            refreshPage();
                        }
                    }, "pm install-existing --user 0 " + p[0]);
                }
            });
        }
    }

    // ---------- Power & sleep ----------

    private void pagePower() {
        row("ic_moon", 0xFFBF5AF2, "Sleep TV Now", "", false, new Runnable() {
            @Override public void run() { closeSettings(false); tvRun(null, null, "input keyevent 223"); }
        });
        row("ic_moon", 0xFF64D2FF, "Start Screen Saver", "", false, new Runnable() {
            @Override public void run() { closeSettings(false); ccAction("Screensaver"); }
        });
        row("ic_download", 0xFFFF9F0A, "Restart TV", "", false, new Runnable() {
            @Override public void run() {
                confirm("Restart the TV?", "It turns off and on again, which takes about a minute.", "Restart", new Runnable() {
                    @Override public void run() { tvRun(null, null, "svc power reboot || reboot"); }
                });
            }
        });
        final String[] saverNames = {"5 minutes", "15 minutes", "30 minutes", "1 hour", "Never"};
        final int[] saverMs = {300000, 900000, 1800000, 3600000, 2147483647};
        row("ic_clock", 0xFFFFD60A, "Screen Saver Starts After", "", true, new Runnable() {
            @Override public void run() {
                subPage("Screen Saver Starts After", new Runnable() {
                    @Override public void run() {
                        choices(saverNames, -1, new ChoiceResult() {
                            @Override public void chose(int k) {
                                tvRun("Screen saver: " + saverNames[k], null, "settings put system screen_off_timeout " + saverMs[k]);
                            }
                        });
                    }
                });
            }
        });
        final String[] offNames = {"1 hour", "2 hours", "4 hours", "8 hours", "Never"};
        final long[] offMs = {3600000L, 7200000L, 14400000L, 28800000L, -1L};
        row("ic_moon", 0xFFFF375F, "Screen Off After", "", true, new Runnable() {
            @Override public void run() {
                subPage("Screen Off After", new Runnable() {
                    @Override public void run() {
                        choices(offNames, -1, new ChoiceResult() {
                            @Override public void chose(int k) {
                                tvRun("Screen off after: " + offNames[k], null,
                                        "settings put secure sleep_timeout " + offMs[k],
                                        "settings put secure attentive_timeout " + (offMs[k] < 0 ? 0 : offMs[k]));
                            }
                        });
                    }
                });
            }
        });
        boolean stay = false;
        try { stay = Settings.Global.getInt(getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0; } catch (Exception ignored) { }
        final boolean stayNow = stay;
        row("ic_tune", 0xFF30D158, "Stay Awake While Charging", stay ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                tvRun(null, new Runnable() { @Override public void run() { refreshPage(); } },
                        "settings put global stay_on_while_plugged_in " + (stayNow ? 0 : 7));
            }
        });
    }

    // ---------- Date & time ----------

    private void pageDateTime() {
        boolean autoTime = false, autoZone = false;
        try {
            autoTime = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
            autoZone = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 1) == 1;
        } catch (Exception ignored) { }
        final boolean t = autoTime, z = autoZone;
        row("ic_clock", 0xFFFFD60A, "Set Time Automatically", t ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                tvRun(null, new Runnable() { @Override public void run() { refreshPage(); } }, "settings put global auto_time " + (t ? 0 : 1));
            }
        });
        row("ic_weather", 0xFF64D2FF, "Set Time Zone Automatically", z ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                tvRun(null, new Runnable() { @Override public void run() { refreshPage(); } }, "settings put global auto_time_zone " + (z ? 0 : 1));
            }
        });
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        row("ic_weather", 0xFF30D158, "Time Zone", tz.getDisplayName(false, java.util.TimeZone.SHORT) + "  ·  " + tz.getID(), false,
                new Runnable() { @Override public void run() { startOr(new Intent(Settings.ACTION_DATE_SETTINGS)); } });
        lookChoice("ic_clock", 0xFFFF9F0A, "Clock on the Home Screen", CLOCK_NAMES, "clock_24", 0, true);
        row("gear", 0xFFD1D1D6, "Set Date, Time & Zone (TV Settings)", "", true, new Runnable() {
            @Override public void run() { startOr(new Intent(Settings.ACTION_DATE_SETTINGS)); }
        });
    }

    private void pageFontScale() {
        final String[] names = {"Small", "Default", "Large", "Largest"};
        final String[] vals = {"0.85", "1.0", "1.15", "1.3"};
        float cur = getResources().getConfiguration().fontScale;
        int sel = cur < 0.95f ? 0 : cur < 1.1f ? 1 : cur < 1.25f ? 2 : 3;
        choices(names, sel, new ChoiceResult() {
            @Override public void chose(int k) { tvRun("System text size: " + names[k], null, "settings put system font_scale " + vals[k]); }
        });
        note("Changes the text size in the TV's own menus and in most apps.");
    }

    // ---------- Device ----------

    private void pageDevice() {
        for (String[] r : TvTools.deviceInfo(this)) {
            row(null, 0, r[0], r[1], false, new Runnable() { @Override public void run() { } });
        }
        row("gear", 0xFFD1D1D6, "About on the TV", "", true, new Runnable() {
            @Override public void run() { startOr(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)); }
        });
    }

    private void pageVpn() {
        row("gear", 0xFFD1D1D6, "TV's VPN Screen", "", true, new Runnable() {
            @Override public void run() { startOr(new Intent(Settings.ACTION_VPN_SETTINGS)); }
        });
        header("Free VPN apps");
        final String[][] vpns = {
                {"Proton VPN", "ch.protonvpn.android", "Free plan, no ads or data limit"},
                {"Riseup VPN", "se.leap.riseupvpn", "Free, run by a volunteer collective"},
                {"WireGuard", "com.wireguard.android", "Fast open-source tunnel; needs your own server"},
        };
        for (final String[] v : vpns) {
            boolean have = false;
            try { pm.getPackageInfo(v[1], 0); have = true; } catch (Exception ignored) { }
            final boolean installed = have;
            row(null, 0, v[0], installed ? "Open" : v[2], false, new Runnable() {
                @Override public void run() {
                    if (installed) {
                        Intent i = pm.getLeanbackLaunchIntentForPackage(v[1]);
                        if (i == null) i = pm.getLaunchIntentForPackage(v[1]);
                        if (i != null) startOr(i);
                    } else {
                        startOr(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + v[1])));
                    }
                }
            });
        }
    }

    private void pageSystemSettings() {
        final String[][] pages = {
                {"Network & Internet", Settings.ACTION_WIFI_SETTINGS},
                {"Display Settings", Settings.ACTION_DISPLAY_SETTINGS},
                {"Picture & Resolution", "android.settings.PICTURE_SETTINGS"},
                {"Sound Settings", Settings.ACTION_SOUND_SETTINGS},
                {"Sound Modes & Output", "com.android.tv.settings.SOUND_SETTINGS"},
                {"Storage Settings", Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
                {"Accessibility Settings", Settings.ACTION_ACCESSIBILITY_SETTINGS},
                {"App Settings", Settings.ACTION_APPLICATION_SETTINGS},
                {"Default Apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"},
                {"Date & Time", Settings.ACTION_DATE_SETTINGS},
                {"Developer Options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
                {"Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS},
                {"Languages", Settings.ACTION_LOCALE_SETTINGS},
                {"Privacy", Settings.ACTION_PRIVACY_SETTINGS},
                {"All Settings", Settings.ACTION_SETTINGS},
        };
        for (final String[] p : pages) {
            row(null, 0, p[0], "", true, new Runnable() { @Override public void run() { startOr(new Intent(p[1])); } });
        }
    }

    // ---------- ADB tools and device administrator ----------

    private void pageAdb() {
        final boolean on = TvTools.adbOn(this);
        row("ic_remote", 0xFF30D158, "Debugging", on ? "On" : "Off", false, new Runnable() {
            @Override public void run() { if (!on) startOr(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
        });
        row("ic_search", 0xFF0A84FF, "Check Connection", "", false, new Runnable() {
            @Override public void run() {
                toast("Connecting… if the TV asks, tick Always allow and choose Allow.");
                tvRun("Connected: ADB Tools are ready", null, "echo ok");
            }
        });
        boolean rights = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
        row("ic_person", 0xFFFF9F0A, "Give TV Home System Rights", rights ? "Done" : "", false, new Runnable() {
            @Override public void run() {
                tvRun("System rights given", new Runnable() { @Override public void run() { refreshPage(); } },
                        "pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS",
                        "appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow",
                        "appops set " + getPackageName() + " GET_USAGE_STATS allow");
            }
        });
        row("ic_download", 0xFF30D158, "Make TV Home the Home App", prefs.getBoolean("adb_setup_ok", false) ? "Done" : "", true, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, SetupActivity.class).putExtra("step", 2)); }
        });
        if (on) {
            row("ic_hidden", 0xFFFF375F, "Turn Off Debugging", "", false, new Runnable() {
                @Override public void run() {
                    confirm("Turn off debugging?", "Turning debugging off stops ADB Tools from working until it is switched on again "
                            + "in Developer options.", "Turn Off", new Runnable() {
                        @Override public void run() { tvRun(null, null, "settings put global adb_enabled 0"); }
                    });
                }
            });
        }
        note("ADB Tools use the TV's own debugging connection (127.0.0.1), the same one a computer uses. TV Home only sends "
                + "the commands for the setting you choose.");
    }

    private boolean isAdmin() {
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            return dpm.isAdminActive(new android.content.ComponentName(this, AdminReceiver.class));
        } catch (Exception e) { return false; }
    }

    private void toggleAdmin() {
        final android.content.ComponentName me = new android.content.ComponentName(this, AdminReceiver.class);
        if (isAdmin()) {
            confirm("Turn off Device Administrator?", "TV Home can then be uninstalled.", "Turn Off", new Runnable() {
                @Override public void run() {
                    try {
                        ((android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE)).removeActiveAdmin(me);
                    } catch (Exception ignored) { }
                    ui.postDelayed(new Runnable() { @Override public void run() { refreshPage(); } }, 600);
                }
            });
            return;
        }
        confirm("Turn on Device Administrator?", "Keeps TV Home from being uninstalled by accident. It asks for no other control. "
                + "To uninstall later, switch it off here first.", "Turn On", new Runnable() {
            @Override public void run() {
                tvRun("TV Home is now a device administrator", new Runnable() { @Override public void run() { refreshPage(); } },
                        "dpm set-active-admin --user 0 " + me.flattenToShortString());
            }
        });
    }

    // ================================================================== Build 3: Control Centre, switcher, sidebar

    /** Control Centre tiles you can add: {key, icon, title, action, shape (w = wide, r = round)}. */
    static final String[][] CC_TILES = {
            {"inputs", "ic_input", "Inputs", "Inputs", "w"},
            {"settings", "gear", "Settings", "TV Home settings", "w"},
            {"recents", "ic_apps", "Recent Apps", "Recent Apps", "w"},
            {"weather", "ic_weather", "Weather", "Weather", "w"},
            {"dns", "ic_hidden", "Ad Blocking", "Ad Blocking", "w"},
            {"picture", "ic_picture", "Picture", "Picture", "w"},
            {"voice", "ic_mic", "Voice Search", "Voice search", "r"},
            {"mirror", "ic_cast", "Screen Mirror", "Screen Mirror", "r"},
            {"screensaver", "ic_moon", "Screen Saver", "Screensaver", "r"},
            {"more", "ic_apps", "More", "More", "r"},
            {"sleep", "ic_moon", "Sleep TV", "Sleep TV", "r"},
            {"search", "ic_search", "Search", "Search", "r"},
            {"mute", "ic_mute", "Mute", "Mute", "r"},
            {"sound", "ic_sound", "Sound Settings", "Sound", "r"},
    };
    static final String CC_DEFAULT = "inputs,settings,voice,mirror,screensaver,more";

    private List<String> ccKeys() {
        List<String> out = new ArrayList<>();
        for (String k : prefs.getString("cc_tiles", CC_DEFAULT).split(",")) if (ccTile(k) != null && !out.contains(k)) out.add(k);
        return out;
    }

    private static String[] ccTile(String key) {
        for (String[] t : CC_TILES) if (t[0].equals(key)) return t;
        return null;
    }

    /** Your chosen tiles: wide ones two to a row, round ones four to a row (at most two rows of each). */
    private void ccTiles(LinearLayout panel, int full, int col, int gapDp) {
        List<String[]> wide = new ArrayList<>(), round = new ArrayList<>();
        for (String k : ccKeys()) {
            String[] t = ccTile(k);
            if ("w".equals(t[4])) { if (wide.size() < 4) wide.add(t); } else if (round.size() < 8) round.add(t);
        }
        for (int i = 0; i < wide.size(); i += 2) {
            LinearLayout b2 = new LinearLayout(this);
            b2.setOrientation(LinearLayout.HORIZONTAL);
            b2.setClipChildren(false);
            for (int j = i; j < Math.min(i + 2, wide.size()); j++) {
                String[] t = wide.get(j);
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(col), dp(56));
                if (j == i) il.setMargins(0, 0, dp(gapDp), 0);
                b2.addView(ccBlock(t[1], t[2], t[3], CC_WIDE, false), il);
            }
            LinearLayout.LayoutParams b2l = new LinearLayout.LayoutParams(-2, -2);
            b2l.setMargins(0, 0, 0, dp(i + 2 >= wide.size() ? 12 : gapDp));
            panel.addView(b2, b2l);
        }
        int circle = 50, space = (full - 4 * circle) / 3;
        for (int i = 0; i < round.size(); i += 4) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setClipChildren(false);
            for (int j = i; j < Math.min(i + 4, round.size()); j++) {
                String[] t = round.get(j);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(circle), dp(circle));
                if (j < i + 3) rl.setMargins(0, 0, dp(space), 0);
                r.addView(ccBlock(t[1], t[2], t[3], CC_ROUND, false), rl);
            }
            LinearLayout.LayoutParams rll = new LinearLayout.LayoutParams(-2, -2);
            rll.setMargins(0, 0, 0, dp(12));
            panel.addView(r, rll);
        }
    }

    /** Actions for the newer tiles. Returns true when it handled one. */
    private boolean ccExtra(String label) {
        if (label.equals("Sleep TV")) { closeControlCentre(); tvRun(null, null, "input keyevent 223"); return true; }
        if (label.equals("Recent Apps")) { closeControlCentre(); openSwitcher(); return true; }
        if (label.equals("Search")) { closeControlCentre(); openSearch(); return true; }
        if (label.equals("Weather")) { closeControlCentre(); openWeather(); return true; }
        if (label.equals("Mute")) {
            if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
            updateVolume();
            return true;
        }
        if (label.equals("Ad Blocking")) {
            final boolean on = !TvTools.dnsNow(this).isEmpty();
            applyDns(on ? "" : TvTools.DNS[1][1]);
            return true;
        }
        return false;
    }

    // ---------- Notifications in Control Centre ----------

    private LinearLayout ccNotes;

    private void buildNotes() {
        ccNotes = new LinearLayout(this);
        ccNotes.setOrientation(LinearLayout.VERTICAL);
        ccNotes.setClipChildren(false);
        ccNotes.setClipToPadding(false);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(dp(280), -2, Gravity.TOP | Gravity.END);
        nl.setMargins(0, dp(52), dp(40 + 256 + 16), 0);
        ccOverlay.addView(ccNotes, nl);
    }

    private void fillNotes() {
        if (ccNotes == null) return;
        ccNotes.removeAllViews();
        if (!prefs.getBoolean("cc_notes", false)) return;
        List<android.service.notification.StatusBarNotification> list = NotifListener.current(getPackageName());
        if (!NotifListener.connected()) {
            TextView t = new UiStyle.FontText(this);
            t.setText("Notifications need access: Settings → Control Centre → Allow Notification Access");
            t.setTextSize(13);
            t.setTextColor(0xB3FFFFFF);
            t.setShadowLayer(dp(4), 0, 0, 0x80000000);
            ccNotes.addView(t);
            return;
        }
        int n = 0;
        for (final android.service.notification.StatusBarNotification s : list) {
            if (n++ >= 4) break;
            android.os.Bundle ex = s.getNotification().extras;
            CharSequence title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE);
            CharSequence text = ex.getCharSequence(android.app.Notification.EXTRA_TEXT);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(10), dp(14), dp(10));
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(ccFill());
            bg.setStroke(Math.max(1, dp(1)), 0x2EFFFFFF);
            card.setBackground(bg);
            card.setFocusable(true);
            card.setClickable(true);
            if (Build.VERSION.SDK_INT >= 26) card.setDefaultFocusHighlightEnabled(false);
            ImageView ic = new ImageView(this);
            try { ic.setImageDrawable(pm.getApplicationIcon(s.getPackageName())); } catch (Exception ignored) { }
            card.addView(ic, new LinearLayout.LayoutParams(dp(28), dp(28)));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(10), 0, 0, 0);
            final TextView tt = new UiStyle.FontText(this);
            tt.setText(title);
            tt.setTextSize(14);
            tt.setTextColor(Color.WHITE);
            tt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(tt);
            final TextView bt = new UiStyle.FontText(this);
            bt.setText(text == null ? "" : text);
            bt.setTextSize(12);
            bt.setTextColor(0xB3FFFFFF);
            bt.setMaxLines(2);
            bt.setEllipsize(TextUtils.TruncateAt.END);
            if (text != null && text.length() > 0) words.addView(bt);
            card.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    bg.setColor(has ? 0xFFF2F3F5 : ccFill());
                    tt.setTextColor(has ? 0xFF111317 : Color.WHITE);
                    bt.setTextColor(has ? 0xB3111317 : 0xB3FFFFFF);
                    v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(UiStyle.ms(200)).start();
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeControlCentre();
                    try {
                        android.app.PendingIntent pi = s.getNotification().contentIntent;
                        if (pi != null) pi.send();
                        if ((s.getNotification().flags & android.app.Notification.FLAG_AUTO_CANCEL) != 0) NotifListener.dismiss(s.getKey());
                    } catch (Exception ignored) { }
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    NotifListener.dismiss(s.getKey());
                    ui.postDelayed(new Runnable() { @Override public void run() { fillNotes(); } }, 300);
                    return true;
                }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.setMargins(0, 0, 0, dp(8));
            ccNotes.addView(card, cl);
        }
        muteClicks(ccNotes);
    }

    // ---------- Recent Apps switcher ----------

    private FrameLayout swOverlay;
    private LinearLayout swRow;
    private ImageView swBlur;
    private boolean swOpen;
    private long lastHomeIntent;

    private List<String> recentPackages() {
        List<String> out = new ArrayList<>();
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                android.app.usage.UsageStatsManager um = (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                List<android.app.usage.UsageStats> st = um.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, now - 3L * 86400000, now);
                java.util.Collections.sort(st, new java.util.Comparator<android.app.usage.UsageStats>() {
                    @Override public int compare(android.app.usage.UsageStats a, android.app.usage.UsageStats b) {
                        return Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed());
                    }
                });
                for (android.app.usage.UsageStats u : st) {
                    String p = u.getPackageName();
                    if (u.getLastTimeUsed() <= 0 || p.equals(getPackageName()) || !apps.containsKey(p) || out.contains(p)) continue;
                    out.add(p);
                    if (out.size() >= 12) break;
                }
            }
        } catch (Throwable ignored) { }
        if (out.isEmpty()) for (String p : recents()) if (apps.containsKey(p)) out.add(p);
        java.util.Set<String> gone = prefs.getStringSet("switcher_hidden", new java.util.HashSet<String>());
        out.removeAll(gone);
        return out;
    }

    private void buildSwitcher(FrameLayout outer) {
        swOverlay = new FrameLayout(this);
        swOverlay.setVisibility(View.INVISIBLE);
        swBlur = new ImageView(this);
        swBlur.setScaleType(ImageView.ScaleType.FIT_XY);
        swOverlay.addView(swBlur, new FrameLayout.LayoutParams(-1, -1));
        TextView title = new UiStyle.FontText(this);
        title.setText("Recent Apps");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.setMargins(dp(56), dp(48), 0, 0);
        swOverlay.addView(title, tl);
        TextView hint = new UiStyle.FontText(this);
        hint.setText("OK opens  ·  hold OK to close an app");
        hint.setTextSize(13);
        hint.setTextColor(0x99FFFFFF);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hl.setMargins(dp(56), 0, 0, dp(40));
        swOverlay.addView(hint, hl);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setClipChildren(false);
        swRow.setClipToPadding(false);
        swRow.setPadding(dp(56), dp(20), dp(56), dp(20));
        hs.addView(swRow);
        outer.addView(swOverlay, new FrameLayout.LayoutParams(-1, -1));
        swOverlay.addView(hs, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
    }

    private void openSwitcher() {
        if (swOpen) return;
        if (ccOpen) closeControlCentre();
        if (settingsOpen) closeSettings(false);
        final List<String> pkgs = recentPackages();
        if (pkgs.isEmpty()) { toast("No recent apps yet. For apps opened outside TV Home, allow Usage access in Settings → Startup & Override."); return; }
        swOpen = true;
        swRow.removeAllViews();
        java.io.File cards = new java.io.File(getCacheDir(), "cards");
        boolean shots = prefs.getBoolean("app_cards", false);
        View first = null;
        for (final String p : pkgs) {
            final App a = apps.get(p);
            if (a == null) continue;
            View card = switcherCard(a, shots ? new java.io.File(cards, p + ".jpg") : null);
            if (first == null) first = card;
        }
        Drawable backdrop = ccBackdrop();
        swBlur.setImageDrawable(backdrop);
        swBlur.setBackgroundColor(backdrop == null ? 0xE6000000 : 0);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        swOverlay.setVisibility(View.VISIBLE);
        swOverlay.setAlpha(0f);
        swOverlay.animate().alpha(1f).setDuration(UiStyle.ms(260)).setInterpolator(EASE).start();
        for (int i = 0; i < swRow.getChildCount(); i++) {
            View c = swRow.getChildAt(i);
            c.setTranslationX(dp(40));
            c.setAlpha(0f);
            c.animate().translationX(0).alpha(1f).setStartDelay(i * 30L).setDuration(UiStyle.ms(420)).setInterpolator(GLIDE).start();
        }
        // The app you were just in is first; the one before it is the quick way back.
        View focus = swRow.getChildCount() > 1 ? swRow.getChildAt(1) : first;
        if (focus != null) ((ViewGroup) focus).getChildAt(0).requestFocus();
    }

    private void closeSwitcher() {
        if (!swOpen) return;
        swOpen = false;
        swOverlay.animate().alpha(0f).setDuration(UiStyle.ms(200)).withEndAction(new Runnable() {
            @Override public void run() { if (!swOpen) { swOverlay.setVisibility(View.INVISIBLE); swBlur.setImageDrawable(null); } }
        }).start();
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private View switcherCard(final App a, java.io.File shot) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        final FrameLayout card = new FrameLayout(this);
        int w = dp(272), h = dp(153);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xFF22252B);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) card.setDefaultFocusHighlightEnabled(false);
        ImageView art = new ImageView(this);
        Bitmap pic = shot != null && shot.exists() ? BitmapFactory.decodeFile(shot.getPath()) : null;
        if (pic != null) {
            art.setImageBitmap(pic);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(-1, -1));
            ImageView badge = new ImageView(this);
            badge.setImageDrawable(a.icon);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM | Gravity.START);
            bl.setMargins(dp(10), 0, 0, dp(10));
            card.addView(badge, bl);
        } else if (a.banner != null) {
            Bitmap r = roundedArt(a.banner, w, h, dp(16));
            if (r != null) art.setImageBitmap(r); else art.setImageDrawable(a.banner);
            art.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(art, new FrameLayout.LayoutParams(-1, -1));
        } else {
            art.setImageDrawable(a.icon);
            card.addView(art, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        }
        final View ring = new View(this);
        GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(dp(16));
        rb.setStroke(dp(Math.max(2, UiStyle.outline)), 0xF2FFFFFF);
        ring.setBackground(rb);
        ring.setAlpha(0f);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        final TextView label = new UiStyle.FontText(this);
        label.setText(a.label);
        label.setTextSize(15);
        label.setTextColor(0xB3FFFFFF);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(10), 0, 0);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                ring.animate().alpha(has ? 1f : 0f).setDuration(UiStyle.ms(160)).start();
                label.setTextColor(has ? Color.WHITE : 0xB3FFFFFF);
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setInterpolator(has ? SPRING : EASE)
                        .setDuration(UiStyle.ms(has ? 320 : 200)).start();
                if (has) v.getParent().requestChildFocus((View) v.getParent(), v);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSwitcher(); launchFrom = v; launch(a); }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setTitle(a.label)
                        .setItems(new String[]{"Close App", "Remove from Recent Apps", "Open App Info"}, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                if (which == 0) tvRun(a.label + " closed", null, "am force-stop " + a.pkg);
                                if (which <= 1) {
                                    java.util.Set<String> s = new java.util.HashSet<>(prefs.getStringSet("switcher_hidden", new java.util.HashSet<String>()));
                                    s.add(a.pkg);
                                    prefs.edit().putStringSet("switcher_hidden", s).apply();
                                    closeSwitcher();
                                    ui.postDelayed(new Runnable() { @Override public void run() { openSwitcher(); } }, 260);
                                } else {
                                    startOr(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                                }
                            }
                        }).show();
                return true;
            }
        });
        col.addView(card, new LinearLayout.LayoutParams(w, h));
        col.addView(label, new LinearLayout.LayoutParams(w, -2));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.setMargins(0, 0, dp(28), 0);
        swRow.addView(col, cl);
        return col;
    }

    // ---------- Sidebar ----------

    private boolean sidebarOn() { return prefs.getBoolean("sidebar", false); }

    private void buildSidebar(FrameLayout root) {
        if (!sidebarOn()) return;
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(6), dp(8), dp(6), dp(8));
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        bg.setColor(0x33000000);
        bg.setStroke(Math.max(1, dp(1)), 0x26FFFFFF);
        rail.setBackground(bg);
        String[][] items = {{"ic_person", "Home"}, {"ic_search", "Search"}, {"ic_apps", "Recent Apps"},
                {"ic_input", "Inputs"}, {"ic_tune", "Control Centre"}, {"gear", "Settings"}};
        for (final String[] it : items) {
            final LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), 0, dp(10), 0);
            final GradientDrawable ib = new GradientDrawable();
            ib.setCornerRadius(dp(20));
            ib.setColor(0);
            item.setBackground(ib);
            item.setFocusable(true);
            item.setClickable(true);
            if (Build.VERSION.SDK_INT >= 26) item.setDefaultFocusHighlightEnabled(false);
            final ImageView ic = new ImageView(this);
            if ("Home".equals(it[1]) && !prefs.getString("user_name", "").isEmpty()) {
                int ci = Math.max(0, Math.min(SetupActivity.AVATAR_COLOURS.length - 1, prefs.getInt("avatar_colour", 0)));
                ic.setImageBitmap(Art.avatar(prefs.getString("avatar", ""), prefs.getString("user_name", "?").substring(0, 1).toUpperCase(),
                        SetupActivity.AVATAR_COLOURS[ci], dp(24)));
            } else {
                ic.setImageResource(drawable(it[0]));
                ic.setColorFilter(0xFFFFFFFF);
            }
            item.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
            final TextView label = new UiStyle.FontText(this);
            label.setText(it[1]);
            label.setTextSize(14);
            label.setTextColor(Color.WHITE);
            label.setPadding(dp(10), 0, dp(4), 0);
            label.setVisibility(View.GONE);
            item.addView(label);
            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    ib.setColor(has ? 0xFFF2F3F5 : 0);
                    if (!"Home".equals(it[1]) || prefs.getString("user_name", "").isEmpty()) ic.setColorFilter(has ? 0xFF111317 : 0xFFFFFFFF);
                    label.setTextColor(has ? 0xFF111317 : Color.WHITE);
                    label.setVisibility(has ? View.VISIBLE : View.GONE);
                }
            });
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String a = it[1];
                    if (a.equals("Home")) goTop();
                    else if (a.equals("Search")) openSearch();
                    else if (a.equals("Recent Apps")) openSwitcher();
                    else if (a.equals("Inputs")) ccAction("Inputs");
                    else if (a.equals("Control Centre")) openControlCentre();
                    else openSettings();
                }
            });
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-2, dp(40));
            il.setMargins(0, dp(3), 0, dp(3));
            rail.addView(item, il);
        }
        muteClicks(rail);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.START);
        rl.setMargins(dp(18), 0, 0, dp(40));
        root.addView(rail, rl);
    }

    // ---------- Dynamic Island (a small app pill, top centre) ----------

    private View islandView;
    private FrameLayout islandRoot;

    private void buildIsland(FrameLayout root) {
        islandRoot = root;
        if (islandView != null) { root.removeView(islandView); islandView = null; }
        if (!prefs.getBoolean("island", false)) return;
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(8), 0, dp(8), 0);
        pill.setClipChildren(false);
        pill.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(0xE6000000);
        pill.setBackground(bg);
        List<String> list = islandApps();
        if (list.isEmpty()) return;
        for (String p : list) {
            final App a = apps.get(p);
            if (a == null) continue;
            final ImageView ic = new ImageView(this);
            ic.setImageDrawable(a.icon);
            ic.setPadding(dp(4), dp(4), dp(4), dp(4));
            ic.setFocusable(true);
            ic.setClickable(true);
            ic.setContentDescription(a.label);
            if (Build.VERSION.SDK_INT >= 26) ic.setDefaultFocusHighlightEnabled(false);
            final GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ic.setBackground(ring);
            ic.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    ring.setColor(has ? 0x40FFFFFF : 0);
                    v.animate().scaleX(has ? 1.25f : 1f).scaleY(has ? 1.25f : 1f).setInterpolator(has ? SPRING : EASE)
                            .setDuration(UiStyle.ms(260)).start();
                }
            });
            ic.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchFrom = v; launch(a); }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(30), dp(30));
            l.setMargins(dp(3), 0, dp(3), 0);
            pill.addView(ic, l);
        }
        muteClicks(pill);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pl.setMargins(0, dp(22), 0, 0);
        root.addView(pill, pl);
        islandView = pill;
    }

    private List<String> islandApps() {
        List<String> out = new ArrayList<>();
        String saved = prefs.getString("island_apps", null);
        if (saved == null) {
            for (String p : favs()) { if (apps.containsKey(p)) out.add(p); if (out.size() >= 4) break; }
        } else {
            for (String p : saved.split("\n")) if (!p.isEmpty() && apps.containsKey(p)) out.add(p);
        }
        return out;
    }

    // ---------- Settings pages for build 3 ----------

    private void pageControlCentre() {
        final List<String> keys = ccKeys();
        header("Tiles");
        for (int i = 0; i < keys.size(); i++) {
            final int idx = i;
            final String[] t = ccTile(keys.get(i));
            row(t[1], 0xFF0A84FF, t[2], "w".equals(t[4]) ? "Wide" : "Round", false, new Runnable() {
                @Override public void run() {
                    final List<String> opts = new ArrayList<>();
                    if (idx > 0) opts.add("Move Earlier");
                    if (idx < keys.size() - 1) opts.add("Move Later");
                    opts.add("Remove Tile");
                    new AlertDialog.Builder(MainActivity.this).setTitle(t[2])
                            .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    List<String> k = new ArrayList<>(keys);
                                    String o = opts.get(w);
                                    if (o.equals("Move Earlier")) java.util.Collections.swap(k, idx, idx - 1);
                                    else if (o.equals("Move Later")) java.util.Collections.swap(k, idx, idx + 1);
                                    else k.remove(idx);
                                    saveCc(k);
                                }
                            }).show();
                }
            });
        }
        row("ic_apps", 0xFF30D158, "Add Tile", "", false, new Runnable() {
            @Override public void run() {
                final List<String[]> avail = new ArrayList<>();
                for (String[] t : CC_TILES) if (!keys.contains(t[0])) avail.add(t);
                if (avail.isEmpty()) { toast("Every feature is already on Control Centre."); return; }
                String[] names = new String[avail.size()];
                for (int i = 0; i < names.length; i++) names[i] = avail.get(i)[2];
                new AlertDialog.Builder(MainActivity.this).setTitle("Add Tile")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                List<String> k = new ArrayList<>(keys);
                                k.add(avail.get(w)[0]);
                                saveCc(k);
                            }
                        }).show();
            }
        });
        row("ic_download", 0xFFD1D1D6, "Reset to Default", "", false, new Runnable() {
            @Override public void run() { prefs.edit().remove("cc_tiles").apply(); restyle(); }
        });
        note("Up to four wide and eight round tiles are shown. StandBy, Wi-Fi, Bluetooth and volume always stay.");
        header("Notifications");
        final boolean notes = prefs.getBoolean("cc_notes", false);
        row("ic_phone", 0xFFFF9F0A, "Notifications in Control Centre", notes ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("cc_notes", !notes).apply(); refreshPage(); }
        });
        if (notes && !NotifListener.connected()) {
            row("ic_person", 0xFF30D158, "Allow Notification Access", "", false, new Runnable() {
                @Override public void run() {
                    tvRun("Notification access allowed", new Runnable() { @Override public void run() { refreshPage(); } },
                            "cmd notification allow_listener " + getPackageName() + "/" + getPackageName() + ".NotifListener");
                }
            });
        }
    }

    private void saveCc(List<String> keys) {
        prefs.edit().putString("cc_tiles", TextUtils.join(",", keys)).apply();
        restyle();
    }

    private boolean accessibilityOn() {
        String svc = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return svc != null && svc.contains(getPackageName() + "/");
    }

    private boolean usageAllowed() {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) { return false; }
    }

    private void pageStartup() {
        final boolean acc = accessibilityOn();
        row("ic_remote", 0xFF30D158, "Accessibility Service", acc ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                if (acc) { startOr(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return; }
                toast("Connecting… if the TV asks, tick Always allow.");
                new Thread(new Runnable() {
                    @Override public void run() {
                        String m;
                        try { AdbClient.runSetup(MainActivity.this, null, true); m = "Accessibility service on"; }
                        catch (Exception e) { m = TvTools.why(e); }
                        final String msg = m;
                        ui.post(new Runnable() { @Override public void run() { toast(msg); refreshPage(); } });
                    }
                }).start();
            }
        });
        note("Needed for catching the Home button and for button remapping. It watches only remote buttons and which app is open.");
        final boolean catchHome = prefs.getBoolean("catch_home", false);
        row("ic_person", 0xFF0A84FF, "Override Current Launcher", catchHome ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                if (!accessibilityOn() && !catchHome) toast("Turn on the Accessibility Service first");
                prefs.edit().putBoolean("catch_home", !catchHome).apply();
                refreshPage();
            }
        });
        note("The Home button opens TV Home even where the TV keeps its own launcher.");
        final boolean dbl = prefs.getBoolean("home_double", true);
        row("ic_apps", 0xFFFF9F0A, "Double-press Home for Recent Apps", dbl ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("home_double", !dbl).apply(); refreshPage(); }
        });
        final boolean cards = prefs.getBoolean("app_cards", false);
        row("ic_picture", 0xFFBF5AF2, "Show App Screens in Cards", cards ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("app_cards", !cards).apply(); refreshPage(); }
        });
        note("Recent Apps shows a picture of each app as you left it (taken through ADB Tools when you press Home).");
        final boolean usage = usageAllowed();
        row("ic_apps", 0xFF64D2FF, "Usage Access (Recent Apps)", usage ? "Allowed" : "Off", false, new Runnable() {
            @Override public void run() {
                if (usage) return;
                tvRun("Usage access allowed", new Runnable() { @Override public void run() { refreshPage(); } },
                        "appops set " + getPackageName() + " GET_USAGE_STATS allow");
            }
        });
        final boolean boot = prefs.getBoolean("start_on_boot", false);
        row("ic_download", 0xFF30D158, "Start When the TV Turns On", boot ? "On" : "Off", false, new Runnable() {
            @Override public void run() {
                prefs.edit().putBoolean("start_on_boot", !boot).apply();
                if (!boot) tvRun("Allowed: starts when the TV turns on", null, "appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow");
                refreshPage();
            }
        });
        row("ic_apps", 0xFF8E8CFF, "Open Recent Apps Now", "", false, new Runnable() {
            @Override public void run() { openSwitcher(); }
        });
    }

    private void pageLayout() {
        final boolean side = sidebarOn();
        row("ic_apps", 0xFF0A84FF, "Sidebar UI", side ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("sidebar", !side).apply(); restyle(); }
        });
        note("A slim menu on the left: Home, Search, Recent Apps, Inputs, Control Centre and Settings. The dock stays.");
        final boolean island = prefs.getBoolean("island", false);
        row("ic_apps", 0xFFFF9F0A, "Dynamic Island", island ? "On" : "Off", false, new Runnable() {
            @Override public void run() { prefs.edit().putBoolean("island", !island).apply(); restyle(); }
        });
        if (island) {
            row("ic_apps", 0xFF30D158, "Dynamic Island Apps", islandApps().size() + " apps", true, new Runnable() {
                @Override public void run() { subPage("Dynamic Island Apps", new Runnable() { @Override public void run() { pageIslandApps(); } }); }
            });
        }
    }

    private void pageIslandApps() {
        final List<String> chosen = islandApps();
        note("Tick up to six apps to keep in the Dynamic Island.");
        List<App> all = new ArrayList<>(apps.values());
        java.util.Collections.sort(all, new java.util.Comparator<App>() {
            @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        for (final App a : all) {
            if (a.inputId != null) continue;
            final boolean on = chosen.contains(a.pkg);
            row(null, 0, a.label, on ? "✓" : "", false, new Runnable() {
                @Override public void run() {
                    List<String> c = new ArrayList<>(islandApps());
                    if (on) c.remove(a.pkg);
                    else if (c.size() < 6) c.add(a.pkg);
                    else { toast("Up to six apps"); return; }
                    prefs.edit().putString("island_apps", TextUtils.join("\n", c)).apply();
                    if (islandRoot != null) buildIsland(islandRoot);
                    refreshPage();
                }
            });
        }
    }

    // ================================================================== Build 4: content, backup, search, updates

    private java.util.Set<String> newsIds() {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        for (String id : prefs.getString("news_sources", TopShelf.NEWS_DEFAULT).split(",")) if (!id.isEmpty()) s.add(id);
        return s;
    }

    private void reloadShelf() {
        shelfCards = new ArrayList<>();
        shelfIndex = -1;
        ++cardGen;
        applyShelfLayout();
        loadShelf(true);
    }

    /** Extra Top Shelf settings, shown under the main ones. */
    private void shelfExtras() {
        String m = shelfMode();
        if (TopShelf.MODE_WATCH.equals(m)) {
            final String[] kinds = {"Movies & Series", "Movies", "TV Series"};
            row("ic_picture", 0xFFFF9F0A, "Shelf Content", kinds[Math.max(0, Math.min(2, prefs.getInt("shelf_kind", 0)))], true, new Runnable() {
                @Override public void run() {
                    subPage("Shelf Content", new Runnable() {
                        @Override public void run() {
                            choices(kinds, prefs.getInt("shelf_kind", 0), new ChoiceResult() {
                                @Override public void chose(int k) { prefs.edit().putInt("shelf_kind", k).apply(); reloadShelf(); }
                            });
                        }
                    });
                }
            });
            String g = prefs.getString("shelf_genre", "");
            row("ic_tune", 0xFF30D158, "Genre", g.isEmpty() ? "All" : g, true, new Runnable() {
                @Override public void run() {
                    subPage("Genre", new Runnable() {
                        @Override public void run() {
                            String[] names = new String[TopShelf.GENRES.length + 1];
                            names[0] = "All";
                            System.arraycopy(TopShelf.GENRES, 0, names, 1, TopShelf.GENRES.length);
                            String cur = prefs.getString("shelf_genre", "");
                            int sel = 0;
                            for (int i = 1; i < names.length; i++) if (names[i].equals(cur)) sel = i;
                            final String[] nn = names;
                            choices(names, sel, new ChoiceResult() {
                                @Override public void chose(int k) { prefs.edit().putString("shelf_genre", k == 0 ? "" : nn[k]).apply(); reloadShelf(); }
                            });
                        }
                    });
                }
            });
        } else if (TopShelf.MODE_NEWS.equals(m)) {
            row("ic_phone", 0xFFFF9F0A, "Newsrooms", newsIds().size() + " chosen", true, new Runnable() {
                @Override public void run() { subPage("Newsrooms", new Runnable() { @Override public void run() { pageNewsrooms(); } }); }
            });
            final int[] mins = {15, 30, 60};
            int ev = prefs.getInt("news_every", 30);
            row("ic_clock", 0xFF64D2FF, "Refresh News Every", ev + " minutes", true, new Runnable() {
                @Override public void run() {
                    subPage("Refresh News Every", new Runnable() {
                        @Override public void run() {
                            int e = prefs.getInt("news_every", 30);
                            choices(new String[]{"15 minutes", "30 minutes", "1 hour"}, e == 15 ? 0 : e == 60 ? 2 : 1, new ChoiceResult() {
                                @Override public void chose(int k) { prefs.edit().putInt("news_every", mins[k]).apply(); }
                            });
                        }
                    });
                }
            });
        }
        final String[] cw = {"All Continue Watching", "App-specific (follows the dock)"};
        row("ic_apps", 0xFF8E8CFF, "Continue Watching", cw[prefs.getInt("cw_mode", 0) == 1 ? 1 : 0], true, new Runnable() {
            @Override public void run() {
                subPage("Continue Watching", new Runnable() {
                    @Override public void run() {
                        choices(cw, prefs.getInt("cw_mode", 0), new ChoiceResult() {
                            @Override public void chose(int k) { prefs.edit().putInt("cw_mode", k).apply(); filterWatchNext(null); }
                        });
                        note("App-specific: as you move along the dock, Continue Watching shows only that app's shows.");
                    }
                });
            }
        });
    }

    private void pageNewsrooms() {
        final java.util.Set<String> ids = newsIds();
        String group = "";
        for (final String[] r : TopShelf.NEWSROOMS) {
            if (!r[2].equals(group)) { group = r[2]; header(group); }
            final boolean on = ids.contains(r[0]);
            row(null, 0, r[1], on ? "✓" : "", false, new Runnable() {
                @Override public void run() {
                    java.util.Set<String> n = newsIds();
                    if (on) { if (n.size() == 1) { toast("Keep at least one newsroom"); return; } n.remove(r[0]); }
                    else n.add(r[0]);
                    prefs.edit().putString("news_sources", TextUtils.join(",", n)).apply();
                    shelfLoadedAt = 0;
                    refreshPage();
                }
            });
        }
    }

    /** Where to watch: the TV's own search, or straight into a streaming app you have. */
    private void whereToWatch(final TopShelf.Card c) {
        final String[][] services = {
                {"Netflix", "com.netflix.ninja", "https://www.netflix.com/search?q="},
                {"Prime Video", "com.amazon.amazonvideo.livingroom", "https://www.primevideo.com/search/?phrase="},
                {"JioHotstar", "in.startv.hotstar", "https://www.hotstar.com/in/search?q="},
                {"JioHotstar", "in.startv.hotstartvonly", "https://www.hotstar.com/in/search?q="},
                {"SonyLIV", "com.sonyliv", "https://www.sonyliv.com/search?searchTerm="},
                {"ZEE5", "com.graymatrix.did", "https://www.zee5.com/search?q="},
                {"YouTube", "com.google.android.youtube.tv", ""},
        };
        final List<String[]> have = new ArrayList<>();
        have.add(new String[]{"Search this TV", "", ""});
        for (String[] s : services) {
            if (apps.containsKey(s[1])) {
                boolean dup = false;
                for (String[] h : have) if (h[0].equals(s[0])) dup = true;
                if (!dup) have.add(s);
            }
        }
        String[] names = new String[have.size()];
        for (int i = 0; i < names.length; i++) names[i] = have.get(i)[0];
        new AlertDialog.Builder(this).setTitle(c.title + (c.meta == null || c.meta.isEmpty() ? "" : "\n" + c.meta))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String[] s = have.get(w);
                        try {
                            if (s[1].isEmpty()) {
                                startActivity(new Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, c.title)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } else if (s[2].isEmpty()) {
                                startActivity(new Intent(Intent.ACTION_SEARCH).setPackage(s[1]).putExtra(SearchManager.QUERY, c.title)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } else {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(s[2] + Uri.encode(c.title))).setPackage(s[1])
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            }
                        } catch (Exception e) {
                            // That app doesn't take a search link: open it instead.
                            App a = apps.get(s[1]);
                            if (a != null) launch(a); else toast("Search isn't available on this TV");
                        }
                    }
                }).show();
    }

    /** A reader for a news story: the summary straight away, then the full article once it loads. */
    private void readStory(final TopShelf.Card c) {
        final ScrollView sv = new ScrollView(this);
        final TextView body = new UiStyle.FontText(this);
        String ago = TopShelf.ago(c.time);
        final String head = c.source + (ago.isEmpty() ? "" : "  ·  " + ago);
        body.setText(head + "\n\n" + (c.desc == null ? "" : c.desc) + "\n\nLoading full article…");
        body.setTextSize(17);
        body.setLineSpacing(0, 1.3f);
        body.setPadding(dp(24), dp(8), dp(24), dp(16));
        body.setFocusable(true);
        sv.addView(body);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle(c.title).setView(sv).setPositiveButton("Close", null).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String full = TopShelf.article(c.link);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!dlg.isShowing()) return;
                        body.setText(head + "\n\n" + (full != null ? full : (c.desc == null ? "" : c.desc)
                                + "\n\n(The full article couldn't be loaded on the TV.)"));
                    }
                });
            }
        }).start();
    }

    // ---------- App-specific Continue Watching ----------

    private List<Wn> allWatchNext = new ArrayList<>();
    private String cwShownFor;

    private void filterWatchNext(String pkg) {
        if (cwRow == null) return;
        if (prefs.getInt("cw_mode", 0) != 1) pkg = null;
        if (pkg != null) {
            boolean any = false;
            for (Wn w : allWatchNext) if (pkg.equals(w.pkg)) { any = true; break; }
            if (!any) pkg = null;   // nothing from this app: keep showing everything
        }
        if (pkg == null ? cwShownFor == null : pkg.equals(cwShownFor)) return;
        cwShownFor = pkg;
        List<Wn> show = new ArrayList<>();
        for (Wn w : allWatchNext) if (pkg == null || pkg.equals(w.pkg)) show.add(w);
        cwRow.removeAllViews();
        for (Wn w : show) cwRow.addView(watchCell(w));
        muteClicks(cwRow);
        updateUpLinks();
    }

    // ---------- Back up & restore ----------

    private java.io.File backupDir() {
        java.io.File d = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (d != null && (d.canWrite() || d.mkdirs())) return d;
        return getExternalFilesDir(null);
    }

    private boolean canWriteStorage() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void pageBackup() {
        row("ic_download", 0xFF30D158, "Back Up Settings", "", false, new Runnable() {
            @Override public void run() {
                if (!canWriteStorage()) {
                    requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                    toast("Allow storage, then choose Back Up Settings again");
                    return;
                }
                try {
                    org.json.JSONObject root = new org.json.JSONObject();
                    root.put("tvhome_backup", 1);
                    root.put("version", versionName());
                    org.json.JSONObject values = new org.json.JSONObject();
                    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                        Object v = e.getValue();
                        org.json.JSONObject one = new org.json.JSONObject();
                        if (v instanceof Boolean) one.put("b", v);
                        else if (v instanceof Integer) one.put("i", v);
                        else if (v instanceof Long) one.put("l", v);
                        else if (v instanceof Float) one.put("f", ((Float) v).doubleValue());
                        else if (v instanceof String) one.put("s", v);
                        else if (v instanceof java.util.Set) one.put("set", new org.json.JSONArray((java.util.Set<?>) v));
                        else continue;
                        values.put(e.getKey(), one);
                    }
                    root.put("values", values);
                    String name = "TVHome-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new java.util.Date()) + ".json";
                    java.io.File f = new java.io.File(backupDir(), name);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    out.write(root.toString(1).getBytes("UTF-8"));
                    out.close();
                    toast("Backup saved: " + f.getPath());
                    refreshPage();
                } catch (Exception e) {
                    toast("Backup failed: " + e.getMessage());
                }
            }
        });
        final List<java.io.File> backups = new ArrayList<>();
        for (java.io.File d : new java.io.File[]{backupDir(), getExternalFilesDir(null)}) {
            java.io.File[] fs = d == null ? null : d.listFiles();
            if (fs != null) for (java.io.File f : fs) if (f.getName().startsWith("TVHome-backup-") && f.getName().endsWith(".json") && !backups.contains(f)) backups.add(f);
        }
        java.util.Collections.sort(backups, new java.util.Comparator<java.io.File>() {
            @Override public int compare(java.io.File a, java.io.File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        header("Restore Settings");
        if (backups.isEmpty()) note("No backups yet. Use Back Up Settings first. Backups are saved in the TV's Download folder; "
                + "copy one there (or to a USB drive's Download folder) to restore it on another TV.");
        for (final java.io.File f : backups) {
            row(null, 0, f.getName().replace("TVHome-backup-", "").replace(".json", ""), "Restore", false, new Runnable() {
                @Override public void run() {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Restore this file?")
                            .setMessage("Replaces your current settings, profile, favourites and folders. TV Home restarts.")
                            .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { restoreBackup(f); }
                            })
                            .setNeutralButton("Delete This Backup", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    toast(f.delete() ? "Backup deleted" : "Couldn't delete it");
                                    refreshPage();
                                }
                            })
                            .setNegativeButton("Keep Current Settings", null).show();
                }
            });
        }
    }

    private void restoreBackup(java.io.File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            org.json.JSONObject root = new org.json.JSONObject(b.toString("UTF-8"));
            if (root.optInt("tvhome_backup", 0) != 1) { toast("Not a TV Home backup"); return; }
            org.json.JSONObject values = root.getJSONObject("values");
            SharedPreferences.Editor ed = prefs.edit().clear();
            java.util.Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                org.json.JSONObject one = values.getJSONObject(k);
                if (one.has("b")) ed.putBoolean(k, one.getBoolean("b"));
                else if (one.has("i")) ed.putInt(k, one.getInt("i"));
                else if (one.has("l")) ed.putLong(k, one.getLong("l"));
                else if (one.has("f")) ed.putFloat(k, (float) one.getDouble("f"));
                else if (one.has("s")) ed.putString(k, one.getString("s"));
                else if (one.has("set")) {
                    java.util.Set<String> set = new java.util.HashSet<>();
                    org.json.JSONArray a = one.getJSONArray("set");
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    ed.putStringSet(k, set);
                }
            }
            ed.commit();
            toast("Settings restored");
            restyle();
        } catch (Exception e) {
            toast("Could not restore: not a valid backup");
        }
    }

    // ---------- Settings search ----------

    /** Everything you can search for in Settings: {title, extra words, where it lives}. */
    private String[][] settingsIndex() {
        return new String[][]{
                {"Wallpaper", "photo background scenery online bing nasa wallhaven unsplash met cleveland gradient emoji fractal glass", "wallpaper"},
                {"Online Wallpapers", "bing nasa wallhaven unsplash wikimedia museum topic 4k quality", "online"},
                {"Gradient & Solid Colour", "colour color background", "gradient"},
                {"Emoji Wallpapers", "emoji pattern", "emoji"},
                {"Fractal Glass", "glass reeded", "fractal"},
                {"Look & Style", "font text size colour outline animation speed sounds panels amoled dark clock 24-hour labels", "look"},
                {"Font", "typeface ttf otf", "font"},
                {"Sidebar & Dynamic Island", "sidebar island layout", "layout"},
                {"Control Centre", "tiles notifications quick settings", "cc"},
                {"Startup & Override", "home button launcher boot accessibility recent apps double press usage", "startup"},
                {"Wi-Fi", "network internet password wifi", "wifi"},
                {"Bluetooth", "pair remote headphones devices", "bluetooth"},
                {"Private DNS & Ad Blocking", "dns adguard cloudflare ads block test", "dns"},
                {"TV Debloat", "remove disable apps system bloat restore uninstall", "debloat"},
                {"Power & Sleep", "sleep restart screen saver screen off timeout", "power"},
                {"Date & Time", "time zone clock automatic", "datetime"},
                {"Device Info", "ram storage model android version ip up time", "device"},
                {"ADB Tools", "debugging adb system rights", "adb"},
                {"Home Screen", "top shelf hero movies news continue watching genre newsrooms", "home"},
                {"Weather", "city location temperature", "weather"},
                {"StandBy", "clock bedside", "standby"},
                {"Profile & Backup", "name picture avatar backup restore", "backup"},
                {"About TV Home", "update version", "about"},
        };
    }

    private void openIndex(String where) {
        switch (where) {
            case "wallpaper": subPage("Wallpaper", new Runnable() { @Override public void run() { pageWallpaper(); } }); break;
            case "online": subPage("Online Wallpapers", new Runnable() { @Override public void run() { pageOnline(); } }); break;
            case "gradient": subPage("Gradient & Solid Colour", new Runnable() { @Override public void run() { pageGradient(); } }); break;
            case "emoji": subPage("Emoji Wallpapers", new Runnable() { @Override public void run() { pageEmoji(); } }); break;
            case "fractal": subPage("Fractal Glass", new Runnable() { @Override public void run() { pageFractal(); } }); break;
            case "look": subPage("Look & Style", new Runnable() { @Override public void run() { pageLook(); } }); break;
            case "font": subPage("Font", new Runnable() { @Override public void run() { pageFont(); } }); break;
            case "layout": subPage("Sidebar & Dynamic Island", new Runnable() { @Override public void run() { pageLayout(); } }); break;
            case "cc": subPage("Control Centre", new Runnable() { @Override public void run() { pageControlCentre(); } }); break;
            case "startup": subPage("Startup & Override", new Runnable() { @Override public void run() { pageStartup(); } }); break;
            case "wifi": subPage("Wi-Fi", new Runnable() { @Override public void run() { pageWifi(); } }); break;
            case "bluetooth": subPage("Bluetooth", new Runnable() { @Override public void run() { pageBluetooth(); } }); break;
            case "dns": subPage("Private DNS & Ad Blocking", new Runnable() { @Override public void run() { pageDns(); } }); break;
            case "debloat": subPage("TV Debloat", new Runnable() { @Override public void run() { pageDebloat(); } }); break;
            case "power": subPage("Power & Sleep", new Runnable() { @Override public void run() { pagePower(); } }); break;
            case "datetime": subPage("Date & Time", new Runnable() { @Override public void run() { pageDateTime(); } }); break;
            case "device": subPage("Device Info", new Runnable() { @Override public void run() { pageDevice(); } }); break;
            case "adb": subPage("ADB Tools", new Runnable() { @Override public void run() { pageAdb(); } }); break;
            case "home": subPage("Home Screen", new Runnable() { @Override public void run() { pageHomeScreen(); } }); break;
            case "weather": subPage("Weather", new Runnable() { @Override public void run() { pageWeather(); } }); break;
            case "standby": subPage("StandBy", new Runnable() { @Override public void run() { pageStandby(); } }); break;
            case "backup": subPage("Profile & Backup", new Runnable() { @Override public void run() { pageBackup(); } }); break;
            default: subPage("About TV Home", new Runnable() { @Override public void run() { pageAbout(); } });
        }
    }

    private void searchSettings() {
        askText("Search Settings", "Type a setting", "", new TextResult() {
            @Override public void done(final String q) {
                final String needle = q.trim().toLowerCase(Locale.ROOT);
                if (needle.isEmpty()) return;
                subPage("Results for “" + q.trim() + "”", new Runnable() {
                    @Override public void run() {
                        int n = 0;
                        for (final String[] e : settingsIndex()) {
                            if (!(e[0] + " " + e[1]).toLowerCase(Locale.ROOT).contains(needle)) continue;
                            n++;
                            row(null, 0, e[0], "", true, new Runnable() { @Override public void run() { openIndex(e[2]); } });
                        }
                        if (n == 0) note("No settings match “" + q.trim() + "”");
                    }
                });
            }
        });
    }

    // ---------- About & updates ----------

    private static final String REPO = "saiesh394-blip/tv-home";

    private String versionName() {
        try { return pm.getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parseIntSafe(x[i]) : 0, q = i < y.length ? parseIntSafe(y[i]) : 0;
            if (p != q) return p - q;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }

    private String updateStatus;

    private void pageAbout() {
        row("ic_apps", 0xFF0A84FF, "Version", versionName(), false, new Runnable() { @Override public void run() { } });
        row("ic_download", 0xFF30D158, updateStatus == null ? "Check for Updates" : updateStatus, "", false, new Runnable() {
            @Override public void run() { checkUpdates(); }
        });
        note("Updates come from github.com/" + REPO + " and install through Android's own installer, over this version, "
                + "keeping your settings.");
    }

    private void checkUpdates() {
        updateStatus = "Checking…";
        refreshPage();
        new Thread(new Runnable() {
            @Override public void run() {
                String best = null, url = null;
                try {
                    org.json.JSONArray files = new org.json.JSONArray(TopShelf.text("https://api.github.com/repos/" + REPO + "/contents/download"));
                    for (int i = 0; i < files.length(); i++) {
                        org.json.JSONObject f = files.getJSONObject(i);
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^TVHome-([0-9.]+)\\.apk$").matcher(f.optString("name"));
                        if (!m.find()) continue;
                        if (best == null || compareVersions(m.group(1), best) > 0) { best = m.group(1); url = f.optString("download_url"); }
                    }
                } catch (Exception ignored) { }
                final String v = best, u = url;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (v == null) { updateStatus = "Couldn't check: try again later"; refreshPage(); return; }
                        if (compareVersions(v, versionName()) <= 0) { updateStatus = "Up to date (" + versionName() + ")"; refreshPage(); return; }
                        updateStatus = "Version " + v + " is available";
                        refreshPage();
                        confirm("Install TV Home " + v + "?", "It downloads from GitHub and installs over this version, keeping your settings.",
                                "Install", new Runnable() {
                                    @Override public void run() {
                                        if (installer == null) installer = new Installer(MainActivity.this);
                                        installer.download(u, "TV Home " + v);
                                    }
                                });
                    }
                });
            }
        }).start();
    }
}
