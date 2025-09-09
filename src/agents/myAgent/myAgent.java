package agents.myAgent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;

import java.util.*;

public class myAgent implements MarioAgent {

    private static final int TICK_WALK = 10;
    private static final int TICK_JUMP = 18;
    private static final int TICK_WAIT = 12;
    private static final float GOAL_X_MARGIN = 16f;
    private static final float QUANT_POS = 2f;
    private static final float QUANT_VEL = 0.25f;
    private static final int MAX_EXPANSIONS = 25000;
    private static final float DROP_THRESH = 1.5f;

    private final ArrayDeque<boolean[]> actionQueue = new ArrayDeque<>();

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        actionQueue.clear();
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        if (actionQueue.isEmpty()) plan(model);
        return actionQueue.isEmpty() ? idle() : actionQueue.pollFirst();
    }

    @Override
    public String getAgentName() { return "SolverAgent-Safe"; }

    private void plan(MarioForwardModel root) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.h));
        HashSet<Long> closed = new HashSet<>();

        State s0 = State.fromModel(root);
        Node n0 = new Node(s0, null, null, heuristic(s0));
        open.add(n0);

        int expansions = 0;
        while (!open.isEmpty() && expansions < MAX_EXPANSIONS) {
            Node cur = open.poll();

            if (isGoal(cur.s, root)) {
                replay(cur);
                return;
            }

            long key = cur.s.key();
            if (!closed.add(key)) continue;

            for (Macro m : MACROS) {
                MarioForwardModel fm = root.clone();
                Deque<Macro> hist = new ArrayDeque<>();
                Node p = cur;
                while (p.parent != null) { hist.addFirst(p.used); p = p.parent; }
                for (Macro mm : hist) if (!simulateMacro(fm, mm)) { fm = null; break; }
                if (fm == null) continue;

                if (!simulateMacro(fm, m)) continue;

                State sn = State.fromModel(fm);
                Node nn = new Node(sn, cur, m, heuristic(sn));
                if (!closed.contains(sn.key())) open.add(nn);
                expansions++;
            }
        }

        for (int i = 0; i < 8; i++) actionQueue.addLast(actIdle());
    }

    private boolean simulateMacro(MarioForwardModel fm, Macro m) {
        switch (m.type) {
            case WAIT: {
                for (int t = 0; t < TICK_WAIT; t++) {
                    if (!advanceSafe(fm, actIdle(), true)) return false;
                }
                return true;
            }
            case WALK_RIGHT: {
                float lastY = fm.getMarioFloatPos()[1];
                for (int t = 0; t < TICK_WALK; t++) {
                    if (!advanceSafe(fm, actRightWalk(), false)) return false;
                    float y = fm.getMarioFloatPos()[1];
                    if (y - lastY > DROP_THRESH) return false;
                    lastY = y;
                }
                if (!fm.isMarioOnGround()) return false;
                return true;
            }
            case RUN_JUMP_RIGHT: {
                float lastY = fm.getMarioFloatPos()[1];
                for (int t = 0; t < TICK_JUMP; t++) {
                    if (!advanceSafe(fm, actRightRunJump(), false)) return false;
                    float y = fm.getMarioFloatPos()[1];
                    if (t < 3 && y - lastY > DROP_THRESH) return false;
                    lastY = y;
                }
                for (int t = 0; t < 6; t++) {
                    if (!advanceSafe(fm, actRightWalk(), false)) return false;
                }
                return true;
            }
            case STEP_BACK: {
                float lastY = fm.getMarioFloatPos()[1];
                for (int t = 0; t < TICK_WALK; t++) {
                    if (!advanceSafe(fm, actLeft(), false)) return false;
                    float y = fm.getMarioFloatPos()[1];
                    if (y - lastY > DROP_THRESH) return false;
                    lastY = y;
                }
                if (!fm.isMarioOnGround()) return false;
                return true;
            }
        }
        return false;
    }

    private boolean advanceSafe(MarioForwardModel fm, boolean[] action, boolean allowFall) {
        fm.advance(action);
        float[] pos = fm.getMarioFloatPos();
        if (!fm.isMarioAlive()) return false;
        if (!allowFall && pos[1] > 3000f) return false;
        return true;
    }

    private boolean isGoal(State s, MarioForwardModel root) {
        float goalX = root.getLevelFloatDimensions()[0] - GOAL_X_MARGIN;
        return s.x >= goalX;
    }

    private void replay(Node goal) {
        ArrayDeque<Macro> seq = new ArrayDeque<>();
        Node p = goal;
        while (p.parent != null) { seq.addFirst(p.used); p = p.parent; }
        for (Macro m : seq) {
            switch (m.type) {
                case WAIT:
                    for (int t = 0; t < TICK_WAIT; t++) actionQueue.addLast(actIdle());
                    break;
                case WALK_RIGHT:
                    for (int t = 0; t < TICK_WALK; t++) actionQueue.addLast(actRightWalk());
                    break;
                case RUN_JUMP_RIGHT:
                    for (int t = 0; t < TICK_JUMP; t++) actionQueue.addLast(actRightRunJump());
                    for (int t = 0; t < 6; t++) actionQueue.addLast(actRightWalk());
                    break;
                case STEP_BACK:
                    for (int t = 0; t < TICK_WALK; t++) actionQueue.addLast(actLeft());
                    break;
            }
        }
        if (actionQueue.isEmpty()) for (int i = 0; i < 12; i++) actionQueue.addLast(actRightWalk());
    }

    private double heuristic(State s) {
        return -s.x + 0.001 * s.y;
    }

    private static boolean[] idle() { return new boolean[]{false,false,false,false,false,false}; }
    private static boolean[] actIdle() { return idle(); }
    private static boolean[] actRightWalk() { return new boolean[]{false,true,false,false,false,false}; }
    private static boolean[] actRightRunJump() { return new boolean[]{false,true,false,true,true,false}; }
    private static boolean[] actLeft() { return new boolean[]{true,false,false,false,false,false}; }

    private enum MacroType { WAIT, WALK_RIGHT, RUN_JUMP_RIGHT, STEP_BACK }
    private static class Macro { final MacroType type; Macro(MacroType t){ this.type=t; } }
    private static final Macro[] MACROS = new Macro[] {
            new Macro(MacroType.WAIT),
            new Macro(MacroType.RUN_JUMP_RIGHT),
            new Macro(MacroType.WALK_RIGHT),
            new Macro(MacroType.STEP_BACK),
    };

    private static class Node {
        final State s; final Node parent; final Macro used; final double h;
        Node(State s, Node p, Macro u, double h){ this.s=s; this.parent=p; this.used=u; this.h=h; }
    }

    private static class State {
        final float x, y, vx, vy; final boolean onGround;
        private State(float x,float y,float vx,float vy,boolean g){ this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.onGround=g; }
        static State fromModel(MarioForwardModel m){
            float[] pos = m.getMarioFloatPos();
            float[] vel = m.getMarioVelocity();
            boolean on = m.isMarioOnGround();
            float qx = quant(pos[0], QUANT_POS);
            float qy = quant(pos[1], QUANT_POS);
            float qvx = quant(vel[0], QUANT_VEL);
            float qvy = quant(vel[1], QUANT_VEL);
            return new State(qx,qy,qvx,qvy,on);
        }
        static float quant(float v,float q){ return (float)Math.floor(v/q)*q; }
        long key(){
            long kx = Float.floatToIntBits(x);
            long ky = Float.floatToIntBits(y);
            long kvx= Float.floatToIntBits(vx);
            long kvy= Float.floatToIntBits(vy);
            long on = onGround ? 1L : 0L;
            long h1 = kx ^ (ky << 1);
            long h2 = kvx ^ (kvy << 1);
            return (h1 * 1315423911L) ^ (h2 * 2654435761L) ^ on;
        }
    }
}
