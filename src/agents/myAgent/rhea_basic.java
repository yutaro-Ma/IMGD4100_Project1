// package agents.myAgent;

// public class new {
    
// }
// package agents.myAgent;

// import engine.core.MarioAgent;
// import engine.core.MarioForwardModel;
// import engine.core.MarioTimer;
// import engine.helper.MarioActions;
// import java.util.Arrays;
// import java.util.Random;

// public class myAgent implements MarioAgent {

//     //  RHEA パラメータ 
//     private static final int    HORIZON_TICKS   = 16;    // 先読み長
//     private static final int    ROLLOUTS        = 140;   // 評価本数
//     private static final double WIN_BONUS       = 6000;  // 勝利加点
//     private static final double DEATH_PEN       = 9000;  // 死亡減点
//     private static final double TIME_COST       = 0.18;  // 時間ペナルティ
//     private static final double JUMP_COST       = 0.04;  // ジャンプ抑制
//     private static final double STALL_PEN       = 2.0;   // 停滞ペナルティの重み

//     private static final double JUMP_ON_PROB    = 0.28;  // 初期列のJUMP密度
//     private static final double MUT_RATE        = 0.12;  // 突然変異率

//     //  “壁ドン”対策（実行側マクロ） 
//     private static final int STUCK_WINDOW       = 14;    // 何フレーム進まなければ停滞とみなすか
//     private static final double STUCK_EPS       = 0.2;   // 進みとみなす最小X差
//     private static final int FORCED_LONG_JUMP   = 10;    // 強制ロングジャンプ継続フレーム

//     private boolean[] action;
//     private int numActions;
//     private final Random rng = new Random(20250928);

//     // RHEAのベースプラン（リシーディング用）
//     private boolean[][] basePlan;

//     // 実行時のスタック検出
//     private double lastXSeen = 0.0;
//     private int    stuckCount = 0;
//     private int    forcedJumpRemain = 0;

//     @Override
//     public void initialize(MarioForwardModel model, MarioTimer timer) {
//         numActions = MarioActions.numberOfActions();
//         action = new boolean[numActions];
//         Arrays.fill(action, false);
//         action[MarioActions.RIGHT.getValue()] = true;
//         action[MarioActions.SPEED.getValue()] = true;

//         basePlan = new boolean[HORIZON_TICKS][numActions];
//         for (int t = 0; t < HORIZON_TICKS; t++) {
//             basePlan[t] = defaultStep(false);
//             if (rng.nextDouble() < JUMP_ON_PROB) {
//                 basePlan[t][MarioActions.JUMP.getValue()] = true;
//             }
//         }
//         // 初期
//         lastXSeen = getMarioX(model);
//         stuckCount = 0;
//         forcedJumpRemain = 0;
//     }

//     @Override
//     public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
//         // “壁ドン”検出
//         double curX = getMarioX(model);
//         if (curX - lastXSeen < STUCK_EPS) stuckCount++;
//         else stuckCount = 0;
//         lastXSeen = curX;

//         double bestScore = -1e30;
//         boolean[][] bestPlan = basePlan;

//         for (int r = 0; r < ROLLOUTS; r++) {
//             boolean[][] cand = mutatePlan(basePlan);
//             double score = evaluatePlanWithStickyJump(model, cand);
//             if (score > bestScore) {
//                 bestScore = score;
//                 bestPlan = cand;
//             }
//         }

//         // 先頭1手
//         boolean[] first = bestPlan[0].clone();
//         first[MarioActions.RIGHT.getValue()] = true;
//         first[MarioActions.SPEED.getValue()] = true;

//         if (stuckCount >= STUCK_WINDOW) {
//             forcedJumpRemain = FORCED_LONG_JUMP;
//             stuckCount = 0; // リセット
//         }
//         if (forcedJumpRemain > 0) {
//             first[MarioActions.JUMP.getValue()] = true;
//             forcedJumpRemain--;
//         }

//         basePlan = shiftAndRefill(bestPlan);

//         return first;
//     }

//     @Override
//     public String getAgentName() {
//         return "RHEA_SpeedySafeAgent_sticky";
//     }

//     // 評価（Sticky Jump & 停滞ペナルティ付き
//     private double evaluatePlanWithStickyJump(MarioForwardModel model, boolean[][] plan) {
//         Object sim = safeCopyModel(model);
//         double startX = getMarioX(sim);
//         double score  = 0.0;

//         boolean jumpLatched = false; 
//         int stallRun = 0;
//         double prevX = startX;

//         for (int t = 0; t < plan.length; t++) {
//             boolean[] a = plan[t].clone();
//             a[MarioActions.RIGHT.getValue()] = true; // 右SPEEDは毎回強制
//             a[MarioActions.SPEED.getValue()] = true;

//             if (plan[t][MarioActions.JUMP.getValue()]) {
//                 jumpLatched = true;
//             }
//             if (jumpLatched) {
//                 // “今”ジャンプ可能？
//                 if (canJump(sim)) {
//                     a[MarioActions.JUMP.getValue()] = true; // このフレームで離陸
//                     jumpLatched = false;                    // ラッチ解除
//                 } else {
//                     a[MarioActions.JUMP.getValue()] = true; // 可能になるまで押し続け
//                 }
//             } else {
//                 // 通常は計画通り
//                 a[MarioActions.JUMP.getValue()] = plan[t][MarioActions.JUMP.getValue()];
//             }
//             // -----------------------------------------------

