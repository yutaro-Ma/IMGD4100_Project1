package agents.myAgent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Agent implements MarioAgent {
    private boolean[] action = new boolean[MarioActions.numberOfActions()];
    private boolean isFirstJump = true; // Track if this is the first jump of the session

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        // Mario is always running right in speed, jump only when needed
        action[MarioActions.RIGHT.getValue()] = true;
        action[MarioActions.SPEED.getValue()] = true;
        action[MarioActions.JUMP.getValue()] = false;
    }

    // function from ForwardAgent.java - I did not write this
    private byte[][] decode(MarioForwardModel model, int[][] state) {
        // Create byte[][] to match the oberservation grid
        byte[][] dstate = new byte[model.obsGridWidth][model.obsGridHeight];

        // fill all elements of the array with the default value 2.
        // This value is used to denote shells that are out of bounds or have not yet
        // been processed.
        for (int i = 0; i < dstate.length; ++i)
            for (int j = 0; j < dstate[0].length; ++j)
                dstate[i][j] = 2;

        // Iterate through the state array,
        // setting each value to 1 if it is not 0, and to 0 if it is 0.
        // If something exists, it's 1 if not it's 0.
        for (int x = 0; x < state.length; x++) {
            for (int y = 0; y < state[x].length; y++) {
                if (state[x][y] != 0) {
                    dstate[x][y] = 1;
                } else {
                    dstate[x][y] = 0;
                }
            }
        }
        return dstate;
    }

    // print the byte map
    private void printGrid(byte[][] map) {
        for (int y = 0; y < map[0].length; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < map.length; x++) {
                row.append(map[x][y]).append(' ');
            }
            System.out.println(row.toString());
        }
        System.out.println("----");
    }

    // Log map state to file when Mario jumps
    private void logMapStateToFile(byte[][] levelScene, byte[][] enemies, MarioForwardModel model, boolean shouldJump) {
        try {
            // Use fixed filename
            String filename = "mario_jump.txt";

            // Overwrite on first jump of session, append for subsequent jumps
            FileWriter writer = new FileWriter(filename, !isFirstJump);

            // Write header with timestamp and Mario's position
            LocalDateTime now = LocalDateTime.now();
            writer.write("=== Mario Jump Log - " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " ===\n");
            writer.write(
                    "Mario Position: (" + model.getMarioFloatPos()[0] + ", " + model.getMarioFloatPos()[1] + ")\n");
            writer.write("Mario Velocity: (" + model.getMarioFloatVelocity()[0] + ", "
                    + model.getMarioFloatVelocity()[1] + ")\n\n");

            // Write level scene map with Mario position marked as 2
            writer.write("--- Level Scene Map (Mario=2) ---\n");
            for (int y = 0; y < levelScene[0].length; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < levelScene.length; x++) {
                    // Mark Mario's position (8,8) as 2
                    if (x == 8 && y == 8) {
                        row.append("2 ");
                    } else {
                        row.append(levelScene[x][y]).append(' ');
                    }
                }
                writer.write(row.toString() + "\n");
            }
            writer.write("\n");

            // Write enemies map with Mario position marked as 2
            writer.write("--- Enemies Map (Mario=2) ---\n");
            for (int y = 0; y < enemies[0].length; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < enemies.length; x++) {
                    // Mark Mario's position (8,8) as 2
                    if (x == 8 && y == 8) {
                        row.append("2 ");
                    } else {
                        row.append(enemies[x][y]).append(' ');
                    }
                }
                writer.write(row.toString() + "\n");
            }
            writer.write("\n");

            // Write detection info
            writer.write("--- Detection Info ---\n");
            writer.write("Obstacle ahead: " + hasObstacleAhead(levelScene) + "\n");
            writer.write("Enemy ahead: " + hasEnemyAhead(enemies) + "\n");
            writer.write("Enemy very close: " + hasEnemyVeryClose(enemies) + "\n");
            writer.write("Mario X velocity: " + model.getMarioFloatVelocity()[0] + "\n");
            writer.write("Mario Y velocity: " + model.getMarioFloatVelocity()[1] + "\n");
            writer.write("Should jump: " + shouldJump + "\n");
            writer.write("==========================================\n\n");

            writer.close();
            System.out.println("Map state logged to: " + filename);

        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    // Simple and effective detection methods
    private boolean hasObstacleAhead(byte[][] levelScene) {
        // Check for walls or gaps 1-3 tiles ahead
        for (int x = 9; x <= 11; x++) {
            if (x < levelScene.length) {
                // Wall at head/body level
                if (levelScene[x][7] == 1 || levelScene[x][8] == 1) {
                    return true;
                }
                // Gap at ground level
                if (levelScene[x][14] == 0 || levelScene[x][15] == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasEnemyAhead(byte[][] enemies) {
        // Check for enemies 1-4 tiles ahead
        for (int y = 7; y <= 9; y++) {
            for (int x = 9; x <= 12; x++) {
                if (x < enemies.length && y < enemies[0].length) {
                    if (enemies[x][y] == 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasEnemyVeryClose(byte[][] enemies) {
        // Check for enemies 1-2 tiles ahead (emergency)
        for (int y = 7; y <= 9; y++) {
            for (int x = 9; x <= 10; x++) {
                if (x < enemies.length && y < enemies[0].length) {
                    if (enemies[x][y] == 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        byte[][] levelScene = decode(model, model.getMarioSceneObservation());
        byte[][] enemies = decode(model, model.getMarioEnemiesObservation());

        // Simple and effective logic
        boolean obstacleAhead = hasObstacleAhead(levelScene);
        boolean enemyAhead = hasEnemyAhead(enemies);
        boolean enemyVeryClose = hasEnemyVeryClose(enemies);

        // Always keep running
        action[MarioActions.RIGHT.getValue()] = true;
        action[MarioActions.SPEED.getValue()] = true;

        // Simple jump logic: Jump when needed, don't overthink
        boolean shouldJump = false;

        if (enemyVeryClose) {
            // Emergency: enemy too close
            shouldJump = true;
        } else if (obstacleAhead) {
            // Jump over walls and gaps
            shouldJump = true;
        } else if (enemyAhead) {
            // Jump to avoid enemies
            shouldJump = true;
        }

        action[MarioActions.JUMP.getValue()] = shouldJump;

        // Log when jumping
        if (shouldJump) {
            logMapStateToFile(levelScene, enemies, model, shouldJump);
            isFirstJump = false;
        }

        return action;
    }

    @Override
    public String getAgentName() {
        return "myAgent";
    }
}
