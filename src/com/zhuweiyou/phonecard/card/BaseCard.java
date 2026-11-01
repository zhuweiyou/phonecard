package com.zhuweiyou.phonecard.card;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 所有卡片的基类：负责「分钟级刷新 / 时间变化刷新 / 多实例刷新」这套公共逻辑。
 *
 * <p>子类只需要做三件事：
 * <ol>
 *   <li>提供 {@link #tickAction()}（Manifest 里要声明同名 action）；</li>
 *   <li>提供 {@link #layoutId()}（RemoteViews 能用的布局）；</li>
 *   <li>实现 {@link #bind(Context, RemoteViews, Date)}：把数据塞进布局，并返回本次内容的指纹。</li>
 * </ol>
 *
 * <h3>防闪烁策略</h3>
 * <ol>
 *   <li><b>局部更新</b>：分钟刷新只走 {@link AppWidgetManager#partiallyUpdateAppWidget}，
 *       只改 TextView 文本，不重新 inflate 布局 → 不会整块重绘、不会闪；</li>
 *   <li><b>内容不变就不刷</b>：指纹与上次相同直接跳过；</li>
 *   <li><b>对齐整分钟</b>：下一次刷新排到下个整分（+200ms），数字跟系统时钟一起翻。</li>
 * </ol>
 */
public abstract class BaseCard extends AppWidgetProvider {

    /** 刷新周期：1 分钟。 */
    protected static final long TICK_MS = 60_000L;

    /** 每个实例上次渲染的内容指纹。key = 实例 id。 */
    private static final Map<Integer, String> LAST_SIGNATURE = new HashMap<>();

    protected abstract String tickAction();

    protected abstract int layoutId();

    /**
     * 布局版本号：改了 layout 结构（比如换成 TextClock）就 +1。
     * App 更新后第一次刷新会自动整块重刷，避免桌面一直用旧布局。
     */
    protected int layoutVersion() {
        return 1;
    }

    /** 绑定数据（含点击事件），并返回本次内容的指纹（用于跳过无变化刷新）。 */
    protected abstract String bind(Context context, RemoteViews views, Date now, int widgetId);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        // 首次添加 / 尺寸变化：必须整块更新一次，保证布局存在
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId());
            LAST_SIGNATURE.put(id, bind(context, views, new Date(), id));
            manager.updateAppWidget(id, views);
        }
        schedule(context);
    }

    @Override
    public void onEnabled(Context context) {
        schedule(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(context));
        LAST_SIGNATURE.clear();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        boolean tick = tickAction().equals(action);
        boolean systemChange = Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action);
        if (!tick && !systemChange) return;

        refreshAll(context, /* partialOnly = */ tick);
        if (tick) schedule(context);
    }

    /** 刷新本卡片的所有实例（用户可能摆了多个）。 */
    public void refreshAll(Context context, boolean partialOnly) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName self = new ComponentName(context, getClass());

        // 布局版本变了（升级安装后）→ 强制整块重刷一次，让桌面用上新布局
        String versionKey = "layout_version_" + getClass().getSimpleName();
        android.content.SharedPreferences sp =
                context.getSharedPreferences("card_state", Context.MODE_PRIVATE);
        boolean layoutUpgraded = sp.getInt(versionKey, -1) != layoutVersion();
        if (layoutUpgraded) {
            for (int id : manager.getAppWidgetIds(self)) {
                RemoteViews views = new RemoteViews(context.getPackageName(), layoutId());
                LAST_SIGNATURE.put(id, bind(context, views, new Date(), id));
                manager.updateAppWidget(id, views);
            }
            sp.edit().putInt(versionKey, layoutVersion()).apply();
            return;
        }

        for (int id : manager.getAppWidgetIds(self)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId());
            String signature = bind(context, views, new Date(), id);

            if (partialOnly) {
                String last = LAST_SIGNATURE.put(id, signature);
                if (signature.equals(last)) continue;            // 内容没变，跳过
                manager.partiallyUpdateAppWidget(id, views);      // 关键：不重建布局，不闪
            } else {
                LAST_SIGNATURE.put(id, signature);
                manager.updateAppWidget(id, views);
            }
        }
    }

    protected RemoteViews build(Context context, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId());
        bind(context, views, new Date(), widgetId);
        return views;
    }

    /**
     * 生成点击跳转的 PendingIntent。
     * 注意：Android 11+ 有包可见性限制，这里不调用 resolveActivity 判断（会误判为 null），
     * 直接创建；目标不存在时点击无反应，但不会崩。
     */
    protected PendingIntent clickIntent(Context context, int widgetId, int slot, Intent intent) {
        int requestCode = widgetId * 10 + slot;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        try {
            return PendingIntent.getActivity(context, requestCode, intent, flags);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 排下一次刷新：目标 = 下个整分钟 + 150ms（等系统时钟先翻过去）。
     *
     * <p>用「精确闹钟」才能做到秒级准点：Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限，
     * 已授权走 setExactAndAllowWhileIdle；未授权（或旧系统）退化为 setWindow(±1s)。
     * 全部只排「一次性」闹钟，每次触发后自己再排下一次，避免重复闹钟被系统批量延迟。
     * 用 RTC 而不是 RTC_WAKEUP：灭屏时不强制唤醒，省电；醒来后错过的闹钟会立刻补发。
     */
    private void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        long next = now - (now % TICK_MS) + TICK_MS + 150L;
        PendingIntent pi = tickIntent(context);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi);
                } else {
                    am.setWindow(AlarmManager.RTC, next, 1000L, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi);
            } else {
                am.setExact(AlarmManager.RTC, next, pi);
            }
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC, next, 1000L, pi);
        }
    }

    private PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, getClass()).setAction(tickAction());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        // 用类名 hash 做 requestCode，保证不同卡片互不覆盖
        return PendingIntent.getBroadcast(context, getClass().getName().hashCode(), intent, flags);
    }
}
