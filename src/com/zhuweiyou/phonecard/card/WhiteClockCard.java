package com.zhuweiyou.phonecard.card;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

import com.zhuweiyou.phonecard.R;

import java.util.Locale;

/**
 * 卡片 01：手机桌面卡片-白色时钟（自包含；时间、日期都由 TextClock 自走，App 端零刷新）。
 *
 * <p>仿一加桌面大时钟：一行小号日期 + 超大号时间，透明背景；数字统一白色，不显示天气。
 *
 * <h3>刷新策略：App 端不做任何刷新</h3>
 * <ul>
 *   <li><b>时间</b>、<b>日期</b> 都是布局里的 {@code TextClock}，由桌面进程自己按时钟走时，
 *       跨分/跨天/改时间/换时区/换语言都会自动更新 → 无需刷新、无需权限、无需闹钟；</li>
 *   <li>唯一例外是 <b>App 升级</b>：{@code MY_PACKAGE_REPLACED} 时重建一次，
 *       让桌面换上新的布局/字号（否则旧 RemoteViews 缓存不会更新）。</li>
 * </ul>
 *
 * <p>交互：点日期/星期 → 系统日历；点时间 → 系统时钟。
 */
public class WhiteClockCard extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, id));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // 仅"App 升级"时重建一次，让新布局生效；其余交给父类（APPWIDGET_UPDATE → onUpdate）
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName self = new ComponentName(context, WhiteClockCard.class);
            for (int id : manager.getAppWidgetIds(self)) {
                manager.updateAppWidget(id, build(context, id));
            }
            return;
        }
        super.onReceive(context, intent);
    }

    /** 构建一张卡片实例：布局里两个 TextClock 自己走时，这里只挂点击跳转。 */
    private RemoteViews build(Context context, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.card_white_clock);

        // 点日期 → 系统日历
        Intent calendar = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
        PendingIntent cal = clickIntent(context, widgetId, 1, calendar);
        if (cal != null) views.setOnClickPendingIntent(R.id.date, cal);

        // 点时间 → 系统时钟
        PendingIntent clk = clickIntent(context, widgetId, 2, clockIntent(context));
        if (clk != null) views.setOnClickPendingIntent(R.id.time, clk);

        return views;
    }

    /** 找到系统的时钟/闹钟 App 并返回启动它的 Intent。 */
    private static Intent clockIntent(Context context) {
        android.content.pm.PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
            if (pkg.contains("alarmclock") || pkg.contains("deskclock") || pkg.contains(".clock")) {
                return new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        return new Intent(AlarmClock.ACTION_SHOW_ALARMS);
    }

    /**
     * 生成点击跳转的 PendingIntent。requestCode 用 widgetId*10+slot，多实例互不覆盖。
     * 注意 Android 11+ 包可见性：这里不调用 resolveActivity 判断（会误判 null），
     * 直接创建；目标不存在时点击无反应，但不会崩。
     */
    private static PendingIntent clickIntent(Context context, int widgetId, int slot, Intent intent) {
        int requestCode = widgetId * 10 + slot;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        try {
            return PendingIntent.getActivity(context, requestCode, intent, flags);
        } catch (Exception e) {
            return null;
        }
    }
}
