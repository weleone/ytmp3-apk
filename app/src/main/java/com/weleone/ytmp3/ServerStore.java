package com.weleone.ytmp3;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ServerStore {
    private static final String PREF = "ytmp3_servers";
    private static final String KEY = "list";

    public static class Server {
        public String name, url;
        public Server(String n, String u) { name = n; url = u; }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Server> load(Context c) {
        List<Server> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Server(o.getString("name"), o.getString("url")));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void save(Context c, List<Server> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Server s : list) {
                JSONObject o = new JSONObject();
                o.put("name", s.name); o.put("url", s.url);
                arr.put(o);
            }
            prefs(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static final String KEY_CURRENT = "current_url";

    /** 当前选中的服务器 URL */
    public static String getCurrentUrl(Context c) {
        return prefs(c).getString(KEY_CURRENT, null);
    }

    public static void setCurrentUrl(Context c, String url) {
        prefs(c).edit().putString(KEY_CURRENT, url).apply();
    }

    public static String normalizeUrl(String u) {
        u = u.trim();
        if (!u.startsWith("http")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
