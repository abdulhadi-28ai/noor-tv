package app.noortv.epl;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The current round of the Premier League, the Saudi Pro League and La Liga, one tab each (left and right switch),
 * in Arabic and in the TV's own time.
 * Fixtures come from TheSportsDB's free API (real kick-off times, live status, full-time scores) and are cached,
 * so the last good copy shows when offline.
 */
public class MainActivity extends Activity {
    private static final String API = "https://www.thesportsdb.com/api/v1/json/123/";
    private static final String[] LEAGUES = {"4328", "4668", "4335"};
    private static final String[] TITLES = {"الدوري الإنجليزي", "دوري روشن السعودي", "الدوري الإسباني"};
    private static final String[] TABS = {"الإنجليزي", "السعودي", "الإسباني"};
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final java.util.Set<String> DONE = new java.util.HashSet<>(java.util.Arrays.asList("FT", "AET", "PEN", "AWD", "WO", "Match Finished"));
    private static final java.util.Set<String> OFF = new java.util.HashSet<>(java.util.Arrays.asList("PST", "CANC", "ABD", "SUSP", "INT", "Postponed"));
    /** The TV's own time zone, so every kick-off shows in the viewer's time. */
    private static final ZoneId HOME = ZoneId.systemDefault();
    private static final Map<String, String> CITIES = new HashMap<>();
    static {
        String[][] c = {{"Asia/Riyadh", "الرياض"}, {"Asia/Kuwait", "الكويت"}, {"Asia/Dubai", "الإمارات"},
                {"Asia/Qatar", "قطر"}, {"Asia/Bahrain", "البحرين"}, {"Asia/Muscat", "عُمان"}, {"Africa/Cairo", "القاهرة"},
                {"Asia/Amman", "عمّان"}, {"Asia/Baghdad", "بغداد"}, {"Africa/Casablanca", "المغرب"}};
        for (String[] p : c) CITIES.put(p[0], p[1]);
    }
    private static final Duration MATCH = Duration.ofMinutes(115);

    private static final String[] DAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final int GOLD = 0xFFFFD369;

    private static final Map<String, String> TEAMS = new HashMap<>();
    static {
        String[][] t = {
                {"AFC Bournemouth", "بورنموث"}, {"Arsenal", "آرسنال"}, {"Aston Villa", "أستون فيلا"},
                {"Brentford", "برينتفورد"}, {"Brighton & Hove Albion", "برايتون"}, {"Brighton and Hove Albion", "برايتون"}, {"Bournemouth", "بورنموث"}, {"Burnley", "بيرنلي"},
                {"Chelsea", "تشيلسي"}, {"Coventry City", "كوفنتري سيتي"}, {"Crystal Palace", "كريستال بالاس"},
                {"Everton", "إيفرتون"}, {"Fulham", "فولهام"}, {"Hull City", "هل سيتي"},
                {"Ipswich Town", "إيبسويتش تاون"}, {"Leeds United", "ليدز يونايتد"}, {"Leicester City", "ليستر سيتي"},
                {"Liverpool", "ليفربول"}, {"Luton Town", "لوتون تاون"}, {"Manchester City", "مانشستر سيتي"},
                {"Manchester United", "مانشستر يونايتد"}, {"Middlesbrough", "ميدلزبره"},
                {"Newcastle United", "نيوكاسل يونايتد"}, {"Norwich City", "نوريتش سيتي"},
                {"Nottingham Forest", "نوتنغهام فورست"}, {"Sheffield United", "شيفيلد يونايتد"},
                {"Southampton", "ساوثهامبتون"}, {"Stoke City", "ستوك سيتي"}, {"Sunderland", "سندرلاند"},
                {"Tottenham Hotspur", "توتنهام"}, {"Watford", "واتفورد"}, {"West Bromwich Albion", "وست بروميتش"},
                {"West Ham United", "وست هام"}, {"Wolverhampton Wanderers", "ولفرهامبتون"},
        };
        for (String[] p : t) TEAMS.put(p[0], p[1]);
        String[][] more = {
                // Saudi Pro League
                {"Abha", "أبها"}, {"Al-Ahli", "الأهلي"}, {"Al-Diriyah", "الدرعية"}, {"Al-Ettifaq", "الاتفاق"},
                {"Al-Faisaly", "الفيصلي"}, {"Al-Fateh", "الفتح"}, {"Al-Fayha", "الفيحاء"}, {"Al-Hazem", "الحزم"},
                {"Al-Hilal", "الهلال"}, {"Al-Ittihad", "الاتحاد"}, {"Al-Khaleej", "الخليج"}, {"Al-Kholood", "الخلود"},
                {"Al-Nassr", "النصر"}, {"Al-Qadsiah", "القادسية"}, {"Al-Riyadh", "الرياض"}, {"Al-Shabab", "الشباب"},
                {"Al-Taawoun", "التعاون"}, {"Neom", "نيوم"}, {"Damac", "ضمك"}, {"Al-Okhdood", "الأخدود"},
                {"Al-Orobah", "العروبة"}, {"Al-Wehda", "الوحدة"}, {"Al-Raed", "الرائد"}, {"Al-Najma", "النجمة"},
                {"Al-Taee", "الطائي"},
                // La Liga
                {"Athletic Bilbao", "أتلتيك بيلباو"}, {"Atlético Madrid", "أتلتيكو مدريد"}, {"Barcelona", "برشلونة"},
                {"Celta Vigo", "سيلتا فيغو"}, {"Deportivo Alavés", "ألافيس"}, {"Deportivo de A Coruña", "ديبورتيفو لاكورونيا"},
                {"Elche", "إلتشي"}, {"Espanyol", "إسبانيول"}, {"Getafe", "خيتافي"}, {"Levante", "ليفانتي"},
                {"Málaga", "ملقة"}, {"Osasuna", "أوساسونا"}, {"Racing de Santander", "راسينغ سانتاندير"},
                {"Rayo Vallecano", "رايو فاليكانو"}, {"Real Betis", "ريال بيتيس"}, {"Real Madrid", "ريال مدريد"},
                {"Real Sociedad", "ريال سوسيداد"}, {"Sevilla", "إشبيلية"}, {"Valencia", "فالنسيا"},
                {"Villarreal", "فياريال"}, {"Girona", "جيرونا"}, {"Mallorca", "مايوركا"}, {"Real Oviedo", "ريال أوفييدو"},
                {"Las Palmas", "لاس بالماس"}, {"Leganés", "ليغانيس"}, {"Real Valladolid", "بلد الوليد"},
        };
        for (String[] p : more) TEAMS.put(p[0], p[1]);
    }

