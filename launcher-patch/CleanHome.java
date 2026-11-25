package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;

/**
 * A clean Home: the wallpaper, the clock and the match strip only. The apps (dock, grid, the big app name and the
 * focus highlight) stay hidden until a remote key asks for them: Down, OK or a side arrow shows them (that first key
 * does nothing else, so nothing opens unseen), and Back, Up to the top bar, 20 seconds without a key or coming back
 * to Home hides them again. Hidden with transitionAlpha, which the launcher's own animations never touch.
 */
final class CleanHome {
    private static final long IDLE = 20_000L;

    private static WeakReference<Activity> act = new WeakReference<>(null);
    private static WeakReference<RecyclerView> gridRef = new WeakReference<>(null);
    private static View[] layer = new View[0];
    private static boolean shown = true;
    private static int swallowUp = -1;
    private static float level = 1f;
    private static ValueAnimator anim;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final Runnable IDLE_HIDE = () -> set(false, true);

    private CleanHome() {
    }

    static void attach(Activity a, RecyclerView grid) {
        act = new WeakReference<>(a);
        gridRef = new WeakReference<>(grid);
        layer = new View[]{grid, a.findViewById(R.id.hero), a.findViewById(R.id.heroScrim), a.findViewById(R.id.glass)};
        a.findViewById(R.id.root).getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (shown && newFocus != null && !inGrid(newFocus)) set(false, true);
        });
        set(false, false);
    }

    /** Back on Home: start clean. */
    static void rest() {
        set(false, false);
    }

    /** From MainActivity.dispatchKeyEvent: true when the key was used here and must go no further. */
    static boolean key(KeyEvent e) {
        Activity a = act.get();
        RecyclerView grid = gridRef.get();
        if (a == null || grid == null) return false;
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP) {
            if (code == swallowUp) {
                swallowUp = -1;
                return true;
            }
            return false;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        boolean nav = code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_UP;
        if (shown) {
            H.removeCallbacks(IDLE_HIDE);
            H.postDelayed(IDLE_HIDE, IDLE);
            if (code == KeyEvent.KEYCODE_BACK && e.getRepeatCount() == 0) {
                set(false, true);
                swallowUp = code;
                return true;
            }
            return false;
        }
        if (!nav) return false;
        View focus = a.getCurrentFocus();
        boolean inApps = focus == null || inGrid(focus);
        if (!inApps) {
            // On the top bar: Down walks into the apps as usual, and they appear on the way.
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) set(true, true);
            return false;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) return false; // up to the top bar, still clean
        set(true, true);
        swallowUp = code;
        return true;
    }

    private static boolean inGrid(View v) {
        RecyclerView grid = gridRef.get();
        for (ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == grid) return true;
        return v == grid;
    }

    private static void set(boolean show, boolean animate) {
        H.removeCallbacks(IDLE_HIDE);
        if (show) H.postDelayed(IDLE_HIDE, IDLE);
        shown = show;
        Fixtures.show(!show, animate);
        if (anim != null) anim.cancel();
        float to = show ? 1f : 0f;
        if (!animate) {
            apply(to);
            return;
        }
        anim = ValueAnimator.ofFloat(level, to);
        anim.setDuration(show ? 260 : 220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(va -> apply((float) va.getAnimatedValue()));
        anim.start();
    }

    private static void apply(float v) {
        level = v;
        for (View x : layer) {
            if (x == null) continue;
            x.setTransitionAlpha(v);
        }
        RecyclerView grid = gridRef.get();
        if (grid != null) grid.setTranslationY((1f - v) * grid.getResources().getDisplayMetrics().density * 40);
    }
}
