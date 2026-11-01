package com.zhuweiyou.phonecard.card;

import android.provider.AlarmClock;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.zhuweiyou.phonecard.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 卡片 01：手机桌面卡片-白色时钟
 *
 * 仿一加桌面大时钟：一行小号日期 + 超大号时间，透明背景；
 * 区别是所有数字统一白色（没有一加红 "1"），也不显示天气。
 *
 * 交互（对齐一加原卡片）：
 * <ul>
 *   <li>点日期/星期 → 打开系统日历</li>
 *   <li>点时间 → 打开系统时钟（闹钟）</li>
 * </ul>
 */
public class WhiteClockCard extends BaseCard {

    /** 每分钟刷新的私有广播 action，必须与 AndroidManifest 中声明的一致。 */
    public static final String ACTION_TICK = "com.zhuweiyou.phonecard.action.WHITE_CLOCK_TICK";

    @Override
    protected String tickAction() {
        return ACTION_TICK;
    }

    @Override
    protected int layoutVersion() {
        return 2;   // 2 = 时间改用 TextClock（不再靠广播刷文本）
    }

    @Override
    protected int layoutId() {
        return R.layout.card_white_clock;
    }

    /** 找到系统的时钟/闹钟 App 并返回启动它的 Intent。 */
    private Intent clockIntent(Context context) {
        android.content.pm.PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
            if (pkg.contains("alarmclock") || pkg.contains("deskclock") || pkg.contains(".clock")) {
                return new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new android.content.ComponentName(
                                ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        return new Intent(AlarmClock.ACTION_SHOW_ALARMS);
    }

    @Override
    protected String bind(Context context, RemoteViews views, Date now, int widgetId) {
        // 时间交给布局里的 TextClock 自己走，这里只维护日期
        String date = new SimpleDateFormat("M月d日  E", Locale.CHINA).format(now);
        views.setTextViewText(R.id.date, date);

        // 点日期 → 系统日历（自动解析默认日历应用）
        Intent calendar = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR);
        PendingIntent cal = clickIntent(context, widgetId, 1, calendar);
        if (cal != null) views.setOnClickPendingIntent(R.id.date, cal);

        // 点时间 → 系统时钟（优先直接打开时钟 App 的主界面，兼容各厂商包名）
        Intent clock = clockIntent(context);
        PendingIntent clk = clickIntent(context, widgetId, 2, clock);
        if (clk != null) views.setOnClickPendingIntent(R.id.time, clk);

        // 指纹：只跟日期有关（时间由 TextClock 自己刷新）
        return date;
    }
}