    private LinearLayout content, tabs;
    private TextView title, subtitle;
    private int league;
    private final List<?>[] rounds = new List<?>[LEAGUES.length];
    private final boolean[] fresh = new boolean[LEAGUES.length];
    private final boolean[] loading = new boolean[LEAGUES.length];
    private final long[] loadedAt = new long[LEAGUES.length];

    private static final class Match {
        String home, away, round, status;
        ZonedDateTime kickoff;
        int[] score; // null before kick-off

        boolean off() {
            return OFF.contains(status);
        }

        boolean finished(ZonedDateTime now) {
            return DONE.contains(status) || (score != null && now.isAfter(kickoff.plus(MATCH)));
        }

        boolean live(ZonedDateTime now) {
            return !finished(now) && !off() && !now.isBefore(kickoff) && now.isBefore(kickoff.plus(MATCH));
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(background());

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(true);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(28), dp(48), dp(24));
        scroll.addView(page);
        root.addView(scroll);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        title = text("⚽ " + TITLES[0], 30, Color.WHITE, true);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(tabs);
        page.addView(top);
        subtitle = text("جاري تحميل المباريات…", 16, GOLD, false);
        subtitle.setPadding(0, dp(2), 0, dp(16));
        page.addView(subtitle);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        page.addView(content);

