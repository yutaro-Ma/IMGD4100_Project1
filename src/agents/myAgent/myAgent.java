package agents.myAgent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class myAgent implements MarioAgent {
    // RHEA basics
    private static final int HORIZON_TICKS = 18;
    private static final int ROLLOUTS = 160;
    private static final double WIN_BONUS = 6500;
    private static final double DEATH_PEN = 12000;
    private static final double TIME_COST = 0.18;
    private static final double JUMP_COST = 0.05;
    private static final double STALL_PEN = 2.5;

    private static final double ENEMY_DX_HITBOX = 18.0;
    private static final double ENEMY_DY_HITBOX = 22.0;
    private static final double ENEMY_ABOVE_EXTRA_PEN = 200.0;

    private static final double MUT_JUMP_RATE = 0.12;
    private static final double MUT_LEFT_RATE = 0.06;

    private static final double INIT_JUMP_PROB = 0.24;
    private static final double INIT_LEFT_PROB = 0.06;

    private static final int STUCK_WINDOW = 16;
    private static final double STUCK_EPS = 0.2;
    private static final int FORCED_LONG_JUMP = 10;
    private static final int FORCED_BACK_RUN = 8;

    private static final int GOAL_CHECK_HORIZON = 80;
    private static final int FINISH_JUMP_EARLY_HOLD = 6;
    private static final int FINISH_JUMP_LATE_START = 4;
    private static final int FINISH_JUMP_LATE_HOLD = 6;
    private static final int FINISH_JUMP_2PH_A_START = 2;
    private static final int FINISH_JUMP_2PH_A_HOLD = 4;
    private static final int FINISH_JUMP_2PH_B_START = 10;
    private static final int FINISH_JUMP_2PH_B_HOLD = 4;

    private static final int OSC_WINDOW = 20;
    private static final int OSC_TOGGLE_THRESH = 8;
    private static final double OSC_PROGRESS_EPS = 1.2;
    private static final int FINISH_LOCK_FRAMES = 30;

    private int na;
    private final Random rnd = new Random(20250928);

    private boolean[][] jPlan;
    private boolean[][] lPlan;

    private double lastX = 0.0;
    private int stuck = 0;
    private int forceJ = 0;
    private int forceB = 0;

    private final Deque<Boolean> lHist = new ArrayDeque<>();
    private final Deque<Double> xHist = new ArrayDeque<>();
    private int lock = 0;

    @Override
    public void initialize(MarioForwardModel m, MarioTimer t) {
        na = MarioActions.numberOfActions();
        jPlan = new boolean[HORIZON_TICKS][1];
        lPlan = new boolean[HORIZON_TICKS][1];
        for (int i = 0; i < HORIZON_TICKS; i++) {
            jPlan[i][0] = rnd.nextDouble() < INIT_JUMP_PROB;
            lPlan[i][0] = rnd.nextDouble() < INIT_LEFT_PROB;
        }
        lastX = getMarioX(m);
        stuck = 0;
        forceJ = 0;
        forceB = 0;
        lHist.clear();
        xHist.clear();
        lock = 0;
    }

    @Override
    public boolean[] getActions(MarioForwardModel m, MarioTimer t) {
        boolean[] fin = tryFinishNow(m);
        if (fin != null) {
            lock = Math.max(lock, FINISH_LOCK_FRAMES);
            fin[MarioActions.LEFT.getValue()] = false;
            fin[MarioActions.RIGHT.getValue()] = true;
            fin[MarioActions.SPEED.getValue()] = true;
            forceB = 0;
            forceJ = 0;
            return fin;
        }

        double x = getMarioX(m);
        if (x - lastX < STUCK_EPS) stuck++; else stuck = 0;
        lastX = x;

        double best = -1e30;
        boolean[][] bj = jPlan, bl = lPlan;
        for (int i = 0; i < ROLLOUTS; i++) {
            boolean[][] cj = mutate1D(jPlan, MUT_JUMP_RATE);
            boolean[][] cl = mutate1D(lPlan, MUT_LEFT_RATE);
            double s = evaluatePlan(m, cj, cl);
            if (s > best) { best = s; bj = cj; bl = cl; }
        }

        boolean[] act = new boolean[na];
        act[MarioActions.RIGHT.getValue()] = true;
        act[MarioActions.SPEED.getValue()] = true;

        boolean wantL = bl[0][0];
        pushHistory(wantL, x);
        if (shouldFinishLock()) lock = Math.max(lock, FINISH_LOCK_FRAMES);

        if (lock > 0) {
            wantL = false;
            forceB = 0;
            forceJ = 0;
            lock--;
        }

        if (wantL) {
            act[MarioActions.LEFT.getValue()] = true;
            act[MarioActions.RIGHT.getValue()] = false;
        }
        act[MarioActions.JUMP.getValue()] = bj[0][0];

        if (lock == 0) {
            if (stuck >= STUCK_WINDOW) {
                if (forceJ == 0 && forceB == 0) {
                    if (isEnemyVeryClose(m)) forceB = FORCED_BACK_RUN;
                    else forceJ = FORCED_LONG_JUMP;
                }
                stuck = 0;
            }
            if (forceJ > 0) { act[MarioActions.JUMP.getValue()] = true; forceJ--; }
            if (forceB > 0) {
                act[MarioActions.LEFT.getValue()] = true;
                act[MarioActions.RIGHT.getValue()] = false;
                forceB--;
            }
            if (isOnGround(m) && enemyHitSoon(m)) {
                act[MarioActions.LEFT.getValue()] = true;
                act[MarioActions.RIGHT.getValue()] = false;
                forceB = Math.max(forceB, 2);
            }
        } else {
            if (x - meanRecentX() < STUCK_EPS * 0.5) {
                act[MarioActions.JUMP.getValue()] = false;
            }
        }

        jPlan = shiftAndRefill1D(bj, INIT_JUMP_PROB);
        lPlan = shiftAndRefill1D(bl, INIT_LEFT_PROB);
        return act;
    }

    @Override
    public String getAgentName() { return "RHEA_SafeFast_FINISH_LOCK"; }

    private boolean[] tryFinishNow(MarioForwardModel m) {
        boolean[] a = macroFinish(m, new FP(0, 0, true));
        if (a != null) return a;
        a = macroFinish(m, new FP(0, FINISH_JUMP_EARLY_HOLD, true));
        if (a != null) return a;
        a = macroFinish(m, new FP(FINISH_JUMP_LATE_START, FINISH_JUMP_LATE_HOLD, true));
        if (a != null) return a;
        a = macroFinish(m, new FP(FINISH_JUMP_2PH_A_START, FINISH_JUMP_2PH_A_HOLD, true),
                           new FP(FINISH_JUMP_2PH_B_START, FINISH_JUMP_2PH_B_HOLD, true));
        if (a != null) return a;
        a = macroFinish(m, new FP(0, 0, false), new FP(3, 0, true));
        if (a != null) return a;
        return macroFinish(m, new FP(0, 0, false), new FP(3, 0, true), new FP(6, 3, true));
    }

    private static class FP {
        final int s, h; final boolean r;
        FP(int s, int h, boolean r){ this.s = s; this.h = h; this.r = r; }
    }

    private boolean[] macroFinish(MarioForwardModel m, FP... ps) {
        Object sim = safeCopyModel(m);
        boolean[] first = null;
        boolean[] jMask = new boolean[GOAL_CHECK_HORIZON];
        boolean[] rMask = new boolean[GOAL_CHECK_HORIZON];
        Arrays.fill(rMask, true);
        for (FP p : ps) {
            if (!p.r) for (int t = 0; t < Math.min(3, GOAL_CHECK_HORIZON); t++) rMask[t] = false;
            for (int t = p.s; t < Math.min(GOAL_CHECK_HORIZON, p.s + p.h); t++) jMask[t] = true;
        }
        for (int t = 0; t < GOAL_CHECK_HORIZON; t++) {
            boolean[] a = new boolean[na];
            boolean r = rMask[t];
            a[MarioActions.RIGHT.getValue()] = r;
            a[MarioActions.SPEED.getValue()] = r;
            a[MarioActions.JUMP.getValue()] = jMask[t];
            if (t == 0) first = a.clone();
            safeAdvance(sim, a);
            if (isWin(sim)) return first;
            if (isDead(sim)) return null;
        }
        return null;
    }

    private double evaluatePlan(MarioForwardModel m, boolean[][] jp, boolean[][] lp) {
        Object sim = safeCopyModel(m);
        double sx = getMarioX(sim), sc = 0.0, px = sx;
        boolean latch = false; int stall = 0;
        for (int t = 0; t < HORIZON_TICKS; t++) {
            boolean[] a = new boolean[na];
            a[MarioActions.RIGHT.getValue()] = true;
            a[MarioActions.SPEED.getValue()] = true;
            if (lp[t][0]) { a[MarioActions.LEFT.getValue()] = true; a[MarioActions.RIGHT.getValue()] = false; }
            if (jp[t][0]) latch = true;
            if (latch) {
                if (canJump(sim)) { a[MarioActions.JUMP.getValue()] = true; latch = false; }
                else a[MarioActions.JUMP.getValue()] = true;
            } else a[MarioActions.JUMP.getValue()] = jp[t][0];

            safeAdvance(sim, a);
            if (isWin(sim)) {
                double x = getMarioX(sim);
                sc += (x - sx) + WIN_BONUS - TIME_COST * (t + 1);
                return sc;
            }
            if (isDead(sim)) { sc -= DEATH_PEN; return sc; }

            double x = getMarioX(sim);
            sc += (x - sx) / HORIZON_TICKS;
            if (a[MarioActions.JUMP.getValue()]) sc -= JUMP_COST;
            sc -= TIME_COST / HORIZON_TICKS;
            if (x - px < STUCK_EPS * 0.5) { stall++; sc -= STALL_PEN * (stall * 0.12); } else { stall = 0; }
            px = x;

            double[] mp = getMarioXY(sim);
            List<double[]> es = getEnemies(sim);
            for (double[] e : es) {
                double dx = e[0] - mp[0], dy = e[1] - mp[1];
                if (Math.abs(dx) <= ENEMY_DX_HITBOX && Math.abs(dy) <= ENEMY_DY_HITBOX) {
                    sc -= 60.0;
                    if (dy < 6.0 && dy > -40.0) sc -= ENEMY_ABOVE_EXTRA_PEN;
                    if (!lp[t][0] && a[MarioActions.JUMP.getValue()]) sc -= 80.0;
                }
            }
        }
        sc += (getMarioX(sim) - sx);
        return sc;
    }

    private void pushHistory(boolean l, double x) {
        lHist.addLast(l);
        xHist.addLast(x);
        while (lHist.size() > OSC_WINDOW) lHist.removeFirst();
        while (xHist.size() > OSC_WINDOW) xHist.removeFirst();
    }

    private boolean shouldFinishLock() {
        if (lHist.size() < OSC_WINDOW || xHist.size() < OSC_WINDOW) return false;
        int tog = 0; Boolean p = null;
        for (Boolean b : lHist) { if (p != null && b != p) tog++; p = b; }
        double prog = xHist.peekLast() - xHist.peekFirst();
        return (tog >= OSC_TOGGLE_THRESH && prog < OSC_PROGRESS_EPS);
    }

    private double meanRecentX() {
        if (xHist.isEmpty()) return 0.0;
        double s = 0.0; for (double v : xHist) s += v; return s / xHist.size();
    }

    private boolean[][] mutate1D(boolean[][] src, double rate) {
        boolean[][] out = new boolean[src.length][1];
        for (int i = 0; i < src.length; i++) {
            boolean v = src[i][0];
            if (rnd.nextDouble() < rate) v = !v;
            out[i][0] = v;
        }
        return out;
    }

    private boolean[][] shiftAndRefill1D(boolean[][] p, double pInit) {
        boolean[][] out = new boolean[p.length][1];
        for (int i = 0; i < p.length - 1; i++) out[i][0] = p[i + 1][0];
        out[p.length - 1][0] = rnd.nextDouble() < pInit;
        return out;
    }

    private boolean isEnemyVeryClose(Object m) {
        double[] mp = getMarioXY(m);
        for (double[] e : getEnemies(m)) {
            if (Math.abs(e[0] - mp[0]) <= ENEMY_DX_HITBOX && Math.abs(e[1] - mp[1]) <= ENEMY_DY_HITBOX) return true;
        }
        return false;
    }

    private boolean enemyHitSoon(Object m) {
        double[] mp = getMarioXY(m);
        for (double[] e : getEnemies(m)) {
            double dx = e[0] - mp[0], dy = e[1] - mp[1];
            if (dx > -8 && dx < ENEMY_DX_HITBOX && Math.abs(dy) <= ENEMY_DY_HITBOX) return true;
        }
        return false;
    }

    private boolean isOnGround(Object m) {
        try {
            Object v = m.getClass().getMethod("isMarioOnGround").invoke(m);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {}
        return false;
    }

    private Object safeCopyModel(Object m) {
        try { return m.getClass().getMethod("copy").invoke(m); } catch (Throwable ignored) {}
        try { return m.getClass().getMethod("clone").invoke(m); } catch (Throwable ignored) {}
        try { return m.getClass().getConstructor(m.getClass()).newInstance(m); } catch (Throwable ignored) {}
        return m;
    }

    private void safeAdvance(Object m, boolean[] a) {
        try { m.getClass().getMethod("advance", boolean[].class).invoke(m, (Object) a); } catch (Throwable ignored) {}
    }

    private double getMarioX(Object m) {
        try {
            Object res = m.getClass().getMethod("getMarioFloatPos").invoke(m);
            if (res instanceof float[]) return ((float[]) res)[0];
        } catch (Throwable ignored) {}
        try {
            Object v = m.getClass().getMethod("getMarioX").invoke(m);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Throwable ignored) {}
        try {
            Object v = m.getClass().getMethod("getGameScore").invoke(m);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private double[] getMarioXY(Object m) {
        try {
            Object res = m.getClass().getMethod("getMarioFloatPos").invoke(m);
            if (res instanceof float[]) {
                float[] p = (float[]) res;
                return new double[] { p[0], p[1] };
            }
        } catch (Throwable ignored) {}
        return new double[] { getMarioX(m), 0.0 };
    }

    private List<double[]> getEnemies(Object m) {
        List<double[]> out = new ArrayList<>();
        try {
            Object res = m.getClass().getMethod("getEnemiesFloatPos").invoke(m);
            if (res instanceof float[][]) {
                float[][] arr = (float[][]) res;
                for (float[] e : arr) { if (e != null && e.length >= 2) out.add(new double[] { e[0], e[1] }); }
                return out;
            }
            if (res instanceof float[]) {
                float[] arr = (float[]) res;
                if (arr.length % 3 == 0) { for (int i = 0; i < arr.length; i += 3) out.add(new double[] { arr[i], arr[i+1] }); return out; }
                if (arr.length % 2 == 0) { for (int i = 0; i < arr.length; i += 2) out.add(new double[] { arr[i], arr[i+1] }); return out; }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private boolean canJump(Object m) {
        try {
            Object v = m.getClass().getMethod("isMarioOnGround").invoke(m);
            if (v instanceof Boolean && (Boolean) v) return true;
        } catch (Throwable ignored) {}
        try {
            Object v = m.getClass().getMethod("mayMarioJump").invoke(m);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {}
        try {
            Object v = m.getClass().getMethod("isMarioAbleToJump").invoke(m);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isDead(Object m) {
        try {
            Object v = m.getClass().getMethod("isMarioDead").invoke(m);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {}
        Integer s = getGameStatus(m);
        return s != null && (s == 2 || s == -1);
    }

    private boolean isWin(Object m) {
        try {
            Object v = m.getClass().getMethod("isMarioWin").invoke(m);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {}
        Integer s = getGameStatus(m);
        return s != null && s == 1;
    }

    private Integer getGameStatus(Object m) {
        try {
            Object v = m.getClass().getMethod("getGameStatus").invoke(m);
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Throwable ignored) {}
        return null;
    }
}
