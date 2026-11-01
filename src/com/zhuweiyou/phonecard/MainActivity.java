package com.zhuweiyou.phonecard;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 应用入口：列出本应用里的卡片，并给出添加步骤。
 * 应用名为“手机桌面卡片”，白色时钟只是本应用里的一张卡片。
 * 底部居中展示当前安装的版本号（versionName / versionCode）。
 */
public class MainActivity extends Activity {

    private static final String[] CARDS = {
            "手机桌面卡片-白色时钟 —— 仿一加桌面时钟，数字全白（无红色 1），不显示天气",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(24);

        // 内容区（可滚动）
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("手机桌面卡片");
        title.setTextSize(22);
        title.setGravity(Gravity.START);

        StringBuilder sb = new StringBuilder();
        sb.append("\n本应用（手机桌面卡片）提供：\n");
        for (String card : CARDS) sb.append("· ").append(card).append('\n');

        sb.append("\n添加方法：\n")
                .append("1. 回到桌面，长按空白处（或双指捏合）进入编辑状态；\n")
                .append("2. 点「卡片」，在「卡片中心」里滑到最底部，点「插件」；\n")
                .append("3. 在小组件列表里找到「手机桌面卡片-白色时钟」，点它即可添加到当前页。\n")
                .append("\n移除一加自带时钟卡片：长按它 → 「移除插件」。\n");

        TextView body = new TextView(this);
        body.setTextSize(15);
        body.setLineSpacing(dp(6), 1f);
        body.setText(sb.toString());

        content.addView(title);
        content.addView(body);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);

        // 整体竖向布局：上面内容占满剩余空间（weight=1），底部固定版本号
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildVersionView());

        setContentView(root);
    }

    /** 底部居中的版本号：显示 versionName + versionCode。 */
    private TextView buildVersionView() {
        String versionName = "";
        int versionCode = 0;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (Exception ignored) {
            // 取不到就不显示具体号，保持界面不崩
        }

        TextView v = new TextView(this);
        v.setText(versionName.isEmpty()
                ? "版本未知"
                : "v" + versionName + " (" + versionCode + ")");
        v.setTextSize(12);
        v.setTextColor(0xFF9E9E9E); // 灰色，弱化
        v.setGravity(Gravity.CENTER);
        int p = dp(10);
        v.setPadding(p, p, p, p);
        return v;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
