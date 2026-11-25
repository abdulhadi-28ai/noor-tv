package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The current round of the Premier League, the Saudi Pro League and La Liga on Home, one league at a time in a strip
 * along the bottom, taking turns every 12 seconds: for each game the home crest, kick-off (24-hour, then LIVE, then
 * the score) and the away crest. It shows while the apps are hidden (CleanHome) and steps aside when they come up.
 * Fixtures come from TheSportsDB's free API (real kick-off times, status and full-time scores): the round being
 * played, or the next once it is over. Each league is refreshed every 30 minutes, every 10 while one of its games is
 * on, and cached for offline days. The crests are bundled (res/drawable-nodpi: crest_t<team id>.png, or crest_<name>
 * for the Premier League). Never focusable.
 */
final class Fixtures {
    private static final String API = "https://www.thesportsdb.com/api/v1/json/123/";
    /** The leagues the strip takes turns with, every CYCLE ms: TheSportsDB id, the strip's name, its colour. */
    private static final String[] IDS = {"4328", "4668", "4335"};
    private static final String[] NAMES = {"PREMIER LEAGUE", "SAUDI PRO LEAGUE", "LA LIGA"};
    private static final int[] BARS = {0xFF3DFF8F, 0xFF1FB45A, 0xFFFF5C4D};
    private static final long CYCLE = 12_000L;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Duration MATCH = Duration.ofMinutes(115);
    private static final long REFRESH = 30 * 60_000L, REFRESH_LIVE = 10 * 60_000L;
    private static final int COLUMNS = 10;    // one strip along the bottom
    private static final int CREST = 20;      // dp
    private static final int MID = 40;        // dp, the kick-off / score column
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("EEE d", Locale.US);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US); // 24-hour, like the clock
    private static final Set<String> DONE = new HashSet<>(Arrays.asList("FT", "AET", "PEN", "AWD", "WO", "Match Finished"));
    private static final Set<String> OFF = new HashSet<>(Arrays.asList("PST", "CANC", "ABD", "SUSP", "INT", "Postponed"));
    private static final Map<String, String> CRESTS = new HashMap<>();

    static {
        String[][] c = {
                {"Arsenal", "arsenal"}, {"Aston Villa", "aston_villa"}, {"Bournemouth", "bournemouth"},
                {"Brentford", "brentford"}, {"Brighton & Hove Albion", "brighton"}, {"Brighton and Hove Albion", "brighton"}, {"Chelsea", "chelsea"},
                {"Coventry City", "coventry"}, {"Crystal Palace", "crystal_palace"}, {"Everton", "everton"},
                {"Fulham", "fulham"}, {"Hull City", "hull"}, {"Ipswich Town", "ipswich"}, {"Leeds United", "leeds"},
                {"Liverpool", "liverpool"}, {"Manchester City", "man_city"}, {"Manchester United", "man_utd"},
                {"Newcastle United", "newcastle"}, {"Nottingham Forest", "nottm_forest"}, {"Sunderland", "sunderland"},
                {"Tottenham Hotspur", "tottenham"},
        };
        for (String[] p : c) CRESTS.put(p[0], p[1]);
    }

    private static final class Match {
        String home, away, homeId, awayId, round, status;
        ZonedDateTime kickoff;
        int[] score;

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

    private static WeakReference<LinearLayout> panelRef = new WeakReference<>(null);
    @SuppressWarnings("unchecked")
    private static final List<Match>[] rounds = new List[IDS.length];
    private static final long[] fetchedAt = new long[IDS.length];
    private static volatile boolean fetching;
    private static int showing;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final Runnable NEXT = Fixtures::next;

    private Fixtures() {
    }

    /** Adds the panel to Home, just above the race panel's layer so the dock and the glass draw over it. */
    static void attach(Activity a, RecyclerView grid) {
        FrameLayout root = a.findViewById(R.id.root);
        View race = a.findViewById(R.id.racePanel);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusable(false);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        int pad = dp(a, 12);
        panel.setPadding(pad, dp(a, 9), pad, dp(a, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x8C000000);
        bg.setCornerRadius(dp(a, 18));
        bg.setStroke(dp(a, 1), 0x33FFFFFF);
        panel.setBackground(bg);
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(a, 30);
        int at = race != null ? root.indexOfChild(race) + 1 : 1;
        root.addView(panel, at, lp);
        panelRef = new WeakReference<>(panel);

        load(a.getApplicationContext());
        render();
        H.removeCallbacks(NEXT);
        H.postDelayed(NEXT, CYCLE);
    }

    /** The next league that has a round to show, cross-faded in. */
    private static void next() {
        H.postDelayed(NEXT, CYCLE);
        LinearLayout p = panelRef.get();
        if (p == null) return;
        int to = showing;
        for (int i = 1; i <= IDS.length; i++) {
            int k = (showing + i) % IDS.length;
            if (rounds[k] != null && !rounds[k].isEmpty()) {
                to = k;
                break;
            }
        }
        if (to == showing) return;
        final int target = to;
        android.animation.ValueAnimator out = android.animation.ValueAnimator.ofFloat(1f, 0f).setDuration(220);
        out.addUpdateListener(va -> p.setTransitionAlpha((float) va.getAnimatedValue()));
        out.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                showing = target;
                render();
                android.animation.ValueAnimator in = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(320);
                in.addUpdateListener(va -> p.setTransitionAlpha((float) va.getAnimatedValue()));
                in.start();
            }
        });
        out.start();
    }

    /** The strip shows while the apps are hidden (CleanHome) and steps aside when they come up. */
    static void show(boolean on, boolean animate) {
        LinearLayout p = panelRef.get();
        if (p == null) return;
        p.animate().cancel();
        float a = on ? 1f : 0f, y = on ? 0f : dp(p.getContext(), 24);
        if (animate) {
            p.animate().alpha(a).translationY(y).setDuration(on ? 260 : 180).start();
        } else {
            p.setAlpha(a);
            p.setTranslationY(y);
        }
    }

    /** Once a minute with the clock: refresh each league (every 30 minutes, 10 while one of its games is on), redraw. */
    static void tick() {
        LinearLayout p = panelRef.get();
        if (p == null) return;
        load(p.getContext().getApplicationContext());
        render();
    }

    private static boolean due(int k) {
        List<Match> r = rounds[k];
        boolean live = false;
        if (r != null) {
            ZonedDateTime now = ZonedDateTime.now(UTC);
            for (Match m : r) live |= m.live(now);
        }
        return System.currentTimeMillis() - fetchedAt[k] > (live ? REFRESH_LIVE : REFRESH);
    }

    /** Fetches every league that is due, one after the other (the free key refuses bursts); caches each. */
    private static void load(Context c) {
        if (fetching) return;
        boolean any = false;
        for (int k = 0; k < IDS.length; k++) any |= due(k) || rounds[k] == null;
        if (!any) return;
        fetching = true;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                for (int k = 0; k < IDS.length; k++) {
                    File cache = new File(c.getFilesDir(), "round-" + IDS[k] + ".json");
                    if (rounds[k] == null && cache.exists()) {
                        // Draw the cached round at once, then fetch.
                        try {
                            rounds[k] = parse(read(new FileInputStream(cache)));
                            main.post(Fixtures::render);
                        } catch (Exception ignored) {
                        }
                    }
                }
                for (int k = 0; k < IDS.length; k++) {
                    if (!due(k)) continue;
                    try {
                        String json = currentRound(IDS[k]);
                        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), "round-" + IDS[k] + ".json"))) {
                            out.write(json.getBytes(StandardCharsets.UTF_8));
                        }
                        rounds[k] = parse(json);
                        fetchedAt[k] = System.currentTimeMillis();
                    } catch (Exception e) {
                        // Offline or rate-limited: keep what is on screen and try again in a few minutes.
                        fetchedAt[k] = System.currentTimeMillis() - REFRESH + 5 * 60_000L;
                    }
                    main.post(Fixtures::render);
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ignored) {
            } finally {
                fetching = false;
                main.post(Fixtures::render);
            }
        }, "fixtures").start();
    }

    /**
     * The round being played: the later of the latest finished game's round and the next game's round (so a
     * rearranged game from an old round never takes over), or the one after it once every game in it is over.
     */
    private static String currentRound(String league) throws Exception {
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

    private static List<Match> parse(String json) throws Exception {
        JSONArray all = new JSONObject(json).optJSONArray("events");
        List<Match> ms = new ArrayList<>();
        if (all == null) return ms;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.getJSONObject(i);
            Match m = new Match();
            m.home = o.optString("strHomeTeam");
            m.away = o.optString("strAwayTeam");
            m.homeId = o.optString("idHomeTeam");
            m.awayId = o.optString("idAwayTeam");
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
        ms.sort((x, y) -> x.kickoff.compareTo(y.kickoff));
        return ms;
    }

    private static void render() {
        LinearLayout panel = panelRef.get();
        if (panel == null) return;
        List<Match> r = rounds[showing];
        if (r == null || r.isEmpty()) {
            for (int k = 0; k < IDS.length; k++) {
                if (rounds[k] != null && !rounds[k].isEmpty()) {
                    showing = k;
                    r = rounds[k];
                    break;
                }
            }
        }
        if (r == null || r.isEmpty()) {
            panel.setVisibility(View.GONE);
            return;
        }
        Context c = panel.getContext();
        panel.removeAllViews();
        ZoneId here = ZoneId.systemDefault();
        boolean oneDay = true;
        LocalDate first = r.get(0).kickoff.withZoneSameInstant(here).toLocalDate();
        for (Match m : r) oneDay &= m.kickoff.withZoneSameInstant(here).toLocalDate().equals(first);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 6));
        View bar = new View(c);
        bar.setBackgroundColor(BARS[showing]);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(c, 3), dp(c, 13));
        bp.rightMargin = dp(c, 8);
        head.addView(bar, bp);
        head.addView(text(c, NAMES[showing], 12, Color.WHITE, true));
        TextView md = text(c, "   ·   MATCHDAY " + r.get(0).round, 10, 0xB3FFFFFF, false);
        md.setLetterSpacing(0.08f);
        head.addView(md);
        LocalDate last = r.get(r.size() - 1).kickoff.withZoneSameInstant(here).toLocalDate();
        String when = oneDay ? DAY.format(first) : DAY_SHORT.format(first) + " – " + DAY.format(last);
        TextView sub = text(c, "   ·   " + when.toUpperCase(Locale.US), 10, 0xB3FFFFFF, false);
        sub.setLetterSpacing(0.08f);
        head.addView(sub);
        panel.addView(head);

        LinearLayout row = null;
        for (int i = 0; i < r.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                panel.addView(row);
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));
            row.addView(cell(c, r.get(i), here, oneDay), cp);
        }
        panel.setVisibility(View.VISIBLE);
    }

    private static View cell(Context c, Match m, ZoneId here, boolean oneDay) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        // Each game in its own pill, so a crest never reads as paired with its neighbour's.
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x24FFFFFF);
        pill.setCornerRadius(dp(c, 14));
        cell.setBackground(pill);
        cell.addView(crest(c, m.home, m.homeId));

        ZonedDateTime k = m.kickoff.withZoneSameInstant(here);
        ZonedDateTime now = ZonedDateTime.now(here);
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        if (!oneDay) mid.addView(centred(text(c, WEEKDAY.format(k).toUpperCase(Locale.US), 8, 0x99FFFFFF, false)));
        TextView main;
        ZonedDateTime nowUtc = now.withZoneSameInstant(UTC);
        if (m.off()) {
            main = text(c, "PP", 10, 0x99FFFFFF, true);
        } else if (m.score != null && m.finished(nowUtc)) {
            main = text(c, m.score[0] + " – " + m.score[1], 12, Color.WHITE, true);
        } else if (m.live(nowUtc)) {
            main = text(c, m.score != null ? m.score[0] + " – " + m.score[1] : "LIVE", m.score != null ? 12 : 10, 0xFFFF5A5A, true);
        } else if (m.finished(nowUtc)) {
            main = text(c, "FT", 10, 0x99FFFFFF, true);
        } else {
            main = text(c, TIME.format(k), 11, 0xFFFFD369, true);
        }
        mid.addView(centred(main));
        cell.addView(mid, new LinearLayout.LayoutParams(dp(c, MID), ViewGroup.LayoutParams.WRAP_CONTENT));

        cell.addView(crest(c, m.away, m.awayId));
        return cell;
    }

    /** The crest on a small white disc (dark crests stay readable); three letters when there is no crest. */
    private static View crest(Context c, String club, String teamId) {
        int size = dp(c, CREST);
        FrameLayout disc = new FrameLayout(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xF2FFFFFF);
        disc.setBackground(d);
        String name = club.replaceAll("^AFC ", "").replaceAll(" (A?FC|AFC)$", "").trim();
        String slug = CRESTS.get(name);
        int id = teamId == null || teamId.isEmpty() ? 0
                : c.getResources().getIdentifier("crest_t" + teamId, "drawable", c.getPackageName());
        if (id == 0 && slug != null) id = c.getResources().getIdentifier("crest_" + slug, "drawable", c.getPackageName());
        if (id != 0) {
            ImageView iv = new ImageView(c);
            iv.setImageResource(id);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int in = dp(c, 2);
            iv.setPadding(in, in, in, in);
            disc.addView(iv, new FrameLayout.LayoutParams(size, size));
        } else {
            TextView t = text(c, name.substring(0, Math.min(3, name.length())).toUpperCase(Locale.US), 9, 0xFF111111, true);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(0, 0, 0, 0);
            disc.addView(t, new FrameLayout.LayoutParams(size, size));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        return wrap(disc, lp);
    }

    private static View wrap(View v, LinearLayout.LayoutParams lp) {
        v.setLayoutParams(lp);
        return v;
    }

    private static TextView centred(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private static TextView text(Context c, String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(colour);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setShadowLayer(4, 0, 1, 0x99000000);
        return v;
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
                return read(c.getInputStream());
            } finally {
                c.disconnect();
            }
        }
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
