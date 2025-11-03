package com.leo.danmu;  // ← 必须和 pom.xml 一致

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LeoDanmuPlugin {
    private static final String API_BASE = "https://danmu.leo123.cn:666/omnibox/api/v2";
    private static Context context;
    private static Object player;

    // ===== JarLoader 入口 =====
    public static void init(Context ctx, Object ply) {
        context = ctx;
        player = ply;
        showToast("Leo弹幕启动成功");
        registerMenu();
    }

    private static void registerMenu() {
        try {
            Class<?> clazz = player.getClass();
            Method addMenu = clazz.getMethod("addMenuItem", String.class, String.class, Runnable.class);
            addMenu.invoke(player, "leo_danmu", "Leo弹幕", (Runnable) LeoDanmuPlugin::showSearchDialog);
        } catch (Exception e) {
            showToast("注册按钮失败: " + e.getMessage());
        }
    }

    private static void showSearchDialog() {
        String title = getCurrentTitle();
        if (title == null) title = "";
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_Translucent_NoTitleBar);
        View view = createSearchView(title);
        AlertDialog dialog = builder.setView(view).create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private static View createSearchView(String defaultText) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        EditText editText = new EditText(context);
        editText.setText(defaultText);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.LTGRAY);
        editText.setHint("输入剧名搜索弹幕");
        editText.setBackgroundColor(Color.argb(100, 0, 0, 0));

        Button searchBtn = new Button(context);
        searchBtn.setText("搜索");
        searchBtn.setTextColor(Color.WHITE);
        searchBtn.setBackgroundColor(Color.argb(150, 255, 255, 255));

        LinearLayout inputLayout = new LinearLayout(context);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.addView(editText, new LinearLayout.LayoutParams(0, -2, 1));
        inputLayout.addView(searchBtn, new LinearLayout.LayoutParams(-2, -2));

        layout.addView(inputLayout, new LinearLayout.LayoutParams(-1, -2));
        searchBtn.setOnClickListener(v -> {
            String key = editText.getText().toString().trim();
            if (!key.isEmpty()) {
                searchAnime(key, layout);
            }
        });
        return layout;
    }

    private static void searchAnime(String key, LinearLayout parent) {
        new Thread(() -> {
            try {
                String url = API_BASE + "/search/anime?keyword=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
                String json = httpGet(url);
                if (json == null) {
                    runOnUiThread(() -> showToast("网络错误"));
                    return;
                }
                JSONObject result = JSON.parseObject(json);
                if (result.getInteger("errorCode") != 0) {
                    runOnUiThread(() -> showToast("搜索无结果"));
                    return;
                }
                JSONArray animes = result.getJSONArray("animes");
                List<JSONObject> items = new ArrayList<>();
                for (int i = 0; i < animes.size(); i++) {
                    JSONObject a = animes.getJSONObject(i);
                    JSONObject item = new JSONObject();
                    item.put("id", a.getString("animeId"));
                    item.put("title", a.getString("animeTitle"));
                    item.put("pic", a.getString("imageUrl"));
                    item.put("remark", a.getString("type"));
                    items.add(item);
                }
                runOnUiThread(() -> showAnimeList(items, parent));
            } catch (Exception e) {
                runOnUiThread(() -> showToast("搜索异常"));
            }
        }).start();
    }

    private static void showAnimeList(List<JSONObject> items, LinearLayout parent) {
        parent.removeViews(1, parent.getChildCount() - 1);
        ListView listView = new ListView(context);
        listView.setBackgroundColor(Color.argb(150, 0, 0, 0));
        listView.setDividerHeight(0);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                tv.setPadding(30, 25, 30, 25);
                tv.setTextSize(16);
                JSONObject item = items.get(position);
                tv.setText(item.getString("title") + "  [" + item.getString("remark") + "]");
                return tv;
            }
        };
        for (int i = 0; i < items.size(); i++) adapter.add("");
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((p, v, pos, id) -> {
            String animeId = items.get(pos).getString("id");
            showEpisodeList(animeId, parent);
        });
        parent.addView(listView, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void showEpisodeList(String animeId, LinearLayout parent) {
        new Thread(() -> {
            try {
                String url = API_BASE + "/bangumi/" + animeId;
                String json = httpGet(url);
                if (json == null) {
                    runOnUiThread(() -> showToast("网络错误"));
                    return;
                }
                JSONObject result = JSON.parseObject(json);
                JSONArray episodes = result.getJSONArray("episodes");
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < episodes.size(); i++) {
                    list.add(episodes.getJSONObject(i));
                }
                runOnUiThread(() -> {
                    parent.removeViews(1, parent.getChildCount() - 1);
                    ListView lv = new ListView(context);
                    lv.setBackgroundColor(Color.argb(150, 0, 0, 0));
                    lv.setDividerHeight(0);
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1) {
                        @Override
                        public View getView(int pos, View v, ViewGroup p) {
                            TextView tv = (TextView) super.getView(pos, v, p);
                            tv.setTextColor(Color.WHITE);
                            tv.setPadding(30, 25, 30, 25);
                            tv.setTextSize(16);
                            tv.setText(list.get(pos).getString("episodeTitle"));
                            return tv;
                        }
                    };
                    for (int i = 0; i < list.size(); i++) adapter.add("");
                    lv.setAdapter(adapter);
                    lv.setOnItemClickListener((p, v, pos, id) -> {
                        String epId = list.get(pos).getString("episodeId");
                        String danmuUrl = API_BASE + "/comment/" + epId + "?format=xml";
                        pushDanmu(danmuUrl);
                        showToast("弹幕推送成功");
                        closeDialog(parent);
                    });
                    parent.addView(lv, new LinearLayout.LayoutParams(-1, -2));
                });
            } catch (Exception e) {
                runOnUiThread(() -> showToast("获取集数失败"));
            }
        }).start();
    }

    private static void closeDialog(LinearLayout layout) {
        ViewGroup vg = (ViewGroup) layout.getParent();
        if (vg != null) vg.removeView(layout);
    }

    private static void pushDanmu(String url) {
        try {
            Class<?> danmuClass = Class.forName("com.fongmi.android.tv.bean.Danmu");
            Method push = danmuClass.getMethod("push", String.class);
            push.invoke(null, url);
        } catch (Exception e) {
            showToast("推送失败: " + e.getMessage());
        }
    }

    private static String getCurrentTitle() {
        try {
            Method getVod = player.getClass().getMethod("getVodInfo");
            Object vodInfo = getVod.invoke(player);
            Method getName = vodInfo.getClass().getMethod("getVodName");
            return (String) getName.invoke(vodInfo);
        } catch (Exception e) {
            return null;
        }
    }

    private static String httpGet(String url) {
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            HttpGet get = new HttpGet(url);
            get.setHeader("User-Agent", "TVBox");
            CloseableHttpResponse resp = client.execute(get);
            return EntityUtils.toString(resp.getEntity(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static void runOnUiThread(Runnable r) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(r);
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }

    private static void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }
}
