package com.codex.windows8launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;
import java.io.File;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final String PREFS = "launcher_prefs";
    private static final String PINNED = "pinned_components";
    private static final int MODE_START = 0;
    private static final int MODE_DESKTOP = 1;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;
    private static final int MIN_WINDOW_WIDTH = 280;
    private static final int MIN_WINDOW_HEIGHT = 200;
    private static final int[] TILE_COLORS = {
            Color.rgb(0, 174, 239),
            Color.rgb(0, 120, 215),
            Color.rgb(0, 178, 148),
            Color.rgb(162, 0, 255),
            Color.rgb(232, 17, 35),
            Color.rgb(255, 140, 0),
            Color.rgb(16, 124, 16),
            Color.rgb(195, 0, 82)
    };

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

    private FrameLayout screenFrame;
    private LinearLayout startRoot;
    private LinearLayout tileRow;
    private LinearLayout appsPanel;
    private TextView startTimeView;
    private TextView startDateView;
    private TextView appsTitleView; // Added
    private TextView trayTimeView;
    private TextView trayDateView;
    private EditText searchField;

    private FrameLayout desktopRoot;
    private FrameLayout windowLayer;
    private LinearLayout taskbarApps;
    private LinearLayout controlCenterPanel;
    private TextView controlCenterClock;
    private int currentMode = MODE_START;
    private int nextWindowOffset = 0;
    private ManagedWindow activeWindow;

    private PackageManager packageManager;
    private SharedPreferences prefs;
    private List<AppEntry> allApps = new ArrayList<>();
    private Set<String> pinned = new HashSet<>();
    private final List<ManagedWindow> windows = new ArrayList<>();

    private File currentPath = Environment.getExternalStorageDirectory();
    private LinearLayout fileListContainer;
    private TextView addressBar;
    private LinearLayout ribbonContent;
    private String currentRibbonTab = "Computer";

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (startTimeView != null) {
                startTimeView.setText(timeFormat.format(now));
            }
            if (startDateView != null) {
                startDateView.setText(dateFormat.format(now));
            }
            if (appsTitleView != null) {
                appsTitleView.setText("Apps (" + allApps.size() + ")");
            }
            if (trayTimeView != null) {
                trayTimeView.setText(timeFormat.format(now));
            }
            if (trayDateView != null) {
                trayDateView.setText(new SimpleDateFormat("M/d/yyyy", Locale.getDefault()).format(now));
            }
            if (controlCenterClock != null) {
                controlCenterClock.setText(timeFormat.format(now) + "\n" + dateFormat.format(now));
            }
            clockHandler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageManager = getPackageManager();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        pinned = new HashSet<>(prefs.getStringSet(PINNED, Collections.<String>emptySet()));

        loadApps();
        if (pinned.isEmpty()) {
            pinDefaultApps();
        }

        buildLayout();
        renderTiles();
        renderApps("");
        showStart();
        clockTick.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        renderTiles();
        if (appsListContainer != null) {
            renderAppsList(searchField == null ? "" : searchField.getText().toString());
        }
    }


    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private void buildLayout() {
        screenFrame = new FrameLayout(this);
        screenFrame.setBackgroundColor(Color.rgb(24, 24, 30));
        buildStartScreen();
        buildDesktop();
        setContentView(screenFrame);
    }

    private void buildStartScreen() {
        startRoot = new LinearLayout(this);
        startRoot.setOrientation(LinearLayout.VERTICAL);
        startRoot.setPadding(dp(48), dp(40), dp(48), dp(0));
        startRoot.setBackgroundColor(Color.rgb(24, 24, 30));
        screenFrame.addView(startRoot, new FrameLayout.LayoutParams(-1, -1));

        // 8.1 Header: [Start] ................. [User][Power][Search]
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        startRoot.addView(header, new LinearLayout.LayoutParams(-1, dp(88)));

        TextView title = new TextView(this);
        title.setText("Start");
        title.setTextColor(Color.WHITE);
        title.setTextSize(48);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        // Top-right controls
        LinearLayout topControls = new LinearLayout(this);
        topControls.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topControls, new LinearLayout.LayoutParams(-2, -1));

        TextView userName = new TextView(this);
        userName.setText("User");
        userName.setTextColor(Color.WHITE);
        userName.setTextSize(16);
        userName.setPadding(0, 0, dp(12), 0);
        topControls.addView(userName);

        TextView userIcon = textButton("👤", Color.TRANSPARENT, Color.WHITE, 20);
        userIcon.setPadding(dp(8), dp(8), dp(24), dp(8));
        topControls.addView(userIcon);

        TextView powerIcon = textButton("⏻", Color.TRANSPARENT, Color.WHITE, 20);
        powerIcon.setPadding(dp(8), dp(8), dp(24), dp(8));
        topControls.addView(powerIcon);

        startTimeView = new TextView(this);
        startTimeView.setTextColor(Color.WHITE);
        startTimeView.setTextSize(26);
        startTimeView.setGravity(Gravity.RIGHT);
        // Not adding to view, but keeping reference for clockTick

        startDateView = new TextView(this);
        startDateView.setTextColor(Color.argb(210, 255, 255, 255));
        startDateView.setTextSize(13);
        startDateView.setGravity(Gravity.RIGHT);
        // Not adding to view, but keeping reference for clockTick

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(false);
        startRoot.addView(hsv, new LinearLayout.LayoutParams(-1, 0, 1));

        tileRow = new LinearLayout(this);
        tileRow.setOrientation(LinearLayout.HORIZONTAL);
        tileRow.setGravity(Gravity.TOP);
        hsv.addView(tileRow, new HorizontalScrollView.LayoutParams(-2, -1));

        // Bottom Navigation Arrow (8.1 Style)
        FrameLayout bottomBar = new FrameLayout(this);
        startRoot.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(80)));

        TextView downArrow = textButton("↓", Color.argb(40, 255, 255, 255), Color.WHITE, 18);
        downArrow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        downArrow.setOnClickListener(v -> showAllApps());
        bottomBar.addView(downArrow, arrowLp);

        appsPanel = new LinearLayout(this);
        appsPanel.setOrientation(LinearLayout.VERTICAL);
        appsPanel.setBackgroundColor(Color.rgb(18, 18, 22));
        appsPanel.setVisibility(View.GONE);
        screenFrame.addView(appsPanel, new FrameLayout.LayoutParams(-1, -1));

        buildAllAppsView();
    }

    private void buildAllAppsView() {
        appsPanel.removeAllViews();
        appsPanel.setPadding(dp(48), dp(40), dp(48), dp(0));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        appsPanel.addView(header, new LinearLayout.LayoutParams(-1, dp(88)));

        appsTitleView = new TextView(this);
        appsTitleView.setText("Apps");
        appsTitleView.setTextColor(Color.WHITE);
        appsTitleView.setTextSize(48);
        appsTitleView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(appsTitleView, new LinearLayout.LayoutParams(0, -1, 1));


        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setHint("Search");
        searchField.setHintTextColor(Color.argb(150, 255, 255, 255));
        searchField.setTextColor(Color.WHITE);
        searchField.setBackgroundColor(Color.argb(30, 255, 255, 255));
        searchField.setPadding(dp(12), 0, dp(12), 0);
        header.addView(searchField, new LinearLayout.LayoutParams(dp(260), dp(40)));

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAppsList(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        appsListContainer = new LinearLayout(this);
        appsListContainer.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(appsListContainer);
        appsPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout bottomBar = new FrameLayout(this);
        appsPanel.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(80)));

        TextView upArrow = textButton("↑", Color.argb(40, 255, 255, 255), Color.WHITE, 18);
        upArrow.setGravity(Gravity.CENTER);
        upArrow.setOnClickListener(v -> hideAllApps());
        bottomBar.addView(upArrow, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        renderAppsList("");
    }

    private void showAllApps() {
        appsPanel.setVisibility(View.VISIBLE);
        startRoot.setVisibility(View.GONE);
    }

    private void hideAllApps() {
        appsPanel.setVisibility(View.GONE);
        startRoot.setVisibility(View.VISIBLE);
    }

    private LinearLayout appsListContainer;

    private void renderAppsList(String query) {
        if (appsListContainer == null) return;
        appsListContainer.removeAllViews();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        appsListContainer.addView(grid);

        String normalized = query.trim().toLowerCase(Locale.getDefault());
        for (final AppEntry app : allApps) {
            if (!normalized.isEmpty() && !app.label.toLowerCase(Locale.getDefault()).contains(normalized)) {
                continue;
            }
            grid.addView(createAppListItem(app));
        }
    }

    private View createAppListItem(AppEntry app) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        item.setClickable(true);
        item.setOnClickListener(v -> launch(app));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        item.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setPadding(dp(12), 0, 0, 0);
        item.addView(label);

        return item;
    }


    private void buildDesktop() {
        desktopRoot = new FrameLayout(this);
        desktopRoot.setBackground(makeDesktopBackground());
        screenFrame.addView(desktopRoot, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout iconColumn = new LinearLayout(this);
        iconColumn.setOrientation(LinearLayout.VERTICAL);
        iconColumn.setPadding(dp(18), dp(18), 0, 0);
        desktopRoot.addView(iconColumn, new FrameLayout.LayoutParams(dp(132), -2));
        iconColumn.addView(createDesktopIcon("这台电脑", "PC", new View.OnClickListener() {
            @Override public void onClick(View v) {
                openDesktopApp("这台电脑", "PC", buildComputerContent());
            }
        }));
        iconColumn.addView(createDesktopIcon("回收站", "BIN", new View.OnClickListener() {
            @Override public void onClick(View v) {
                openDesktopApp("回收站", "BIN", buildRecycleContent());
            }
        }));
        iconColumn.addView(createDesktopIcon("控制面板", "CTRL", new View.OnClickListener() {
            @Override public void onClick(View v) {
                openDesktopApp("控制面板", "CTRL", buildControlPanelContent());
            }
        }));

        windowLayer = new FrameLayout(this);
        FrameLayout.LayoutParams windowsLp = new FrameLayout.LayoutParams(-1, -1);
        windowsLp.bottomMargin = dp(48);
        desktopRoot.addView(windowLayer, windowsLp);

        buildControlCenter();
        buildTaskbar();
    }

    private void buildTaskbar() {
        LinearLayout taskbar = new LinearLayout(this);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(0, 0, dp(8), 0);
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setBackgroundColor(Color.argb(226, 18, 18, 22));
        FrameLayout.LayoutParams taskbarLp = new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM);
        desktopRoot.addView(taskbar, taskbarLp);

        TextView startButton = textButton("⊞", Color.argb(0, 0, 0, 0), Color.WHITE, 24);
        startButton.setGravity(Gravity.CENTER);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showStart();
            }
        });
        taskbar.addView(startButton, new LinearLayout.LayoutParams(dp(56), -1));

        taskbarApps = new LinearLayout(this);
        taskbarApps.setGravity(Gravity.CENTER_VERTICAL);
        taskbarApps.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.addView(taskbarApps, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout tray = new LinearLayout(this);
        tray.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        tray.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.addView(tray, new LinearLayout.LayoutParams(dp(190), -1));

        TextView status = textButton("NET  VOL", Color.TRANSPARENT, Color.argb(220, 255, 255, 255), 11);
        status.setGravity(Gravity.CENTER);
        tray.addView(status, new LinearLayout.LayoutParams(dp(70), -1));

        LinearLayout trayClock = new LinearLayout(this);
        trayClock.setGravity(Gravity.CENTER);
        trayClock.setOrientation(LinearLayout.VERTICAL);
        trayClock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleControlCenter();
            }
        });
        tray.addView(trayClock, new LinearLayout.LayoutParams(dp(86), -1));

        trayTimeView = new TextView(this);
        trayTimeView.setTextColor(Color.WHITE);
        trayTimeView.setTextSize(12);
        trayTimeView.setGravity(Gravity.CENTER);
        trayClock.addView(trayTimeView, new LinearLayout.LayoutParams(-1, dp(22)));

        trayDateView = new TextView(this);
        trayDateView.setTextColor(Color.argb(215, 255, 255, 255));
        trayDateView.setTextSize(11);
        trayDateView.setGravity(Gravity.CENTER);
        trayClock.addView(trayDateView, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView center = textButton("▤", Color.TRANSPARENT, Color.WHITE, 20);
        center.setGravity(Gravity.CENTER);
        center.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleControlCenter();
            }
        });
        tray.addView(center, new LinearLayout.LayoutParams(dp(34), -1));
    }

    private void buildControlCenter() {
        controlCenterPanel = new LinearLayout(this);
        controlCenterPanel.setOrientation(LinearLayout.VERTICAL);
        controlCenterPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        controlCenterPanel.setBackgroundColor(Color.argb(238, 26, 26, 32));
        controlCenterPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(dp(292), -1, Gravity.RIGHT);
        centerLp.bottomMargin = dp(48);
        desktopRoot.addView(controlCenterPanel, centerLp);

        TextView title = new TextView(this);
        title.setText("控制中心");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        controlCenterPanel.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        controlCenterClock = new TextView(this);
        controlCenterClock.setTextColor(Color.argb(230, 255, 255, 255));
        controlCenterClock.setTextSize(18);
        controlCenterClock.setGravity(Gravity.LEFT);
        controlCenterPanel.addView(controlCenterClock, new LinearLayout.LayoutParams(-1, dp(76)));

        controlCenterPanel.addView(settingTile("网络", "已连接到 Android 设备网络"));
        controlCenterPanel.addView(settingTile("音量", "系统音量 68%"));
        controlCenterPanel.addView(settingTile("亮度", "屏幕亮度 自动"));
        controlCenterPanel.addView(settingTile("通知", "没有新的通知"));
    }

    private View settingTile(String title, String subtitle) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(8), dp(12), dp(8));
        tile.setBackgroundColor(Color.argb(46, 255, 255, 255));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72));
        lp.setMargins(0, 0, 0, dp(10));
        tile.setLayoutParams(lp);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        tile.addView(titleView, new LinearLayout.LayoutParams(-1, dp(28)));

        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(Color.argb(195, 255, 255, 255));
        subView.setTextSize(12);
        subView.setMaxLines(2);
        tile.addView(subView, new LinearLayout.LayoutParams(-1, -2));
        return tile;
    }

    private View createDesktopIcon(String label, String symbol, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(8), dp(4), dp(8));
        item.setClickable(true);
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(dp(104), dp(104));
        itemLp.bottomMargin = dp(8);
        item.setLayoutParams(itemLp);

        TextView icon = textButton(symbol, Color.argb(132, 0, 120, 215), Color.WHITE, 18);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(48)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(13);
        text.setGravity(Gravity.CENTER);
        text.setShadowLayer(3, 1, 1, Color.BLACK);
        text.setMaxLines(2);
        item.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return item;
    }

    private LinearLayout buildComputerContent() {
        checkPermissions();
        LinearLayout explorer = new LinearLayout(this);
        explorer.setOrientation(LinearLayout.VERTICAL);
        explorer.setBackgroundColor(Color.WHITE);

        // Ribbon Tabs
        explorer.addView(buildRibbonTabs());

        // Ribbon Content (Toolbar)
        ribbonContent = new LinearLayout(this);
        ribbonContent.setOrientation(LinearLayout.HORIZONTAL);
        ribbonContent.setPadding(dp(12), dp(4), dp(12), dp(4));
        ribbonContent.setBackgroundColor(Color.rgb(245, 246, 247));
        explorer.addView(ribbonContent, new LinearLayout.LayoutParams(-1, dp(92)));
        updateRibbonContent();

        // Address Bar Row
        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(8), dp(4), dp(8), dp(4));
        addressRow.setBackgroundColor(Color.WHITE);
        explorer.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView backBtn = textButton("←", Color.TRANSPARENT, Color.BLACK, 18);
        backBtn.setPadding(dp(8), 0, dp(8), 0);
        backBtn.setOnClickListener(v -> navigateBack());
        addressRow.addView(backBtn);

        addressBar = new TextView(this);
        addressBar.setBackgroundColor(Color.rgb(240, 240, 240));
        addressBar.setPadding(dp(10), dp(4), dp(10), dp(4));
        addressBar.setTextSize(13);
        addressBar.setSingleLine(true);
        LinearLayout.LayoutParams addrLp = new LinearLayout.LayoutParams(0, -2, 1);
        addrLp.leftMargin = dp(8);
        addressRow.addView(addressBar, addrLp);

        // File List
        fileListContainer = new LinearLayout(this);
        fileListContainer.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fileListContainer);
        explorer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderFiles();
        return explorer;
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }
    }

    private View buildRibbonTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(Color.rgb(0, 120, 215));
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), 0, 0, 0);

        String[] tabNames = {"File", "Computer", "View"};
        for (String name : tabNames) {
            TextView tab = new TextView(this);
            tab.setText(name);
            tab.setPadding(dp(16), dp(8), dp(16), dp(8));
            tab.setTextSize(13);
            tab.setTextColor(currentRibbonTab.equals(name) ? Color.BLACK : Color.WHITE);
            tab.setBackgroundColor(currentRibbonTab.equals(name) ? Color.rgb(245, 246, 247) : Color.TRANSPARENT);
            tab.setOnClickListener(v -> {
                currentRibbonTab = name;
                updateRibbonTabs(tabs);
                updateRibbonContent();
            });
            tabs.addView(tab);
        }
        return tabs;
    }

    private void updateRibbonTabs(LinearLayout tabs) {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView tab = (TextView) tabs.getChildAt(i);
            boolean selected = tab.getText().toString().equals(currentRibbonTab);
            tab.setTextColor(selected ? Color.BLACK : Color.WHITE);
            tab.setBackgroundColor(selected ? Color.rgb(245, 246, 247) : Color.TRANSPARENT);
        }
    }

    private void updateRibbonContent() {
        ribbonContent.removeAllViews();
        if ("Computer".equals(currentRibbonTab)) {
            ribbonContent.addView(ribbonButton("Properties", "📁"));
            ribbonContent.addView(ribbonButton("Open", "📂"));
            ribbonContent.addView(ribbonButton("Rename", "📝"));
        } else if ("View".equals(currentRibbonTab)) {
            ribbonContent.addView(ribbonButton("Large icons", "🖼️"));
            ribbonContent.addView(ribbonButton("Details", "📋"));
            ribbonContent.addView(ribbonButton("Sort by", "🔢"));
        } else {
            ribbonContent.addView(ribbonButton("Info", "ℹ️"));
            ribbonContent.addView(ribbonButton("Help", "❓"));
        }
    }

    private View ribbonButton(String label, String iconStr) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(8), dp(4), dp(8), dp(4));
        btn.setClickable(true);

        TextView icon = new TextView(this);
        icon.setText(iconStr);
        icon.setTextSize(24);
        btn.addView(icon);

        TextView txt = new TextView(this);
        txt.setText(label);
        txt.setTextSize(11);
        txt.setTextColor(Color.BLACK);
        btn.addView(txt);

        return btn;
    }

    private void renderFiles() {
        if (fileListContainer == null) return;
        fileListContainer.removeAllViews();
        addressBar.setText(currentPath.getAbsolutePath());

        File[] files = currentPath.listFiles();
        if (files == null) {
            TextView error = new TextView(this);
            error.setText("Permission denied or empty folder");
            error.setPadding(dp(16), dp(16), dp(16), dp(16));
            fileListContainer.addView(error);
            return;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files) {
            fileListContainer.addView(createFileRow(file));
        }
    }

    private View createFileRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (file.isDirectory()) {
                currentPath = file;
                renderFiles();
            } else {
                Toast.makeText(this, "Opening " + file.getName(), Toast.LENGTH_SHORT).show();
            }
        });

        TextView icon = new TextView(this);
        icon.setText(file.isDirectory() ? "📁" : "📄");
        icon.setTextSize(22);
        row.addView(icon);

        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(12);
        row.addView(textGroup, lp);

        TextView name = new TextView(this);
        name.setText(file.getName());
        name.setTextColor(Color.BLACK);
        name.setTextSize(14);
        textGroup.addView(name);

        TextView details = new TextView(this);
        details.setText(file.isDirectory() ? "Folder" : (file.length() / 1024) + " KB");
        details.setTextColor(Color.GRAY);
        details.setTextSize(12);
        textGroup.addView(details);

        return row;
    }

    private void navigateBack() {
        File parent = currentPath.getParentFile();
        if (parent != null && parent.canRead()) {
            currentPath = parent;
            renderFiles();
        }
    }

    private LinearLayout buildRecycleContent() {
        LinearLayout content = windowContentRoot();
        content.addView(windowSectionTitle("回收站"));
        content.addView(fileItem("旧截图.png"));
        content.addView(fileItem("未命名文档.txt"));
        content.addView(fileItem("临时安装包.apk"));
        TextView hint = new TextView(this);
        hint.setText("这些项目只是桌面模式的演示内容。");
        hint.setTextColor(Color.rgb(82, 82, 88));
        hint.setTextSize(13);
        hint.setPadding(0, dp(18), 0, 0);
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        return content;
    }

    private LinearLayout buildControlPanelContent() {
        LinearLayout content = windowContentRoot();
        content.addView(windowSectionTitle("调整计算机的设置"));
        content.addView(controlRow("个性化", "桌面背景、颜色和锁屏"));
        content.addView(controlRow("系统", "设备信息、显示与电源"));
        content.addView(controlRow("网络和共享中心", "查看网络状态和任务"));
        content.addView(controlRow("程序", "管理桌面应用和启动项"));
        return content;
    }

    private LinearLayout windowContentRoot() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(18));
        content.setBackgroundColor(Color.rgb(246, 246, 248));
        return content;
    }

    private TextView windowSectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(0, 120, 215));
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private View driveRow(String title, String subtitle, int fillPercent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(12));
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.rgb(30, 30, 34));
        label.setTextSize(14);
        row.addView(label, new LinearLayout.LayoutParams(-1, dp(26)));

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.rgb(212, 216, 220));
        View fill = new View(this);
        fill.setBackgroundColor(Color.rgb(0, 120, 215));
        bar.addView(fill, new FrameLayout.LayoutParams(dp(fillPercent * 3), -1));
        row.addView(bar, new LinearLayout.LayoutParams(dp(310), dp(12)));

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(Color.rgb(88, 88, 94));
        sub.setTextSize(12);
        row.addView(sub, new LinearLayout.LayoutParams(-1, dp(24)));
        return row;
    }

    private View fileItem(String label) {
        TextView item = new TextView(this);
        item.setText("□  " + label);
        item.setTextColor(Color.rgb(34, 34, 38));
        item.setTextSize(14);
        item.setGravity(Gravity.CENTER_VERTICAL);
        return item;
    }

    private View controlRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(70));
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(0, 92, 170));
        titleView.setTextSize(15);
        row.addView(titleView, new LinearLayout.LayoutParams(-1, dp(26)));

        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(Color.rgb(86, 86, 92));
        subView.setTextSize(12);
        row.addView(subView, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void openDesktopApp(String title, String symbol, View content) {
        ManagedWindow existing = findWindow(title);
        if (existing != null) {
            existing.minimized = false;
            existing.container.setVisibility(View.VISIBLE);
            bringToFront(existing);
            renderTaskbarApps();
            return;
        }

        final ManagedWindow window = new ManagedWindow();
        window.title = title;
        window.symbol = symbol;
        window.container = new FrameLayout(this);
        window.normalWidth = dp(540);
        window.normalHeight = dp(390);
        window.normalLeft = dp(156 + nextWindowOffset);
        window.normalTop = dp(58 + nextWindowOffset);
        nextWindowOffset = (nextWindowOffset + 26) % 104;

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(246, 246, 248));
        window.container.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(10), 0, 0, 0);
        titleBar.setBackgroundColor(Color.rgb(0, 120, 215));
        shell.addView(titleBar, new LinearLayout.LayoutParams(-1, dp(36)));

        TextView icon = textButton(symbol, Color.TRANSPARENT, Color.WHITE, 11);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(icon, new LinearLayout.LayoutParams(dp(48), -1));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));

        titleBar.addView(windowControl("—", new View.OnClickListener() {
            @Override public void onClick(View v) {
                minimizeWindow(window);
            }
        }));
        titleBar.addView(windowControl("□", new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleMaximize(window);
            }
        }));
        titleBar.addView(windowControl("×", new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeWindow(window);
            }
        }));

        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        window.container.setElevation(dp(12));
        window.container.setBackgroundColor(Color.rgb(246, 246, 248));

        enableWindowDrag(window, titleBar);
        enableWindowResize(window);
        window.container.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                bringToFront(window);
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(window.normalWidth, window.normalHeight);
        lp.leftMargin = window.normalLeft;
        lp.topMargin = window.normalTop;
        windowLayer.addView(window.container, lp);
        windows.add(window);
        bringToFront(window);
        renderTaskbarApps();
    }

    private TextView windowControl(String label, View.OnClickListener listener) {
        TextView control = textButton(label, Color.TRANSPARENT, Color.WHITE, 18);
        control.setGravity(Gravity.CENTER);
        control.setOnClickListener(listener);
        control.setClickable(true);
        control.setBackgroundColor(Color.TRANSPARENT);
        control.setPadding(0, 0, 0, dp(2));
        control.setLayoutParams(new LinearLayout.LayoutParams(dp(48), -1));
        return control;
    }

    private void enableWindowDrag(final ManagedWindow window, View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startLeft;
            private int startTop;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (window.maximized) {
                    return false;
                }
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) window.container.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startLeft = lp.leftMargin;
                        startTop = lp.topMargin;
                        bringToFront(window);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.leftMargin = Math.max(0, startLeft + Math.round(event.getRawX() - downX));
                        lp.topMargin = Math.max(0, startTop + Math.round(event.getRawY() - downY));
                        window.container.setLayoutParams(lp);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void enableWindowResize(final ManagedWindow window) {
        int hs = dp(10); // Handle size
        // Edges
        window.container.addView(createResizeHandle(window, Gravity.LEFT, hs, -1, RESIZE_LEFT));
        window.container.addView(createResizeHandle(window, Gravity.RIGHT, hs, -1, RESIZE_RIGHT));
        window.container.addView(createResizeHandle(window, Gravity.TOP, -1, hs, RESIZE_TOP));
        window.container.addView(createResizeHandle(window, Gravity.BOTTOM, -1, hs, RESIZE_BOTTOM));
        // Corners
        window.container.addView(createResizeHandle(window, Gravity.TOP | Gravity.LEFT, hs, hs, RESIZE_TOP | RESIZE_LEFT));
        window.container.addView(createResizeHandle(window, Gravity.TOP | Gravity.RIGHT, hs, hs, RESIZE_TOP | RESIZE_RIGHT));
        window.container.addView(createResizeHandle(window, Gravity.BOTTOM | Gravity.LEFT, hs, hs, RESIZE_BOTTOM | RESIZE_LEFT));
        window.container.addView(createResizeHandle(window, Gravity.BOTTOM | Gravity.RIGHT, hs, hs, RESIZE_BOTTOM | RESIZE_RIGHT));
    }

    private View createResizeHandle(final ManagedWindow window, int gravity, int width, int height, final int direction) {
        View handle = new View(this);
        handle.setLayoutParams(new FrameLayout.LayoutParams(width, height, gravity));
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startW, startH, startL, startT;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (window.maximized) return false;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) window.container.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startW = lp.width;
                        startH = lp.height;
                        startL = lp.leftMargin;
                        startT = lp.topMargin;
                        bringToFront(window);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if ((direction & RESIZE_LEFT) != 0) {
                            int delta = Math.round(dx);
                            if (startW - delta > dp(MIN_WINDOW_WIDTH)) {
                                lp.leftMargin = startL + delta;
                                lp.width = startW - delta;
                            }
                        }
                        if ((direction & RESIZE_RIGHT) != 0) {
                            lp.width = Math.max(dp(MIN_WINDOW_WIDTH), startW + Math.round(dx));
                        }
                        if ((direction & RESIZE_TOP) != 0) {
                            int delta = Math.round(dy);
                            if (startH - delta > dp(MIN_WINDOW_HEIGHT)) {
                                lp.topMargin = startT + delta;
                                lp.height = startH - delta;
                            }
                        }
                        if ((direction & RESIZE_BOTTOM) != 0) {
                            lp.height = Math.max(dp(MIN_WINDOW_HEIGHT), startH + Math.round(dy));
                        }
                        window.container.setLayoutParams(lp);
                        return true;
                }
                return false;
            }
        });
        return handle;
    }


    private ManagedWindow findWindow(String title) {
        for (ManagedWindow window : windows) {
            if (window.title.equals(title)) {
                return window;
            }
        }
        return null;
    }

    private void bringToFront(ManagedWindow window) {
        activeWindow = window;
        window.container.bringToFront();
        controlCenterPanel.bringToFront();
        renderTaskbarApps();
    }

    private void minimizeWindow(ManagedWindow window) {
        window.minimized = true;
        window.container.setVisibility(View.GONE);
        if (activeWindow == window) {
            activeWindow = null;
        }
        renderTaskbarApps();
    }

    private void toggleMaximize(ManagedWindow window) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) window.container.getLayoutParams();
        if (window.maximized) {
            lp.width = window.normalWidth;
            lp.height = window.normalHeight;
            lp.leftMargin = window.normalLeft;
            lp.topMargin = window.normalTop;
            window.maximized = false;
        } else {
            window.normalWidth = lp.width;
            window.normalHeight = lp.height;
            window.normalLeft = lp.leftMargin;
            window.normalTop = lp.topMargin;
            lp.width = -1;
            lp.height = -1;
            lp.leftMargin = 0;
            lp.topMargin = 0;
            window.maximized = true;
        }
        window.container.setLayoutParams(lp);
        bringToFront(window);
    }

    private void closeWindow(ManagedWindow window) {
        windowLayer.removeView(window.container);
        windows.remove(window);
        if (activeWindow == window) {
            activeWindow = null;
        }
        renderTaskbarApps();
    }

    private void renderTaskbarApps() {
        if (taskbarApps == null) {
            return;
        }
        taskbarApps.removeAllViews();
        for (final ManagedWindow window : windows) {
            TextView button = textButton(window.symbol + "  " + window.title,
                    activeWindow == window && !window.minimized ? Color.argb(96, 255, 255, 255) : Color.argb(36, 255, 255, 255),
                    Color.WHITE,
                    12);
            button.setGravity(Gravity.CENTER);
            button.setSingleLine(true);
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (activeWindow == window && !window.minimized) {
                        minimizeWindow(window);
                    } else {
                        window.minimized = false;
                        window.container.setVisibility(View.VISIBLE);
                        bringToFront(window);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(38));
            lp.setMargins(dp(4), dp(5), dp(4), dp(5));
            taskbarApps.addView(button, lp);
        }
    }

    private void toggleControlCenter() {
        controlCenterPanel.setVisibility(controlCenterPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        controlCenterPanel.bringToFront();
    }

    private void showStart() {
        currentMode = MODE_START;
        startRoot.setVisibility(View.VISIBLE);
        desktopRoot.setVisibility(View.GONE);
    }

    private void showDesktop() {
        currentMode = MODE_DESKTOP;
        desktopRoot.setVisibility(View.VISIBLE);
        startRoot.setVisibility(View.GONE);
        controlCenterPanel.setVisibility(View.GONE);
    }

    private void loadApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(intent, 0);
        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (pkg.equals(getPackageName())) {
                continue;
            }
            CharSequence label = info.loadLabel(packageManager);
            Drawable icon = info.loadIcon(packageManager);
            apps.add(new AppEntry(label == null ? pkg : label.toString(), pkg, cls, icon));
        }
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        allApps = apps;
    }

    private void pinDefaultApps() {
        for (int i = 0; i < allApps.size() && i < 18; i++) {
            pinned.add(allApps.get(i).key());
        }
        savePinned();
    }

    private void renderTiles() {
        if (tileRow == null) {
            return;
        }
        tileRow.removeAllViews();

        View desktopTile = createDesktopTile();
        LinearLayout.LayoutParams desktopLp = new LinearLayout.LayoutParams(dp(280), dp(104));
        desktopLp.setMargins(dp(4), dp(4), dp(34), 0);
        tileRow.addView(desktopTile, desktopLp);

        List<AppEntry> pinnedApps = new ArrayList<>();
        for (AppEntry app : allApps) {
            if (pinned.contains(app.key())) {
                pinnedApps.add(app);
            }
        }

        int index = 0;
        while (index < pinnedApps.size()) {
            GridLayout group = new GridLayout(this);
            group.setColumnCount(2);
            group.setRowCount(6); // Increased for more density
            group.setUseDefaultMargins(false);
            LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(dp(292), -2);
            groupLp.setMargins(0, 0, dp(30), 0);
            tileRow.addView(group, groupLp);

            for (int slot = 0; slot < 12 && index < pinnedApps.size(); slot++, index++) {
                AppEntry app = pinnedApps.get(index);
                // 8.1 Tile Pattern: Support for different sizes
                int span = (slot % 7 == 0) ? 2 : 1;
                View tile = createTile(app, index, span);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = span == 2 ? dp(280) : dp(136);
                lp.height = dp(136); // Square tiles by default
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, span);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                group.addView(tile, lp);
                if (span == 2) {
                    slot++;
                }
            }
        }
    }


    private View createDesktopTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(makeDesktopTileBackground());
        tile.setClickable(true);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showDesktop();
            }
        });

        TextView preview = new TextView(this);
        preview.setText("⊞  Desktop");
        preview.setTextColor(Color.WHITE);
        preview.setTextSize(20);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        tile.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView label = new TextView(this);
        label.setText("Desktop");
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return tile;
    }

    private View createTile(final AppEntry app, int index, int span) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        tile.setPadding(dp(12), dp(10), dp(12), dp(10));
        tile.setBackground(new ColorDrawable(TILE_COLORS[index % TILE_COLORS.length]));
        tile.setClickable(true);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch(app);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showPinDialog(app);
                return true;
            }
        });

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconLp.gravity = Gravity.LEFT;
        iconLp.bottomMargin = dp(12);
        tile.addView(icon, iconLp);

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(span == 2 ? 15 : 13);
        label.setMaxLines(2);
        tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return tile;
    }

    private View createAllAppsTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        tile.setBackgroundColor(Color.argb(64, 255, 255, 255));
        tile.setClickable(true);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                searchField.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        TextView plus = new TextView(this);
        plus.setText("...");
        plus.setTextColor(Color.WHITE);
        plus.setTextSize(28);
        plus.setGravity(Gravity.CENTER);
        tile.addView(plus, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView label = new TextView(this);
        label.setText("All apps");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return tile;
    }

    private void renderApps(String query) {
        if (appsPanel == null) {
            return;
        }
        appsPanel.removeAllViews();

        TextView caption = new TextView(this);
        caption.setText(query.trim().isEmpty() ? "All apps" : "Search results");
        caption.setTextColor(Color.argb(220, 255, 255, 255));
        caption.setTextSize(16);
        appsPanel.addView(caption, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        appsPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(list, new ScrollView.LayoutParams(-2, -2));

        String normalized = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (final AppEntry app : allApps) {
            if (!normalized.isEmpty() && !app.label.toLowerCase(Locale.getDefault()).contains(normalized)) {
                continue;
            }
            list.addView(createAppChip(app), new LinearLayout.LayoutParams(dp(164), dp(54)));
            shown++;
            if (shown >= 24) {
                break;
            }
        }
    }

    private View createAppChip(final AppEntry app) {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(8), 0, dp(8), 0);
        chip.setClickable(true);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch(app);
            }
        });
        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showPinDialog(app);
                return true;
            }
        });

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        chip.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setMaxLines(2);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1);
        labelLp.leftMargin = dp(8);
        chip.addView(label, labelLp);
        return chip;
    }

    private void showPinDialog(final AppEntry app) {
        final boolean isPinned = pinned.contains(app.key());
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(new CharSequence[]{isPinned ? "Unpin from Start" : "Pin to Start"}, (dialog, which) -> {
                    if (isPinned) {
                        pinned.remove(app.key());
                    } else {
                        pinned.add(app.key());
                    }
                    savePinned();
                    renderTiles();
                })
                .show();
    }

    private void launch(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(app.packageName, app.activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "Cannot open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable makeDesktopBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(0, 92, 170),
                        Color.rgb(38, 138, 205),
                        Color.rgb(76, 181, 190)
                });
    }

    private Drawable makeDesktopTileBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(0, 92, 170), Color.rgb(0, 174, 239)});
    }

    private TextView textButton(String text, int backgroundColor, int textColor, int textSize) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(textSize);
        view.setBackgroundColor(backgroundColor);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setClickable(true);
        return view;
    }

    private void savePinned() {
        prefs.edit().putStringSet(PINNED, new HashSet<>(pinned)).apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class ManagedWindow {
        String title;
        String symbol;
        FrameLayout container;
        boolean minimized;
        boolean maximized;
        int normalWidth;
        int normalHeight;
        int normalLeft;
        int normalTop;
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final String activityName;
        final Drawable icon;

        AppEntry(String label, String packageName, String activityName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
            this.icon = icon;
        }

        String key() {
            return packageName + "/" + activityName;
        }
    }
}
