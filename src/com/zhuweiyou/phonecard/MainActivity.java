package com.zhuweiyou.phonecard;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 应用入口：列出本应用里的卡片，并给出添加步骤。
 * 应用名为“手机桌面卡片”，白色时钟只是本应用里的一张卡片。
 */
public class MainActivity extends Activity {

    private static final String[] CARDS = {
            "手机桌面卡片-白色时钟 —— 仿一加桌面时钟，数字全白（无红色 1），不显示天气",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

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
                .append("\n移除一加自带时钟卡片：长按它 → 「移除插件」。\n")
                .append("\n样式参数在 res/layout/card_white_clock.xml；")
                .append("新增卡片见工程根目录 DESIGN.md。");

        TextView body = new TextView(this);
        body.setTextSize(15);
        body.setLineSpacing(dp(6), 1f);
        body.setText(sb.toString());

        root.addView(title);
        root.addView(body);
        scroll.addView(root);
        setContentView(scroll);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
