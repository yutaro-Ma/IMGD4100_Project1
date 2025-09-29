
import engine.core.MarioGame;
import engine.core.MarioResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class PlayLevel {
    public static void printResults(MarioResult result) {
        System.out.println("****************************************************************");
        System.out.println("Game Status: " + result.getGameStatus().toString() +
                " Percentage Completion: " + result.getCompletionPercentage());
        System.out.println("Lives: " + result.getCurrentLives() + " Coins: " + result.getCurrentCoins() +
                " Remaining Time: " + (int) Math.ceil(result.getRemainingTime() / 1000f));
        System.out.println("Mario State: " + result.getMarioMode() +
                " (Mushrooms: " + result.getNumCollectedMushrooms() + " Fire Flowers: " + result.getNumCollectedFireflower() + ")");
        System.out.println("Total Kills: " + result.getKillsTotal() + " (Stomps: " + result.getKillsByStomp() +
                " Fireballs: " + result.getKillsByFire() + " Shells: " + result.getKillsByShell() +
                " Falls: " + result.getKillsByFall() + ")");
        System.out.println("Bricks: " + result.getNumDestroyedBricks() + " Jumps: " + result.getNumJumps() +
                " Max X Jump: " + result.getMaxXJump() + " Max Air Time: " + result.getMaxJumpAirTime());
        System.out.println("****************************************************************");
    }

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
        }
        return content;
    }
    private static int extractLevelNumber(Path p) {
        String name = p.getFileName().toString();        String digits = name.replaceAll("\\D+", "");
        if (digits.isEmpty()) return Integer.MAX_VALUE;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }
    private static List<Path> listLevelFiles() {
        try {
            Path dir = Paths.get("levels/original");
            if (Files.isDirectory(dir)) {
                List<Path> files = Files.list(dir)
                        .filter(p -> p.getFileName().toString().endsWith(".txt"))
                        .sorted(Comparator.comparingInt(PlayLevel::extractLevelNumber)
                                .thenComparing(p -> p.getFileName().toString()))
                        .collect(Collectors.toList());
                if (!files.isEmpty()) return files;
            }
        } catch (IOException ignored) {}
        return IntStream.rangeClosed(1, 32)
                .mapToObj(i -> Paths.get("levels/original/lvl-" + i + ".txt"))
                .filter(Files::exists)
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        MarioGame game = new MarioGame();
        List<Path> levels = listLevelFiles();

        if (levels.isEmpty()) {
            System.out.println("No level files found in levels/original.");
            return;
        }

        System.out.println("Found levels: " + levels.stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.joining(", ")));
        
        final int TIME_SECONDS = 20;   // 1レベルあたりの制限時間（秒）
        final int START_MODE   = 0;    // 0=small, 1=big, 2=fire
        final boolean VISUALS  = true; // 可視

        for (Path lp : levels) {
            String levelName = lp.getFileName().toString();
            System.out.println("\n=== Running " + levelName + " ===");
            MarioResult result = game.runGame(new agents.myAgent.myAgent(),
                    getLevel(lp.toString()), TIME_SECONDS, START_MODE, VISUALS);
            printResults(result);

            if (!"WIN".equalsIgnoreCase(result.getGameStatus().toString())) {
                System.out.println("Stopped: level failed (" + levelName + ").");
            }
        }
        System.out.println("Done.");
    }

    //     public static void main(String[] args) {
//         MarioGame game = new MarioGame();
//         // printResults(game.playGame(getLevel("../levels/original/lvl-1.txt"), 200, 0));
//         // game.playGame(
//         //     getLevel("levels/original/lvl-1.txt"),
//         //     100,   // time（sec）
//         //     2     // start: 0=small 1=big 2=fire
//         // );

//         printResults(game.runGame(new agents.myAgent.myAgent(),
//         getLevel("levels/original/lvl-10.txt"), 20, 0, true));

//     }
}