//             safeAdvance(sim, a);

//             // 勝敗チェック
//             if (isWin(sim)) {
//                 double x = getMarioX(sim);
//                 score += (x - startX);
//                 score += WIN_BONUS;
//                 score -= TIME_COST * (t + 1);
//                 return score;
//             }
//             if (isDead(sim)) {
//                 score -= DEATH_PEN;
//                 return score;
//             }

//             // 基本スコア
//             double x = getMarioX(sim);
//             score += (x - startX) / plan.length;
//             if (a[MarioActions.JUMP.getValue()]) score -= JUMP_COST;
//             score -= TIME_COST / plan.length;

//             // 停滞ペナルティ（距離がほぼ伸びないフレームが続くと重く）
//             if (x - prevX < STUCK_EPS * 0.5) {
//                 stallRun++;
//                 score -= STALL_PEN * (stallRun * 0.1); // だんだん重くする
//             } else {
//                 stallRun = 0;
//             }
//             prevX = x;
//         }
//         double endX = getMarioX(sim);
//         score += (endX - startX);
//         return score;
//     }

//     private boolean[][] mutatePlan(boolean[][] src) {
//         boolean[][] out = new boolean[src.length][numActions];
//         for (int t = 0; t < src.length; t++) {
//             out[t] = src[t].clone();
//             out[t][MarioActions.RIGHT.getValue()] = true;
//             out[t][MarioActions.SPEED.getValue()] = true;
//             // JUMP のみ中心に突然変異
//             if (rng.nextDouble() < MUT_RATE) {
//                 int j = MarioActions.JUMP.getValue();
//                 out[t][j] = !out[t][j];
//             }
//         }
//         return out;
//     }

//     private boolean[][] shiftAndRefill(boolean[][] plan) {
//         boolean[][] out = new boolean[plan.length][numActions];
//         for (int t = 0; t < plan.length - 1; t++) {
//             out[t] = plan[t + 1].clone();
//             out[t][MarioActions.RIGHT.getValue()] = true;
//             out[t][MarioActions.SPEED.getValue()] = true;
//         }
//         out[plan.length - 1] = defaultStep(rng.nextDouble() < JUMP_ON_PROB);
//         return out;
//     }

//     private boolean[] defaultStep(boolean jumpOn) {
//         boolean[] a = new boolean[numActions];
//         a[MarioActions.RIGHT.getValue()] = true;
//         a[MarioActions.SPEED.getValue()] = true;
//         a[MarioActions.JUMP.getValue()]  = jumpOn;
//         return a;
//     }

//     private Object safeCopyModel(Object model) {
//         try { return model.getClass().getMethod("copy").invoke(model); } catch (Throwable ignored) {}
//         try { return model.getClass().getMethod("clone").invoke(model); } catch (Throwable ignored) {}
//         try { return model.getClass().getConstructor(model.getClass()).newInstance(model); } catch (Throwable ignored) {}
//         return model;
//     }

//     private void safeAdvance(Object model, boolean[] action) {
//         try { model.getClass().getMethod("advance", boolean[].class).invoke(model, (Object) action); } catch (Throwable ignored) {}
//     }

//     private double getMarioX(Object model) {
//         try {
//             Object res = model.getClass().getMethod("getMarioFloatPos").invoke(model);
//             if (res instanceof float[]) return ((float[]) res)[0];
//         } catch (Throwable ignored) {}
//         try {
//             Object v = model.getClass().getMethod("getMarioX").invoke(model);
//             if (v instanceof Number) return ((Number) v).doubleValue();
//         } catch (Throwable ignored) {}
//         try {
//             Object v = model.getClass().getMethod("getGameScore").invoke(model);
//             if (v instanceof Number) return ((Number) v).doubleValue();
//         } catch (Throwable ignored) {}
//         return 0.0;
//     }

//     private boolean canJump(Object model) {
//         // よくある名前を順に試す
//         try {
//             Object v = model.getClass().getMethod("isMarioOnGround").invoke(model);
//             if (v instanceof Boolean && (Boolean) v) return true;
//         } catch (Throwable ignored) {}
//         try {
//             Object v = model.getClass().getMethod("mayMarioJump").invoke(model);
//             if (v instanceof Boolean) return (Boolean) v;
//         } catch (Throwable ignored) {}
//         try {
//             Object v = model.getClass().getMethod("isMarioAbleToJump").invoke(model);
//             if (v instanceof Boolean) return (Boolean) v;
//         } catch (Throwable ignored) {}
//         return false;
//     }

//     private boolean isDead(Object model) {
//         try {
//             Object v = model.getClass().getMethod("isMarioDead").invoke(model);
//             if (v instanceof Boolean) return (Boolean) v;
//         } catch (Throwable ignored) {}
//         Integer s = getGameStatus(model);
//         return s != null && (s == 2 || s == -1);
//     }

//     private boolean isWin(Object model) {
//         try {
//             Object v = model.getClass().getMethod("isMarioWin").invoke(model);
//             if (v instanceof Boolean) return (Boolean) v;
//         } catch (Throwable ignored) {}
//         Integer s = getGameStatus(model);
//         return s != null && s == 1;
//     }

//     private Integer getGameStatus(Object model) {
//         try {
//             Object v = model.getClass().getMethod("getGameStatus").invoke(model);
//             if (v instanceof Number) return ((Number) v).intValue();
//         } catch (Throwable ignored) {}
//         return null;
//     }
// }