        setContentView(root);
        scroll.requestFocus();
        select(0);
        // The other leagues load behind the first, one after the other (the free key refuses bursts).
        new Handler(Looper.getMainLooper()).postDelayed(() -> load(1), 6000);
        new Handler(Looper.getMainLooper()).postDelayed(() -> load(2), 12000);
    }

    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent e) {
        // Right to left: the first tab is on the right, so Left moves on and Right goes back.
        if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
            select((league + 1) % LEAGUES.length);
            return true;
        }
        if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
            select((league + LEAGUES.length - 1) % LEAGUES.length);
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private void select(int k) {
        league = k;
        title.setText("⚽ " + TITLES[k]);
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            TextView t = text(TABS[i], 16, i == k ? 0xFF0B2A17 : 0xCCFFFFFF, true);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            if (i == k) bg.setColor(GOLD);
            else bg.setStroke(dp(1), 0x55FFFFFF);
            t.setBackground(bg);
            t.setPadding(dp(18), dp(6), dp(18), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(10));
            tabs.addView(t, lp);
        }
        @SuppressWarnings("unchecked") List<Match> r = (List<Match>) rounds[k];
        if (r != null) {
            show(r, fresh[k]);
        } else {
            content.removeAllViews();
            subtitle.setText("جاري تحميل المباريات…");
        }
        load(k);
    }

    /** The person's wallpaper (res/drawable-nodpi/bg.jpg) when there is one, else a dark pitch green. */
    private Drawable background() {
        int id = getResources().getIdentifier("bg", "drawable", getPackageName());
        if (id != 0) {
            Drawable d = getDrawable(id);
            d.setColorFilter(0x99000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
            return d;
        }
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0B3D20, 0xFF04170F});
    }

    /** Fetches league k (cached copy first, so an offline TV still shows the last good round). */
    private void load(int k) {
        // Fetched in the last five minutes: what is on screen is current.
        if (loading[k] || (rounds[k] != null && fresh[k] && System.currentTimeMillis() - loadedAt[k] < 5 * 60_000L)) return;
        loading[k] = true;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String json = null;
            boolean live = true;
            File cache = new File(getFilesDir(), "round-" + LEAGUES[k] + ".json");
            try {
                json = currentRound(LEAGUES[k]);
                try (FileOutputStream out = new FileOutputStream(cache)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                live = false;
                try (InputStream in = new FileInputStream(cache)) {
                    json = new String(readAll(in), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
            }
            List<Match> round = null;
            if (json != null) {
                try {
                    round = parse(json);
                } catch (Exception ignored) {
                }
            }
            final List<Match> r = round;
            final boolean ok = live;
            main.post(() -> {
                loading[k] = false;
                if (r != null || rounds[k] == null) {
                    rounds[k] = r;
                    fresh[k] = ok;
                    if (ok) loadedAt[k] = System.currentTimeMillis();
                }
                if (league == k) {
                    @SuppressWarnings("unchecked") List<Match> shown = (List<Match>) rounds[k];
                    show(shown, fresh[k]);
                }
            });
        }).start();
    }

    /**
     * The round being played: the later of the latest finished game's round and the next game's round (so a
     * rearranged game from an old round never takes over), or the one after it once every game in it is over.
     */
    static String currentRound(String league) throws Exception {
        JSONObject last = new JSONObject(fetch(API + "eventspastleague.php?id=" + league))
                .getJSONArray("events").getJSONObject(0);
        int r = Integer.parseInt(last.getString("intRound"));
        String season = last.getString("strSeason");
        try {
            Thread.sleep(1500); // the free key refuses quick bursts (HTTP 429)
            JSONArray next = new JSONObject(fetch(API + "eventsnextleague.php?id=" + league)).optJSONArray("events");
            if (next != null && next.length() > 0 && season.equals(next.getJSONObject(0).optString("strSeason"))) {
                r = Math.max(r, Integer.parseInt(next.getJSONObject(0).getString("intRound")));
            }
        } catch (Exception ignored) {
        }
        Thread.sleep(1500);
        String json = fetch(API + "eventsround.php?id=" + league + "&r=" + r + "&s=" + season);
        ZonedDateTime now = ZonedDateTime.now(UTC);
        boolean open = false;
        for (Match m : parse(json)) open |= !m.finished(now) && !m.off();
        if (open) return json;
        Thread.sleep(1500);
        String after = fetch(API + "eventsround.php?id=" + league + "&r=" + (r + 1) + "&s=" + season);
        return parse(after).isEmpty() ? json : after;
    }

    private static String fetch(String u) throws Exception {
        // The free key answers 429 to quick bursts (Home and this app fetching together): wait and try again.
        for (int attempt = 0; ; attempt++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            try {
                int code = c.getResponseCode();
                if (code == 429 && attempt < 4) {
                    Thread.sleep(3000L * (attempt + 1));
                    continue;
                }
                if (code != 200) throw new Exception("HTTP " + code);
                return new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
            } finally {
                c.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** The round's matches from TheSportsDB's events JSON, sorted by kick-off. */
    static List<Match> parse(String json) throws Exception {
        JSONArray all = new JSONObject(json).optJSONArray("events");
        List<Match> ms = new ArrayList<>();
        if (all == null) return ms;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.getJSONObject(i);
            Match m = new Match();
            m.home = o.optString("strHomeTeam");
            m.away = o.optString("strAwayTeam");
            m.round = o.optString("intRound");
            m.status = "yes".equalsIgnoreCase(o.optString("strPostponed")) ? "PST" : o.optString("strStatus");
            String ts = o.optString("strTimestamp", "");
            if (ts.isEmpty() || "null".equals(ts)) ts = o.getString("dateEvent") + "T" + o.optString("strTime", "15:00:00");
            m.kickoff = LocalDateTime.parse(ts.replace("+00:00", "").replace("Z", "")).atZone(UTC);
            if (!o.isNull("intHomeScore") && !o.isNull("intAwayScore")) {
                try {
                    m.score = new int[]{Integer.parseInt(o.getString("intHomeScore")), Integer.parseInt(o.getString("intAwayScore"))};
                } catch (NumberFormatException ignored) {
                }
            }
            ms.add(m);
        }
        ms.sort((a, c) -> a.kickoff.compareTo(c.kickoff));
        return ms;
    }

    private void show(List<Match> round, boolean fresh) {
        content.removeAllViews();
        if (round == null) {
            subtitle.setText("تعذّر تحميل المباريات. تأكد إن التلفزيون متصل بالإنترنت وافتحها مرة ثانية");
            return;
        }
        if (round.isEmpty()) {
            subtitle.setText("انتهى الموسم. نشوفك الموسم الجاي 👋");
            return;
        }
        String n = round.get(0).round;
        subtitle.setText("الجولة " + n + "  ·  " + (CITIES.containsKey(HOME.getId()) ? "بتوقيت " + CITIES.get(HOME.getId()) : "بتوقيتك") + (fresh ? "" : "  ·  آخر نسخة محفوظة"));

        LinearLayout row = null;
        for (int i = 0; i < round.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);
                content.addView(row, lp);
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % 2 == 0) cp.setMarginEnd(dp(12));
            row.addView(card(round.get(i)), cp);
        }
        if (round.size() % 2 == 1) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    private View card(Match m) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(10), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3000000);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x33FFFFFF);
        c.setBackground(bg);

        ZonedDateTime k = m.kickoff.withZoneSameInstant(HOME);
        TextView when = text(DAYS[k.getDayOfWeek().getValue() - 1] + " " + k.getDayOfMonth() + " "
                + MONTHS[k.getMonthValue() - 1], 13, 0xB3FFFFFF, false);
        when.setGravity(Gravity.CENTER);
        c.addView(when);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(4), 0, 0);

        TextView home = text(name(m.home), 19, Color.WHITE, true);
        home.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        line.addView(home, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ZonedDateTime now = ZonedDateTime.now(HOME);
        String mid;
        int midColour = GOLD;
        if (m.off()) {
            mid = "مؤجلة";
            midColour = 0x99FFFFFF;
        } else if (m.score != null && m.finished(now)) {
            mid = m.score[0] + " - " + m.score[1];
            midColour = Color.WHITE;
        } else if (m.live(now)) {
            mid = m.score != null ? m.score[0] + " - " + m.score[1] + "  مباشر" : "مباشر";
            midColour = 0xFFFF5A5A;
        } else if (m.finished(now)) {
            mid = "انتهت";
            midColour = 0x99FFFFFF;
        } else {
            mid = clock(k);
        }
        TextView centre = text(mid, 18, midColour, true);
        centre.setGravity(Gravity.CENTER);
        centre.setTextDirection(View.TEXT_DIRECTION_RTL);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x26FFFFFF);
        pill.setCornerRadius(dp(10));
        centre.setBackground(pill);
        centre.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.setMarginStart(dp(10));
        mp.setMarginEnd(dp(10));
        line.addView(centre, mp);

        TextView away = text(name(m.away), 19, Color.WHITE, true);
        away.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        line.addView(away, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        c.addView(line);
        return c;
    }

    /** "6:00 م" */
    private static String clock(ZonedDateTime t) {
        int h = t.getHour() % 12;
        if (h == 0) h = 12;
        return h + ":" + String.format(java.util.Locale.US, "%02d", t.getMinute()) + (t.getHour() < 12 ? " ص" : " م");
    }

    static String name(String club) {
        String s = club.replaceAll("^AFC ", "").replaceAll(" (A?FC|AFC)$", "").trim();
        String ar = TEAMS.get(s);
        if (ar == null) ar = TEAMS.get(club);
        return ar != null ? ar : s;
    }

    private TextView text(String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(colour);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setSingleLine(false);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setShadowLayer(6, 0, 2, 0x99000000);
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
