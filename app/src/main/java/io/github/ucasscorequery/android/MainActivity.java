/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(24, 55, 123);
    private static final int BLUE = Color.rgb(43, 101, 214);
    private static final int BLUE_LIGHT = Color.rgb(232, 240, 255);
    private static final int PAGE_BG = Color.rgb(244, 247, 252);
    private static final int TEXT_MAIN = Color.rgb(27, 37, 57);
    private static final int TEXT_MUTED = Color.rgb(103, 115, 139);
    private static final int BORDER = Color.rgb(224, 230, 240);
    private static final int GREEN = Color.rgb(26, 139, 96);
    private static final int GREEN_LIGHT = Color.rgb(229, 247, 239);
    private static final int RED = Color.rgb(201, 55, 67);
    private static final int RED_LIGHT = Color.rgb(255, 235, 238);
    private static final int ORANGE = Color.rgb(199, 112, 22);
    private static final int ORANGE_LIGHT = Color.rgb(255, 244, 224);
    private static final int GOLD = Color.rgb(224, 164, 28);
    private static final int GOLD_LIGHT = Color.rgb(255, 249, 226);

    private static final int PAGE_SCORES = 0;
    private static final int PAGE_QUERY = 1;
    private static final int PAGE_SETTINGS = 2;

    private static final int[] INTERVAL_VALUES = {15, 30, 60, 120, 240, 360, 720, 1440};
    private static final String[] INTERVAL_LABELS = {
            "15 分钟", "30 分钟", "1 小时", "2 小时",
            "4 小时", "6 小时", "12 小时", "24 小时"
    };
    private static final int[] RETRY_VALUES = {0, 1, 2, 3, 5};
    private static final String[] RETRY_LABELS = {
            "不重试", "失败后重试 1 次", "失败后重试 2 次",
            "失败后重试 3 次", "失败后重试 5 次"
    };

    private EditText usernameInput;
    private EditText passwordInput;
    private EditText tokenInput;
    private EditText modelInput;
    private CheckBox autoEnabledInput;
    private Spinner intervalInput;
    private Spinner retryInput;
    private RadioGroup notifyGroup;
    private RadioButton notifyNew;
    private RadioButton notifyAll;

    private Button queryButton;
    private Button navResults;
    private Button navQuery;
    private Button navSettings;
    private View resultsPage;
    private View queryPage;
    private View settingsPage;
    private TextView runStatus;
    private ProgressBar queryProgressBar;
    private long lastUiRepairAt;
    private TextView heroStatus;
    private TextView heroTime;
    private TextView heroCount;
    private LinearLayout autoSummaryBody;
    private LinearLayout manualSummaryBody;
    private LinearLayout scoreListContainer;
    private TextView nextRunCountdown;
    private TextView nextRunClock;
    private TextView backgroundProtectionStatus;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTicker = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            updateBackgroundProtection();
            updateLiveProgress();
            uiHandler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        NotificationHelper.createChannel(this);
        setContentView(buildContent());
        loadSettingsIntoForm();
        refreshRecords();
        showPage(PAGE_SCORES);
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppPrefs.loadSettings(this).autoEnabled) {
            Scheduler.ensureHealthy(this, true);
        }
        if (autoSummaryBody != null) refreshRecords();
        uiHandler.removeCallbacks(countdownTicker);
        uiHandler.post(countdownTicker);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(countdownTicker);
        super.onPause();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }


    private View buildContent() {
        final LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.WHITE);
        shell.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top = Build.VERSION.SDK_INT >= 30
                        ? insets.getSystemWindowInsetTop() : 0;
                int bottom = Build.VERSION.SDK_INT >= 30
                        ? insets.getSystemWindowInsetBottom() : 0;
                view.setPadding(0, top + dp(14), 0, bottom);
                return insets;
            }
        });

        FrameLayout pageHost = new FrameLayout(this);
        pageHost.setBackgroundColor(PAGE_BG);
        resultsPage = buildResultsPage();
        queryPage = buildQueryPage();
        settingsPage = buildSettingsPage();
        pageHost.addView(resultsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageHost.addView(queryPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageHost.addView(settingsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        shell.requestApplyInsets();
        return shell;
    }


    private View buildResultsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(buildHero());
        root.addView(buildScoresSection());
        return scroll;
    }

    private View buildQueryPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(14));
        TextView title = text("查询中心", 25, true);
        title.setTextColor(NAVY);
        TextView subtitle = text("手动查询、运行状态与历史记录", 13, false);
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setPadding(0, dp(4), 0, 0);
        header.addView(title);
        header.addView(subtitle);
        root.addView(header);

        root.addView(buildCountdownCard());
        root.addView(buildQueryAction());
        root.addView(buildQueryOverview());
        return scroll;
    }

    private View buildSettingsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(14));
        TextView title = text("查询设置", 25, true);
        title.setTextColor(NAVY);
        TextView subtitle = text("管理账号、自动查询与通知规则", 13, false);
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setPadding(0, dp(4), 0, 0);
        header.addView(title);
        header.addView(subtitle);
        root.addView(header);

        root.addView(buildBackgroundProtection());
        root.addView(buildAutoSettings());
        root.addView(buildAccountSettings());

        Button saveButton = button("保存设置", true);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        saveParams.setMargins(0, 0, 0, dp(10));
        root.addView(saveButton, saveParams);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings(true);
            }
        });

        TextView note = text("保存后，自动查询任务会按新设置重新安排。账号、密码和接口密钥只加密保存在本机。", 12, false);
        note.setTextColor(TEXT_MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), 0, dp(8), dp(8));
        root.addView(note);
        return scroll;
    }


    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(12));

        navResults = navigationButton("成绩");
        navQuery = navigationButton("查询");
        navSettings = navigationButton("设置");

        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, dp(50), 1f);
        first.setMargins(0, 0, dp(4), 0);
        nav.addView(navResults, first);

        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, dp(50), 1f);
        middle.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(navQuery, middle);

        LinearLayout.LayoutParams last = new LinearLayout.LayoutParams(0, dp(50), 1f);
        last.setMargins(dp(4), 0, 0, 0);
        nav.addView(navSettings, last);

        navResults.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPage(PAGE_SCORES);
            }
        });
        navQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPage(PAGE_QUERY);
            }
        });
        navSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPage(PAGE_SETTINGS);
            }
        });
        return nav;
    }

    private void showPage(int page) {
        if (resultsPage == null || queryPage == null || settingsPage == null) return;
        resultsPage.setVisibility(page == PAGE_SCORES ? View.VISIBLE : View.GONE);
        queryPage.setVisibility(page == PAGE_QUERY ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        styleNavigation(navResults, page == PAGE_SCORES);
        styleNavigation(navQuery, page == PAGE_QUERY);
        styleNavigation(navSettings, page == PAGE_SETTINGS);
        if (page == PAGE_SCORES || page == PAGE_QUERY) refreshRecords();
    }



    private void styleNavigation(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : TEXT_MUTED);
        button.setBackground(roundRect(selected ? BLUE : Color.WHITE,
                16, selected ? 0 : 1, selected ? Color.TRANSPARENT : BORDER));
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(16));
        hero.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {Color.rgb(19, 49, 117), Color.rgb(51, 112, 220)}));
        LinearLayout.LayoutParams heroParams = matchWrap();
        heroParams.setMargins(0, 0, 0, dp(14));
        hero.setLayoutParams(heroParams);
        hero.setElevation(dp(5));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("UCAS", 14, true);
        badge.setTextColor(NAVY);
        badge.setGravity(Gravity.CENTER);
        badge.setLetterSpacing(0.08f);
        badge.setBackground(roundRect(Color.WHITE, 22, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(64), dp(42));
        badgeParams.setMargins(0, 0, dp(12), 0);
        brandRow.addView(badge, badgeParams);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("国科大成绩助手", 22, true);
        title.setTextColor(Color.WHITE);
        TextView subtitle = text("每门课程清晰呈现", 13, false);
        subtitle.setTextColor(Color.argb(220, 255, 255, 255));
        subtitle.setPadding(0, dp(3), 0, 0);
        brandText.addView(title);
        brandText.addView(subtitle);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(brandRow);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(17), 0, 0);
        heroStatus = metricValue("未查询");
        heroTime = metricValue("—");
        heroCount = metricValue("0 门");
        metrics.addView(metricCell("自动查询", heroStatus), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(metricCell("最近更新", heroTime), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(metricCell("课程数量", heroCount), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(metrics);
        return hero;
    }


    private View buildCountdownCard() {
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("下次自动查询", 17, true);
        title.setTextColor(NAVY);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(chip("实时倒计时", BLUE, BLUE_LIGHT));
        card.addView(header);

        nextRunCountdown = text("未启用", 25, true);
        nextRunCountdown.setTextColor(BLUE);
        nextRunCountdown.setGravity(Gravity.CENTER);
        nextRunCountdown.setPadding(0, dp(14), 0, dp(4));
        card.addView(nextRunCountdown);

        nextRunClock = text("在设置页启用自动查询后显示", 12, false);
        nextRunClock.setTextColor(TEXT_MUTED);
        nextRunClock.setGravity(Gravity.CENTER);
        card.addView(nextRunClock);
        return card;
    }

    private View buildBackgroundProtection() {
        LinearLayout card = card();
        card.addView(sectionTitle("后台运行保障"));
        TextView intro = text(
                "启用后会显示常驻通知，并使用唤醒闹钟与系统任务双重保障熄屏查询。",
                13, false);
        intro.setTextColor(TEXT_MUTED);
        intro.setLineSpacing(0, 1.15f);
        card.addView(intro);

        backgroundProtectionStatus = infoBox(
                "正在检查后台权限…", BLUE, BLUE_LIGHT);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, dp(10), 0, dp(10));
        card.addView(backgroundProtectionStatus, statusParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button exactButton = button("精确闹钟", false);
        Button batteryButton = button("电池放行", false);
        Button notificationButton = button("通知权限", false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1f);
        left.setMargins(0, 0, dp(4), 0);
        actions.addView(exactButton, left);
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, dp(48), 1f);
        middle.setMargins(dp(4), 0, dp(4), 0);
        actions.addView(batteryButton, middle);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1f);
        right.setMargins(dp(4), 0, 0, 0);
        actions.addView(notificationButton, right);
        card.addView(actions);

        exactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestExactAlarmAccess();
            }
        });
        batteryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestBatteryOptimizationExemption();
            }
        });
        notificationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestNotificationAccess();
            }
        });

        TextView warning = text(
                "说明：从系统设置中“强行停止”应用后，Android 会禁止任何闹钟、任务和服务自行恢复，必须重新打开应用；普通划掉后台不会取消自动查询。",
                12, false);
        warning.setTextColor(ORANGE);
        warning.setLineSpacing(0, 1.18f);
        warning.setPadding(0, dp(10), 0, 0);
        card.addView(warning);
        return card;
    }

    private View buildQueryAction() {
        LinearLayout card = card();
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout headingText = new LinearLayout(this);
        headingText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("立即查询", 18, true);
        title.setTextColor(NAVY);
        TextView subtitle = text("使用“设置”页中保存的账号与接口配置", 12, false);
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setPadding(0, dp(3), 0, 0);
        headingText.addView(title);
        headingText.addView(subtitle);
        heading.addView(headingText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heading.addView(chip("手动查询", BLUE, BLUE_LIGHT));
        card.addView(heading);

        queryButton = button("查询最新成绩", true);
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        queryParams.setMargins(0, dp(14), 0, 0);
        card.addView(queryButton, queryParams);
        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runManualQuery();
            }
        });

        queryProgressBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        queryProgressBar.setMax(100);
        queryProgressBar.setProgress(0);
        queryProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.setMargins(0, dp(12), 0, 0);
        card.addView(queryProgressBar, progressParams);

        runStatus = text("点击按钮后会逐步显示登录、验证码识别和成绩读取进度。", 13, false);
        runStatus.setTextColor(TEXT_MUTED);
        runStatus.setPadding(dp(2), dp(10), dp(2), 0);
        runStatus.setLineSpacing(0, 1.15f);
        card.addView(runStatus);
        return card;
    }


    private View buildScoresSection() {
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("课程成绩", 19, true);
        title.setTextColor(NAVY);
        TextView subtitle = text("按学期分组，学位课使用黄色描边", 12, false);
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setPadding(0, dp(3), 0, 0);
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(chip("最新成功结果", BLUE, BLUE_LIGHT));
        card.addView(header);

        scoreListContainer = new LinearLayout(this);
        scoreListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.setMargins(0, dp(14), 0, 0);
        card.addView(scoreListContainer, listParams);
        return card;
    }


    private View buildQueryOverview() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout autoCard = card();
        autoCard.addView(compactHeader("自动查询记录", "后台", ORANGE, ORANGE_LIGHT));
        autoSummaryBody = new LinearLayout(this);
        autoSummaryBody.setOrientation(LinearLayout.VERTICAL);
        autoCard.addView(autoSummaryBody, matchWrap());
        group.addView(autoCard);

        LinearLayout manualCard = card();
        manualCard.addView(compactHeader("手动查询记录", "即时", BLUE, BLUE_LIGHT));
        manualSummaryBody = new LinearLayout(this);
        manualSummaryBody.setOrientation(LinearLayout.VERTICAL);
        manualCard.addView(manualSummaryBody, matchWrap());
        group.addView(manualCard);
        return group;
    }

    private LinearLayout compactHeader(String title, String tag, int tagColor, int tagBackground) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(8));
        TextView titleView = text(title, 15, true);
        titleView.setTextColor(TEXT_MAIN);
        row.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(chip(tag, tagColor, tagBackground));
        return row;
    }

    private View buildAutoSettings() {
        LinearLayout card = card();
        card.addView(sectionTitle("自动查询与通知"));

        LinearLayout enabledRow = new LinearLayout(this);
        enabledRow.setOrientation(LinearLayout.HORIZONTAL);
        enabledRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout enabledText = new LinearLayout(this);
        enabledText.setOrientation(LinearLayout.VERTICAL);
        TextView enabledTitle = text("启用自动查询", 16, true);
        TextView enabledSub = text("后台定期检查成绩变化", 12, false);
        enabledSub.setTextColor(TEXT_MUTED);
        enabledText.addView(enabledTitle);
        enabledText.addView(enabledSub);
        enabledRow.addView(enabledText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        autoEnabledInput = new CheckBox(this);
        enabledRow.addView(autoEnabledInput);
        card.addView(enabledRow);

        card.addView(divider());
        card.addView(label("查询间隔"));
        intervalInput = spinner(INTERVAL_LABELS);
        card.addView(intervalInput, matchWrap());

        card.addView(label("整轮查询失败重试"));
        retryInput = spinner(RETRY_LABELS);
        card.addView(retryInput, matchWrap());

        TextView reliability = infoBox(
                "稳定性说明：自动查询使用前台常驻服务、熄屏唤醒闹钟和持久化系统任务三重保障；HTTP 422、网络超时等瞬时错误仍会重试。",
                BLUE, BLUE_LIGHT);
        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.setMargins(0, dp(10), 0, dp(8));
        reliability.setLayoutParams(infoParams);
        card.addView(reliability);

        card.addView(label("通知方式"));
        notifyGroup = new RadioGroup(this);
        notifyGroup.setOrientation(RadioGroup.VERTICAL);
        notifyNew = new RadioButton(this);
        notifyNew.setText("仅发现新成绩或成绩变化时通知");
        notifyNew.setTextColor(TEXT_MAIN);
        notifyNew.setTextSize(14);
        notifyAll = new RadioButton(this);
        notifyAll.setText("每次自动查询结束后都通知");
        notifyAll.setTextColor(TEXT_MAIN);
        notifyAll.setTextSize(14);
        notifyGroup.addView(notifyNew);
        notifyGroup.addView(notifyAll);
        card.addView(notifyGroup);

        TextView scheduleNote = text(
                "未授予精确闹钟权限或未关闭电池优化时，部分品牌手机仍可能延后执行。",
                12, false);
        scheduleNote.setTextColor(TEXT_MUTED);
        scheduleNote.setPadding(0, dp(8), 0, 0);
        card.addView(scheduleNote);
        return card;
    }

    private View buildAccountSettings() {
        LinearLayout card = card();
        card.addView(sectionTitle("账号与验证码服务"));
        TextView intro = text("用于 SEP 登录和验证码识别，仅加密保存在本机。", 13, false);
        intro.setTextColor(TEXT_MUTED);
        intro.setPadding(0, 0, 0, dp(8));
        card.addView(intro);

        usernameInput = input("国科大用户名", false);
        passwordInput = input("国科大密码", true);
        tokenInput = input("CSTCloud 大模型接口密钥", true);
        modelInput = input("验证码识别模型名称，例如 qwen3.5", false);
        card.addView(usernameInput);
        card.addView(passwordInput);
        card.addView(tokenInput);
        card.addView(modelInput);

        TextView security = infoBox(
                "应用自动兼容学号与学号@mails.ucas.ac.cn 两种身份格式。密码中的 # 会按普通字符处理。",
                GREEN, GREEN_LIGHT);
        LinearLayout.LayoutParams securityParams = matchWrap();
        securityParams.setMargins(0, dp(8), 0, 0);
        security.setLayoutParams(securityParams);
        card.addView(security);
        return card;
    }

    private void loadSettingsIntoForm() {
        AppSettings settings = AppPrefs.loadSettings(this);
        usernameInput.setText(settings.credentials.username);
        passwordInput.setText(settings.credentials.password);
        tokenInput.setText(settings.credentials.token);
        modelInput.setText(settings.credentials.model);
        autoEnabledInput.setChecked(settings.autoEnabled);
        intervalInput.setSelection(findIntervalIndex(settings.intervalMinutes));
        retryInput.setSelection(findRetryIndex(settings.retryCount));
        if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) notifyAll.setChecked(true);
        else notifyNew.setChecked(true);
    }

    private AppSettings readSettings() {
        Credentials credentials = new Credentials(
                usernameInput.getText().toString(),
                passwordInput.getText().toString(),
                tokenInput.getText().toString(),
                modelInput.getText().toString());
        int index = intervalInput.getSelectedItemPosition();
        int interval = index >= 0 && index < INTERVAL_VALUES.length
                ? INTERVAL_VALUES[index] : 60;
        String mode = notifyAll.isChecked()
                ? AppSettings.NOTIFY_ALL : AppSettings.NOTIFY_NEW;
        int retryIndex = retryInput.getSelectedItemPosition();
        int retryCount = retryIndex >= 0 && retryIndex < RETRY_VALUES.length
                ? RETRY_VALUES[retryIndex] : 3;
        return new AppSettings(credentials, autoEnabledInput.isChecked(),
                interval, mode, retryCount);
    }

    private boolean saveSettings(boolean showToast) {
        AppSettings settings = readSettings();
        if (settings.autoEnabled && !settings.credentials.isComplete()) {
            Toast.makeText(this, "启用自动查询前，请完整填写用户名、密码和接口密钥。",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            AppPrefs.saveSettings(this, settings);
            Scheduler.apply(this, settings);
            updateCountdown();
            updateBackgroundProtection();
            if (showToast) {
                String message = settings.autoEnabled
                        ? "设置已保存：每 "
                            + INTERVAL_LABELS[findIntervalIndex(settings.intervalMinutes)]
                            + " 查询一次，整轮失败最多重试 " + settings.retryCount
                            + " 次。后台常驻服务已启动。"
                        : "设置已保存，自动查询当前未启用。";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
            refreshRecords();
            return true;
        } catch (Exception error) {
            Toast.makeText(this, "保存失败：" + cleanError(error), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void runManualQuery() {
        final AppSettings settings = readSettings();
        if (!settings.credentials.isComplete()) {
            Toast.makeText(this, "请先在设置页完整填写用户名、密码和接口密钥。",
                    Toast.LENGTH_LONG).show();
            showPage(PAGE_SETTINGS);
            return;
        }
        if (!saveSettings(false)) return;
        queryButton.setEnabled(false);
        queryButton.setText("查询中…");
        final long started = System.currentTimeMillis();
        AppPrefs.saveQueryProgress(this, new QueryProgress(
                true, false, 1, "手动查询启动", "正在准备查询会话",
                0, Math.max(1, settings.retryCount + 1), started, started));
        updateLiveProgress();
        new Thread(new Runnable() {
            @Override
            public void run() {
                QueryRecord record;
                try {
                    QueryRunner.Result result = QueryRunner.execute(
                            settings.credentials, settings.retryCount,
                            new QueryProgressListener() {
                                @Override
                                public void onProgress(int percent, String stage, String detail,
                                                       int attempt, int maxAttempts) {
                                    AppPrefs.saveQueryProgress(MainActivity.this,
                                            new QueryProgress(true, false, percent, stage, detail,
                                                    attempt, maxAttempts, started,
                                                    System.currentTimeMillis()));
                                }
                            });
                    List<Score> scores = result.scores;
                    String retrySummary = result.attempts > 1
                            ? "，第 " + result.attempts + " 次整轮尝试成功（已重试 "
                                + (result.attempts - 1) + " 次）"
                            : "";
                    record = new QueryRecord(true, System.currentTimeMillis(),
                            System.currentTimeMillis() - started,
                            "手动查询成功" + retrySummary + "，共查询到 "
                                    + scores.size() + " 条成绩记录。",
                            scores, new ArrayList<Score>());
                    AppPrefs.saveRecord(MainActivity.this, "last_success_result", record);
                } catch (Exception error) {
                    record = new QueryRecord(false, System.currentTimeMillis(),
                            System.currentTimeMillis() - started,
                            "手动查询失败：" + cleanError(error),
                            new ArrayList<Score>(), new ArrayList<Score>());
                }
                AppPrefs.saveRecord(MainActivity.this, "last_manual_result", record);
                AppPrefs.clearQueryProgress(MainActivity.this);
                final QueryRecord finalRecord = record;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        queryButton.setEnabled(true);
                        queryButton.setText("查询最新成绩");
                        runStatus.setText(finalRecord.message);
                        runStatus.setTextColor(finalRecord.success ? GREEN : RED);
                        refreshRecords();
                    }
                });
            }
        }, "ucas-manual-query").start();
    }

    private void refreshRecords() {
        QueryRecord autoRecord = AppPrefs.loadRecord(this, "last_auto_result");
        QueryRecord manualRecord = AppPrefs.loadRecord(this, "last_manual_result");
        QueryRecord successRecord = AppPrefs.loadRecord(this, "last_success_result");
        if (successRecord == null) {
            successRecord = newestSuccessful(autoRecord, manualRecord);
            if (successRecord != null) {
                AppPrefs.saveRecord(this, "last_success_result", successRecord);
            }
        }
        renderSummary(autoSummaryBody, autoRecord, true);
        renderSummary(manualSummaryBody, manualRecord, false);
        renderScores(successRecord);
        updateHero(autoRecord, successRecord);
    }

    private QueryRecord newestSuccessful(QueryRecord first, QueryRecord second) {
        QueryRecord result = null;
        if (first != null && first.success && !first.scores.isEmpty()) result = first;
        if (second != null && second.success && !second.scores.isEmpty()
                && (result == null || second.timeMillis > result.timeMillis)) result = second;
        return result;
    }

    private void updateHero(QueryRecord autoRecord, QueryRecord successRecord) {
        AppSettings settings = AppPrefs.loadSettings(this);
        if (!settings.autoEnabled) heroStatus.setText("未启用");
        else if (autoRecord == null) heroStatus.setText("等待首次");
        else heroStatus.setText(autoRecord.success ? "运行正常" : "上次失败");

        if (successRecord == null) {
            heroTime.setText("—");
            heroCount.setText("0 门");
        } else {
            heroTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(successRecord.timeMillis)));
            heroCount.setText(successRecord.scores.size() + " 门");
        }
    }

    private void renderSummary(LinearLayout container, QueryRecord record, boolean auto) {
        if (container == null) return;
        container.removeAllViews();
        if (record == null) {
            TextView empty = text(auto ? "尚无自动查询记录" : "尚无手动查询记录",
                    13, false);
            empty.setTextColor(TEXT_MUTED);
            empty.setPadding(0, dp(3), 0, dp(3));
            container.addView(empty);
            return;
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(chip(record.success ? "成功" : "失败",
                record.success ? GREEN : RED,
                record.success ? GREEN_LIGHT : RED_LIGHT));
        TextView time = text(new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                .format(new Date(record.timeMillis)), 12, false);
        time.setTextColor(TEXT_MUTED);
        time.setGravity(Gravity.END);
        top.addView(time, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(top);

        TextView message = text(record.message, 13, false);
        message.setTextColor(record.success ? TEXT_MAIN : RED);
        message.setLineSpacing(0, 1.16f);
        message.setPadding(0, dp(8), 0, dp(4));
        container.addView(message);

        String detail = String.format(Locale.CHINA, "耗时 %.1f 秒",
                record.durationMillis / 1000.0);
        if (record.success) detail += "  ·  " + record.scores.size() + " 门课程";
        if (!record.changes.isEmpty()) detail += "  ·  " + record.changes.size() + " 项变化";
        TextView meta = text(detail, 12, false);
        meta.setTextColor(TEXT_MUTED);
        container.addView(meta);
    }

    private void renderScores(QueryRecord record) {
        if (scoreListContainer == null) return;
        scoreListContainer.removeAllViews();
        if (record == null || record.scores.isEmpty()) {
            scoreListContainer.addView(emptyState(
                    "还没有可展示的成绩",
                    "完成一次成功查询后，课程会按学期以卡片形式显示。"));
            return;
        }

        Map<String, List<Score>> groups = new LinkedHashMap<String, List<Score>>();
        for (Score score : record.scores) {
            String semester = score.semester.isEmpty() ? "其他学期" : score.semester;
            List<Score> values = groups.get(semester);
            if (values == null) {
                values = new ArrayList<Score>();
                groups.put(semester, values);
            }
            values.add(score);
        }

        for (Map.Entry<String, List<Score>> entry : groups.entrySet()) {
            LinearLayout semesterRow = new LinearLayout(this);
            semesterRow.setOrientation(LinearLayout.HORIZONTAL);
            semesterRow.setGravity(Gravity.CENTER_VERTICAL);
            semesterRow.setPadding(0, dp(6), 0, dp(8));
            TextView semester = text(entry.getKey(), 14, true);
            semester.setTextColor(NAVY);
            semesterRow.addView(semester, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            semesterRow.addView(chip(entry.getValue().size() + " 门", BLUE, BLUE_LIGHT));
            scoreListContainer.addView(semesterRow);
            for (Score score : entry.getValue()) {
                scoreListContainer.addView(courseCard(
                        score, containsScore(record.changes, score)));
            }
        }
    }


    private View courseCard(Score score, boolean changed) {
        boolean degree = isDegreeCourse(score.degreeCourse);
        int fill = changed ? Color.rgb(255, 252, 243)
                : degree ? Color.rgb(255, 253, 245) : Color.WHITE;
        int strokeColor = degree ? GOLD
                : changed ? Color.rgb(242, 190, 84) : BORDER;
        int strokeWidth = degree ? 2 : 1;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundRect(fill, 15, strokeWidth, strokeColor));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(score.courseName, 16, true);
        name.setTextColor(TEXT_MAIN);
        name.setLineSpacing(0, 1.08f);
        titleBlock.addView(name);

        if (!score.englishName.isEmpty()
                && !score.englishName.equals(score.courseName)) {
            TextView english = text(score.englishName, 11, false);
            english.setTextColor(TEXT_MUTED);
            english.setPadding(0, dp(3), 0, 0);
            english.setMaxLines(2);
            titleBlock.addView(english);
        }
        topRow.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView scoreView = text(emptyAsDash(score.score), 17, true);
        scoreView.setTextColor(scoreColor(score.score));
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setMinWidth(dp(50));
        scoreView.setPadding(dp(8), dp(5), dp(8), dp(5));
        scoreView.setBackground(roundRect(
                scoreBackground(score.score), 13, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.setMargins(dp(10), 0, 0, 0);
        topRow.addView(scoreView, scoreParams);
        card.addView(topRow);

        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        tags.setPadding(0, dp(9), 0, 0);

        TextView credit = chip("学分 " + emptyAsDash(score.credit),
                BLUE, BLUE_LIGHT);
        tags.addView(credit);
        if (degree) {
            TextView degreeTag = chip("学位课", Color.rgb(144, 96, 0), GOLD_LIGHT);
            LinearLayout.LayoutParams degreeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            degreeParams.setMargins(dp(7), 0, 0, 0);
            tags.addView(degreeTag, degreeParams);
        }
        if (changed) {
            TextView changedTag = chip("本次有变化", ORANGE, ORANGE_LIGHT);
            LinearLayout.LayoutParams changedParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            changedParams.setMargins(dp(7), 0, 0, 0);
            tags.addView(changedTag, changedParams);
        }
        card.addView(tags);

        if (!score.evaluation.isEmpty()) {
            TextView evaluation = text("评估：" + score.evaluation, 12, false);
            evaluation.setTextColor(TEXT_MUTED);
            evaluation.setPadding(0, dp(8), 0, 0);
            card.addView(evaluation);
        }
        return card;
    }

    private boolean isDegreeCourse(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;
        if (normalized.contains("非学位") || normalized.equals("否")
                || normalized.equals("no") || normalized.equals("false")
                || normalized.equals("0")) return false;
        return normalized.contains("学位") || normalized.equals("是")
                || normalized.equals("yes") || normalized.equals("true")
                || normalized.equals("1");
    }

    private boolean containsScore(List<Score> values, Score target) {
        if (values == null) return false;
        for (Score value : values) {
            if (value.key().equals(target.key())
                    && value.score.equals(target.score)) return true;
        }
        return false;
    }

    private int scoreColor(String value) {
        try {
            double numeric = Double.parseDouble(value.trim());
            if (numeric >= 85) return GREEN;
            if (numeric >= 60) return BLUE;
            return RED;
        } catch (Exception ignored) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.contains("优") || normalized.contains("通过")
                    || normalized.contains("合格")) return GREEN;
            if (normalized.contains("不") || normalized.contains("未")) return RED;
            return BLUE;
        }
    }

    private int scoreBackground(String value) {
        int color = scoreColor(value);
        if (color == GREEN) return GREEN_LIGHT;
        if (color == RED) return RED_LIGHT;
        return BLUE_LIGHT;
    }

    private View emptyState(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(22), dp(16), dp(22));
        box.setBackground(roundRect(Color.rgb(248, 250, 254), 14, 1, BORDER));
        TextView titleView = text(title, 15, true);
        titleView.setTextColor(TEXT_MAIN);
        titleView.setGravity(Gravity.CENTER);
        TextView subtitleView = text(subtitle, 12, false);
        subtitleView.setTextColor(TEXT_MUTED);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(6), 0, 0);
        box.addView(titleView);
        box.addView(subtitleView);
        return box;
    }

    private void updateCountdown() {
        if (nextRunCountdown == null || nextRunClock == null) return;
        AppSettings settings = AppPrefs.loadSettings(this);
        if (!settings.autoEnabled) {
            nextRunCountdown.setText("未启用");
            nextRunCountdown.setTextColor(TEXT_MUTED);
            nextRunClock.setText("请在设置页开启自动查询");
            return;
        }
        long next = AppPrefs.getNextRunAt(this);
        if (next <= 0L) {
            nextRunCountdown.setText("正在安排");
            nextRunCountdown.setTextColor(BLUE);
            nextRunClock.setText("后台服务正在计算下次执行时间");
            return;
        }
        long remaining = next - System.currentTimeMillis();
        if (remaining <= 0L) {
            QueryProgress progress = AppPrefs.loadQueryProgress(this);
            nextRunCountdown.setText(progress.active && progress.automatic
                    ? "正在自动查询" : "正在强制触发");
            nextRunCountdown.setTextColor(ORANGE);
            long now = System.currentTimeMillis();
            if (!progress.active && now - lastUiRepairAt > 10_000L) {
                lastUiRepairAt = now;
                Scheduler.ensureHealthy(this, true);
            }
        } else {
            nextRunCountdown.setText(formatRemaining(remaining));
            nextRunCountdown.setTextColor(BLUE);
        }
        nextRunClock.setText("预计 " + new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(next)));
    }

    private void updateLiveProgress() {
        if (runStatus == null || queryProgressBar == null || queryButton == null) return;
        QueryProgress progress = AppPrefs.loadQueryProgress(this);
        if (!progress.active) {
            queryProgressBar.setVisibility(View.GONE);
            queryButton.setEnabled(true);
            queryButton.setText("查询最新成绩");
            return;
        }
        queryProgressBar.setVisibility(View.VISIBLE);
        queryProgressBar.setIndeterminate(progress.percent <= 0 || progress.percent >= 100);
        if (!queryProgressBar.isIndeterminate()) queryProgressBar.setProgress(progress.percent);
        String attempt = progress.attempt > 0 && progress.maxAttempts > 0
                ? "（整轮 " + progress.attempt + "/" + progress.maxAttempts + "）" : "";
        String source = progress.automatic ? "自动查询" : "手动查询";
        runStatus.setText(source + " · " + progress.stage + attempt
                + "\n" + progress.detail + "\n进度 " + progress.percent + "%");
        runStatus.setTextColor(BLUE);
        queryButton.setEnabled(false);
        queryButton.setText(progress.automatic ? "自动查询进行中" : "查询中…");
    }

    private void updateBackgroundProtection() {
        if (backgroundProtectionStatus == null) return;
        AppSettings settings = AppPrefs.loadSettings(this);
        boolean exact = Scheduler.canScheduleExactAlarms(this);
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean batteryAllowed = manager != null
                && manager.isIgnoringBatteryOptimizations(getPackageName());
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean notificationsAllowed = notificationManager == null
                || Build.VERSION.SDK_INT < 24 || notificationManager.areNotificationsEnabled();
        long heartbeat = AppPrefs.getServiceHeartbeat(this);
        boolean serviceAlive = settings.autoEnabled
                && System.currentTimeMillis() - heartbeat < 90_000L;
        String status = "常驻服务：" + (serviceAlive ? "运行中" : settings.autoEnabled ? "正在自动修复" : "未启用")
                + "\n精确闹钟：" + (exact ? "已授权" : "未授权，可靠性会明显下降")
                + "\n电池优化：" + (batteryAllowed ? "已放行" : "受系统省电限制")
                + "\n最近调度：" + emptyAsDash(AppPrefs.getLastSchedulerEvent(this));
        String startError = AppPrefs.getServiceStartError(this);
        if (!startError.isEmpty()) status += "\n服务诊断：" + startError;
        if (settings.autoEnabled && !serviceAlive
                && System.currentTimeMillis() - lastUiRepairAt > 10_000L) {
            lastUiRepairAt = System.currentTimeMillis();
            Scheduler.ensureHealthy(this, true);
        }
        backgroundProtectionStatus.setText(status);
        int foreground = serviceAlive && exact && batteryAllowed && notificationsAllowed ? GREEN : ORANGE;
        int background = serviceAlive && exact && batteryAllowed && notificationsAllowed ? GREEN_LIGHT : ORANGE_LIGHT;
        backgroundProtectionStatus.setTextColor(foreground);
        backgroundProtectionStatus.setBackground(roundRect(
                background, 12, 0, Color.TRANSPARENT));
    }

    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31 || Scheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, "当前设备已允许精确闹钟。", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开精确闹钟设置：" + cleanError(error),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestBatteryOptimizationExemption() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager != null && manager.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "当前应用已不受电池优化限制。", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception second) {
                Toast.makeText(this, "无法打开电池优化设置：" + cleanError(second),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0L) {
            return String.format(Locale.CHINA, "%d天 %02d:%02d:%02d",
                    days, hours, minutes, seconds);
        }
        return String.format(Locale.CHINA, "%02d:%02d:%02d",
                hours, minutes, seconds);
    }

    private void requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 101);
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开通知设置：" + cleanError(error),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(PAGE_BG);
        return scroll;
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        root.setBackgroundColor(PAGE_BG);
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(roundRect(Color.WHITE, 17, 1,
                Color.rgb(231, 235, 243)));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(params);
        card.setElevation(dp(2));
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 18, true);
        title.setTextColor(NAVY);
        title.setPadding(0, 0, 0, dp(12));
        return title;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, true);
        label.setTextColor(TEXT_MAIN);
        label.setPadding(0, dp(12), 0, dp(6));
        return label;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(12), dp(6), dp(10), dp(6));
        spinner.setBackground(roundRect(Color.rgb(249, 251, 255), 12, 1, BORDER));
        return spinner;
    }

    private EditText input(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(143, 153, 173));
        input.setTextColor(TEXT_MAIN);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setPadding(dp(13), dp(11), dp(13), dp(11));
        input.setBackground(roundRect(Color.rgb(249, 251, 255), 12, 1, BORDER));
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(5), 0, dp(6));
        input.setLayoutParams(params);
        return input;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setBackground(roundRect(primary ? BLUE : Color.WHITE,
                14, 1, primary ? BLUE : Color.rgb(170, 194, 238)));
        button.setStateListAnimator(null);
        return button;
    }

    private Button navigationButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setStateListAnimator(null);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private TextView chip(String value, int foreground, int background) {
        TextView chip = text(value, 11, true);
        chip.setTextColor(foreground);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chip.setBackground(roundRect(background, 20, 0, Color.TRANSPARENT));
        return chip;
    }

    private TextView infoBox(String value, int foreground, int background) {
        TextView box = text(value, 12, false);
        box.setTextColor(foreground);
        box.setLineSpacing(0, 1.2f);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(roundRect(background, 12, 0, Color.TRANSPARENT));
        return box;
    }

    private TextView metricValue(String value) {
        TextView view = text(value, 14, true);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        return view;
    }

    private View metricCell(String label, TextView value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        TextView labelView = text(label, 11, false);
        labelView.setTextColor(Color.argb(190, 255, 255, 255));
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, 0, 0, dp(4));
        cell.addView(labelView);
        cell.addView(value);
        return cell;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(235, 239, 246));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(0, dp(12), 0, 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(TEXT_MAIN);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int fill, int radiusDp,
                                       int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int findIntervalIndex(int minutes) {
        for (int i = 0; i < INTERVAL_VALUES.length; i++) {
            if (INTERVAL_VALUES[i] == minutes) return i;
        }
        return 2;
    }

    private int findRetryIndex(int retryCount) {
        for (int i = 0; i < RETRY_VALUES.length; i++) {
            if (RETRY_VALUES[i] == retryCount) return i;
        }
        return 3;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String emptyAsDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private static String cleanError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
