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
        if (wxOpen) { closeWeather(); return; }
        if (listening) { cancelVoice(); return; }
        if (searchOpen) { closeSearch(); return; }
        if (openFolder != null) { closeFolderPanel(true); return; }
        if (settingsOpen) { settingsBack(); return; }
        goTop();
    }

    private void goTop() {
        userMoved = false;
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
        wiggle.setDuration(150);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.start();
        tile.animate().scaleX(1.3f).scaleY(1.3f).setInterpolator(EASE).setDuration(200).start();
        if (moveHint != null) {
            moveHint.setText("\u2190  \u2192  to move     OK to place");
            moveHint.setAlpha(0f);
            moveHint.setVisibility(View.VISIBLE);
            moveHint.animate().alpha(1f).setDuration(250).start();
        }
    }

    private void moveBy(int dir) {
        int i = favRow.indexOfChild(movingCell);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= favRow.getChildCount() || isSettingsCell(favRow.getChildAt(j))) {
            // Gentle bump at the end of the row.
            movingCell.animate().translationX(dir * dp(10)).setDuration(90).withEndAction(new Runnable() {
                @Override public void run() {
                    movingCell.animate().translationX(0).setInterpolator(SPRING).setDuration(220).start();
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
        neighbour.animate().translationX(0).setInterpolator(EASE).setDuration(280).start();
        movingCell.setTranslationX(-dir * w);
        movingCell.animate().translationX(0).setInterpolator(EASE).setDuration(280).start();

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
        tile.animate().rotation(0).setDuration(150).start();
        if (moveHint != null) moveHint.animate().alpha(0f).setDuration(200).start();
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
        heroEyebrow = new TextView(this);
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
        heroTitle = new TextView(this);
        greetTitle = heroTitle;
        heroTitle.setTextSize(34);
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heroTitle.setLetterSpacing(-0.01f);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setShadowLayer(dp(10), 0, 0, 0x66000000);
        hero.addView(heroTitle);
        heroSub = new TextView(this);
        greetSub = heroSub;
        heroSub.setTextSize(17);
        heroSub.setTextColor(0xB3FFFFFF);
        heroSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        heroSub.setShadowLayer(dp(8), 0, 0, 0x66000000);
        heroSub.setPadding(0, dp(4), 0, 0);
        heroSub.setMaxLines(2);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroSub, new LinearLayout.LayoutParams(-1, dp(56)));
        moveHint = new TextView(this);
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
        shelfDots = new TextView(this);
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
        heroLp.setMargins(side, (int) (screenH * 0.18f), 0, 0);
        root.addView(hero, heroLp);
        setHeroNow(greetEyebrow(), greetTitleText(), greetLine());

        // Photo credit, small, in the empty top-left corner.
        creditView = new TextView(this);
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
        weatherTemp = new TextView(this);
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(has ? 300 : 200).start();
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
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(side - dp(gap / 2), dp(4), side - dp(gap / 2), dp(24));
        cwSection = section("Continue Watching");
        cwRow = (LinearLayout) cwSection.getChildAt(1);
        content.addView(cwSection);
        recentSection = section("Recent");
        recentRow = (LinearLayout) recentSection.getChildAt(1);
        content.addView(recentSection);
        TextView appsLabel = new TextView(this);
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
        dockTip.setVisibility(View.GONE);   // the logos already say which app it is
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

        weatherView = new TextView(this);
        weatherView.setTextSize(15);
        weatherView.setTextColor(0xCCFFFFFF);
        weatherView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        weatherView.setShadowLayer(dp(6), 0, 0, 0x80000000);
        weatherView.setVisibility(View.GONE);
        String cached = prefs.getString("weather_text", null);
        if (cached != null) { weatherView.setText(cached); weatherView.setVisibility(View.VISIBLE); }

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
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
            over.animate().alpha(0f).setDuration(fadeMs).setInterpolator(EASE).withEndAction(new Runnable() {
                @Override public void run() { over.setImageDrawable(null); hideColourFill(); }
            }).start();
            wallFront = under;
        } else {
            over.setImageBitmap(bmp);
            if (wallFront == null) over.setAlpha(0f);
            over.animate().alpha(1f).setDuration(fadeMs).setInterpolator(EASE).withEndAction(new Runnable() {
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
            colA.animate().alpha(1f).setDuration(dur).start();
            colFront = colA;
        } else if (colFront == colB) {
            // A sits underneath: paint it, then let B fade away to reveal it.
            ((GradientDrawable) colA.getBackground()).setColors(cols);
            colA.setAlpha(1f);
            colB.animate().cancel();
            colB.animate().alpha(0f).setDuration(dur).setInterpolator(EASE).start();
            colFront = colA;
        } else {
            ((GradientDrawable) colB.getBackground()).setColors(cols);
            colB.animate().cancel();
            colB.animate().alpha(1f).setDuration(dur).setInterpolator(EASE).start();
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
        heroSub.animate().alpha(0f).setDuration(110).setInterpolator(EASE).withEndAction(new Runnable() {
            @Override public void run() {
                if (gen != subGen) return;
                heroSub.setText(text);
                heroSub.setTranslationY(dp(6));
                heroSub.animate().alpha(1f).translationY(0).setDuration(300).setInterpolator(EASE).start();
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
        hero.animate().alpha(0f).setDuration(130).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (gen != heroGen) return;
                        setHeroNow(eyebrow, title, sub);
                        hero.setTranslationY(dp(10));
                        hero.animate().alpha(1f).translationY(0)
                                .setDuration(340).setInterpolator(EASE).start();
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
            TextView empty = new TextView(this);
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
            hero.animate().alpha(1f).translationY(0).setDuration(700).setInterpolator(EASE).start();
            dockFrame.setAlpha(0f);
            dockFrame.setTranslationY(dp(40));
            dockFrame.animate().alpha(1f).translationY(0).setStartDelay(150).setDuration(600)
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
                .setDuration(520).setInterpolator(EASE).start();
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
        vAnim.setDuration(smooth ? 260 : 460);
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
        hAnim.setDuration(smooth ? 240 : 420);
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
        TextView hiddenLabel = new TextView(this);   // keeps the shared tile code happy
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
            icon.animate().scaleX(sc[i]).scaleY(sc[i]).setInterpolator(EASE).setDuration(dur).start();
            cell.animate().translationX(shift[i]).setInterpolator(EASE).setDuration(dur).start();
            cell.setTranslationZ(focus >= 0 && Math.abs(i - focus) <= 1 ? dp(i == focus ? 2 : 1) : 0);
        }
        // Widen the glass bar by exactly what the icons need.
        float left = shift[0] - (sc[0] - 1f) * w / 2f;
        float right = shift[n - 1] + (sc[n - 1] - 1f) * w / 2f;
        float baseW = dockBar.getWidth() > 0 ? dockBar.getWidth() : dockBar.getLayoutParams().width;
        dockBar.animate().scaleX((baseW - left + right) / baseW).translationX((left + right) / 2f)
                .setInterpolator(EASE).setDuration(dur).start();
        // The app's name floats just above the magnified icon.
        if (focus < 0) {
            dockTip.animate().alpha(0f).setDuration(120).start();
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
        dockTip.animate().alpha(1f).translationX(tx).translationY(ty).setInterpolator(EASE).setDuration(dur).start();
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
        TextView tv = new TextView(this);
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
                            .setDuration(smooth ? 140 : has ? 300 : 200).start();
                }
                Tag tag = (Tag) v.getTag();
                if (tag == null) return;
                if (tag.ring != null) {
                    if (smooth) tag.ring.setAlpha(has ? 1f : 0f);
                    else tag.ring.animate().alpha(has ? 1f : 0f).setDuration(has ? 180 : 140).start();
                }
                if (tag.dim != null) tag.dim.setAlpha(has ? 0f : 1f);
                if (tag.sheen != null) tag.sheen.animate().alpha(has ? 1f : 0f).setDuration(has ? 300 : 150).start();
                if (smooth) tag.label.setAlpha(has ? 1f : 0.62f);
                else tag.label.animate().alpha(has ? 1f : 0.62f).setDuration(180).start();
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
        ringBg.setStroke(dp(2), 0xF2FFFFFF);
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

        final TextView tv = iconOnly ? null : new TextView(this);
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(has ? 300 : 200).start();
            }
        });
        pill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(90).withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(200).setInterpolator(SPRING).start();
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

        voiceText = new TextView(this);
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
        voiceLayer.animate().alpha(1f).translationY(0).setStartDelay(0).setInterpolator(EASE).setDuration(320).start();
        wave.start();
    }

    private void hideVoiceLayer(int delay) {
        listening = false;
        wave.stop();
        voiceLayer.animate().cancel();
        voiceLayer.animate().alpha(0f).translationY(dp(30)).setStartDelay(delay).setDuration(280)
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
        searchOverlay.animate().alpha(1f).setDuration(260).start();
        searchField.requestFocus();
    }

    private void closeSearch() {
        if (!searchOpen) return;
        searchOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        searchOverlay.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
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
        final TextView tv = new TextView(this);
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(240).start();
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
            nightDim.animate().alpha(target).setDuration(3000).start();
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
        v.animate().scaleX(rest * 0.94f).scaleY(rest * 0.94f).setDuration(90).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(rest).scaleY(rest).setDuration(220)
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
            tintView.animate().alpha(0f).setDuration(250).setInterpolator(EASE).start();
            return;
        }
        Runnable swap = new Runnable() {
            @Override public void run() {
                tintDrawable.setColors(new int[]{withAlpha(to, 0x66), withAlpha(to, 0x24), 0});
                tintView.animate().alpha(1f).setDuration(320).setInterpolator(EASE).start();
            }
        };
        if (tintView.getAlpha() > 0.05f) {
            tintView.animate().alpha(0f).setDuration(140).setInterpolator(EASE).withEndAction(swap).start();
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
        TextClock t = new TextClock(this);
        t.setFormat12Hour("h:mm a");
        t.setFormat24Hour("HH:mm");
        t.setTextSize(19);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(dp(6), 0, 0, 0x66000000);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextClock d = new TextClock(this);
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

        // Inputs and Settings
        LinearLayout b2 = new LinearLayout(this);
        b2.setOrientation(LinearLayout.HORIZONTAL);
        b2.setClipChildren(false);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(col), dp(56));
        il.setMargins(0, 0, dp(gapDp), 0);
        b2.addView(ccBlock("ic_input", "Inputs", "Inputs", CC_WIDE, false), il);
        b2.addView(ccBlock("gear", "Settings", "TV Home settings", CC_WIDE, false), new LinearLayout.LayoutParams(dp(col), dp(56)));
        LinearLayout.LayoutParams b2l = new LinearLayout.LayoutParams(-2, -2);
        b2l.setMargins(0, 0, 0, dp(12));
        ccPanel.addView(b2, b2l);

        // Round buttons: voice, mirror, screensaver, more
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        String[][] rounds = {{"ic_mic", "Voice search"}, {"ic_cast", "Screen Mirror"},
                {"ic_moon", "Screensaver"}, {"ic_apps", "More"}};
        int circle = 50, space = (full - 4 * circle) / 3;
        for (int i = 0; i < rounds.length; i++) {
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(circle), dp(circle));
            if (i < rounds.length - 1) rl.setMargins(0, 0, dp(space), 0);
            r.addView(ccBlock(rounds[i][0], rounds[i][1], rounds[i][1], CC_ROUND, false), rl);
        }
        LinearLayout.LayoutParams rll = new LinearLayout.LayoutParams(-2, -2);
        rll.setMargins(0, 0, 0, dp(12));
        ccPanel.addView(r, rll);

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
        final TextView tv = kind == CC_ROUND ? null : new TextView(this);
        final TextView sub = withSub ? new TextView(this) : null;
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(has ? 280 : 180).start();
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
        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(0, dp(6), 0, 0);
        tile.addView(tv);
        final TextView sub = new TextView(this);
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(has ? 300 : 200).start();
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
        final TextView tv = text != null ? new TextView(this) : null;
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(250).start();
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
        applyCcTint();
        Drawable backdrop = ccBackdrop();
        ccBlur.setImageDrawable(backdrop);
        ccBlur.setBackgroundColor(backdrop == null ? 0x99000000 : 0);
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ccOverlay.setVisibility(View.VISIBLE);

        // The home screen melts into a blur...
        ccBlur.animate().cancel();
        ccBlur.setAlpha(0f);
        ccBlur.animate().alpha(1f).setInterpolator(EASE).setDuration(300).withEndAction(new Runnable() {
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
                .setStartDelay(40).setInterpolator(GLIDE).setDuration(480).withLayer().start();
        for (int i = 0; i < ccPanel.getChildCount(); i++) {
            View c = ccPanel.getChildAt(i);
            c.animate().cancel();
            c.setAlpha(0f);
            c.setTranslationY(-dp(10));
            c.animate().alpha(1f).translationY(0).setStartDelay(90 + i * 45L)
                    .setInterpolator(GLIDE).setDuration(420).start();
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
        ccBlur.animate().alpha(0f).setInterpolator(EASE).setDuration(260).start();
        ccFrame.animate().cancel();
        ccFrame.animate().alpha(0f).translationX(dp(16)).translationY(-dp(8)).scaleX(0.97f).scaleY(0.97f)
                .setStartDelay(0).setInterpolator(EASE).setDuration(220).withLayer()
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
        TextView label = new TextView(this);
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

    private void renderRecents() {
        if (recentRow == null) return;
        recentRow.removeAllViews();
        Set<String> hide = hidden();
        int n = 0;
        for (String p : recents()) {
            App a = apps.get(p);
            if (a == null || hide.contains(p) || a.inputId != null) continue;
            recentRow.addView(cell(a, false, "r:"));
            if (++n >= COLS) break;
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
            TextView tt = new TextView(this);
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
        folderTitle = new TextView(this);
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
        folderOverlay.animate().alpha(1f).setDuration(220).start();
        folderPanel.setScaleX(0.94f);
        folderPanel.setScaleY(0.94f);
        folderPanel.animate().scaleX(1f).scaleY(1f).setInterpolator(GLIDE).setDuration(420).start();
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
        TextView how = new TextView(this);
        how.setText("On your phone (on the same Wi-Fi), open this in the browser:");
        how.setTextSize(15);
        box.addView(how);
        TextView link = new TextView(this);
        link.setText(url.replace("http://", ""));
        link.setTextSize(26);
        link.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        link.setPadding(0, dp(12), 0, dp(12));
        box.addView(link);
        TextView note = new TextView(this);
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
        TextView big = new TextView(this);
        big.setText("Settings");
        big.setTextSize(38);
        big.setTextColor(Color.WHITE);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        big.setPadding(0, dp(26), 0, dp(4));
        big.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(big, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = new TextView(this);
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
        settingsTitle = new TextView(this);
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
                settingsOverlay.setBackgroundColor(0xFF0B0B0F);
            }
            mainRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            settingsOverlay.setVisibility(View.VISIBLE);
            settingsOverlay.setAlpha(0f);
            settingsOverlay.animate().alpha(1f).setInterpolator(EASE).setDuration(260).withEndAction(new Runnable() {
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
        settingsOverlay.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
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
        settingsList.animate().alpha(1f).translationX(0).setInterpolator(GLIDE).setDuration(320).start();
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
        final TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView v = new TextView(this);
        v.setText(value == null ? "" : value);
        v.setTextSize(15);
        v.setTextColor(0x8CFFFFFF);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth(dp(220));
        r.addView(v);
        final TextView ch = new TextView(this);
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
                        .setInterpolator(has ? SPRING : EASE).setDuration(has ? 260 : 160).start();
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
        row("ic_wallpaper", 0xFF5AC8FA, "Appearance", "", true, new Runnable() {
            @Override public void run() { subPage("Appearance", new Runnable() { @Override public void run() { pageAppearance(); } }); }
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
        row("ic_download", 0xFF30D158, "Setup & Home App", prefs.getBoolean("adb_setup_ok", false) ? "Done" : "", true, new Runnable() {
            @Override public void run() { subPage("Setup & Home App", new Runnable() { @Override public void run() { pageSetup(); } }); }
        });
        row("gear", 0xFFD1D1D6, "All Settings", "", false, new Runnable() {
            @Override public void run() { ccAction("All settings"); }
        });
    }

    private void pageAppearance() {
        String mode = wallpapers.mode();
        String wp = Wallpapers.MODE_MINE.equals(mode) ? "My photos" : Wallpapers.MODE_PHONE.equals(mode) ? "From phone" : "Scenery";
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

    private void pageWallpaper() {
        String mode = wallpapers.mode();
        int sel = Wallpapers.MODE_MINE.equals(mode) ? 1 : Wallpapers.MODE_PHONE.equals(mode) ? 2 : 0;
        row(null, 0, "Scenery by time of day", sel == 0 ? "✓" : "", false, new Runnable() {
            @Override public void run() { wallpapers.setMode(Wallpapers.MODE_SCENERY); wallpapers.refresh(true); refreshPage(); }
        });
        row(null, 0, "My photos", sel == 1 ? "✓" : "", false, new Runnable() {
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
        row(null, 0, "Send from phone…", sel == 2 ? "✓" : "", false, new Runnable() {
            @Override public void run() { sendFromPhone(); }
        });
        row(null, 0, "Shuffle now", "", false, new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, "Finding a new wallpaper…", Toast.LENGTH_SHORT).show();
                wallpapers.refresh(true);
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
                else { settingsBg.setImageDrawable(null); settingsOverlay.setBackgroundColor(0xFF0B0B0F); }
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
            a.setDuration(220);
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
        TextView title = new TextView(this);
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
            chip.setTag(new Tag(a, false, "i:" + a.pkg, new TextView(this)));
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
        return ccOpen || searchOpen || openFolder != null || settingsOpen || wxOpen || movingCell != null || listening;
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
                final List<TopShelf.Card> list = TopShelf.MODE_NEWS.equals(mode) ? TopShelf.news() : TopShelf.watch();
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
        hero.animate().alpha(0f).setDuration(smooth ? 120 : 180).setInterpolator(EASE).withEndAction(new Runnable() {
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
                } else {
                    if (c.meta != null) sub.append(c.meta);
                    if (c.desc != null && !c.desc.isEmpty()) sub.append('\n').append(c.desc);
                }
                heroSub.setText(sub.toString());
                heroSub.setMaxLines(news ? 2 : 3);
                shelfOpenText.setText(news ? "Read Story" : "Where to Watch");
                hero.setTranslationY(dp(8));
                hero.animate().alpha(1f).translationY(0).setDuration(smooth ? 260 : 380).setInterpolator(EASE).start();
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
        if ("news".equals(c.kind)) { showStory(c); return; }
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
        TextView body = new TextView(this);
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
        scroll.animate().alpha(0f).setDuration(700).setInterpolator(EASE).withLayer().start();
        dockFrame.animate().alpha(0f).setDuration(700).setInterpolator(EASE).withLayer().start();
        shelfActions.animate().alpha(0f).setDuration(700).start();
        weatherPill.animate().alpha(0.6f).setDuration(700).start();
    }

    private void wakeFromIdle() {
        idleFull = false;
        scroll.animate().cancel();
        dockFrame.animate().cancel();
        scroll.animate().alpha(1f).setDuration(300).setInterpolator(EASE).withLayer().start();
        dockFrame.animate().alpha(1f).setDuration(300).setInterpolator(EASE).withLayer().start();
        shelfActions.animate().alpha(1f).setDuration(300).start();
        weatherPill.animate().alpha(1f).setDuration(300).start();
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
                        choices(new String[]{"Wallpaper (photos)", "Movies & Shows (popular now)", "News (Indian headlines)"}, s,
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
            weatherPill.animate().alpha(idleFull ? 0.6f : 1f).setDuration(500).start();
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
        wxBlur.animate().alpha(1f).setInterpolator(EASE).setDuration(300).withEndAction(new Runnable() {
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
                .setStartDelay(40).setInterpolator(GLIDE).setDuration(480).withLayer().start();
        refreshWeather(System.currentTimeMillis() - prefs.getLong("weather_time", 0) > 10 * 60 * 1000L);
    }

    private void closeWeather() {
        if (!wxOpen) return;
        wxOpen = false;
        mainRoot.setVisibility(View.VISIBLE);
        wxBlur.animate().cancel();
        wxBlur.animate().alpha(0f).setInterpolator(EASE).setDuration(260).start();
        wxPanel.animate().cancel();
        wxPanel.animate().alpha(0f).translationX(-dp(16)).translationY(-dp(8)).scaleX(0.97f).scaleY(0.97f)
                .setStartDelay(0).setInterpolator(EASE).setDuration(220).withLayer()
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!wxOpen) { wxOverlay.setVisibility(View.INVISIBLE); wxBlur.setImageDrawable(null); }
                    }
                }).start();
        mainRoot.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (weatherPill != null && weatherPill.getVisibility() == View.VISIBLE) weatherPill.requestFocus();
    }

    private TextView wxText(String t, int sp, int colour, boolean medium) {
        TextView v = new TextView(this);
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
}
