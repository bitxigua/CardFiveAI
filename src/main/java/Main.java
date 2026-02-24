import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * 控制台版本游戏主循环（真人 + 两个 AI）。
 */
public class Main {

    public static void main(String[] args) {
        GameEngine engine = new GameEngine();
        Table table = new Table();
        Scanner scanner = new Scanner(System.in);
        try {
            ConsoleDecisionProvider decisionProvider = new ConsoleDecisionProvider(scanner);
            HumanPlayer human = new HumanPlayer("玩家", engine, decisionProvider);
            AIPlayer aiOne = new AIPlayer("AI-1", engine);
            AIPlayer aiTwo = new AIPlayer("AI-2", engine);
            Game game = new Game(table, Arrays.asList(human, aiOne, aiTwo));
            decisionProvider.bindGame(game);

            System.out.println("三人卡五星对决开始，祝你好运！");
            Game.TurnOutcome opening = game.startRound();
            if (handleOutcome(game, opening)) {
                System.out.println("本局结束。");
                return;
            }
            runGameLoop(game);
        } finally {
            scanner.close();
        }
    }

    private static void runGameLoop(Game game) {
        while (game.isRoundActive()) {
            Player current = game.getPlayers().get(game.getCurrentPlayerIndex());
            System.out.println("\n--- 轮到 " + current.getName() + " ---");
            if (current instanceof HumanPlayer) {
                printHand(current.getHand());
                printDiscardPile(game.getDiscardPile());
            }
            Game.TurnOutcome outcome = game.playTurn();
            if (handleOutcome(game, outcome)) {
                break;
            }
        }
        System.out.println("本局结束。");
    }

    private static void printHand(List<Card> hand) {
        System.out.println("你的手牌：");
        for (int i = 0; i < hand.size(); i++) {
            System.out.println(i + ": " + hand.get(i));
        }
    }

    private static void printDiscardPile(List<Card> discards) {
        if (discards.isEmpty()) {
            System.out.println("桌面暂无弃牌。");
            return;
        }
        System.out.print("桌面牌：");
        for (Card card : discards) {
            System.out.print(card + " ");
        }
        System.out.println();
    }

    private static boolean handleOutcome(Game game, Game.TurnOutcome outcome) {
        if (outcome == null) {
            return false;
        }
        if (outcome.isWallDepleted()) {
            System.out.println("牌堆摸空，流局结束。");
            return true;
        }
        if (outcome.isHu()) {
            System.out.println(outcome.getPlayer().getName() + " 胡牌！番数: " + outcome.getFan());
            return true;
        }
        if (outcome.getDiscardedCard() != null && outcome.getPlayer() != null) {
            String action = outcome.getDrawnCard() == null ? "开始牌打出: " : "打出: ";
            System.out.println(outcome.getPlayer().getName() + " " + action + outcome.getDiscardedCard());
            printDiscardPile(game.getDiscardPile());
        }
        return false;
    }

    /**
     * 控制台实现的真人决策输入。
     */
    private static final class ConsoleDecisionProvider implements HumanDecisionProvider {
        private final Scanner scanner;
        private Game game;

        private ConsoleDecisionProvider(Scanner scanner) {
            this.scanner = scanner;
        }

        void bindGame(Game game) {
            this.game = game;
        }

        @Override
        public boolean shouldHu(List<Card> hand, int fan) {
            if (fan <= 0) {
                return false;
            }
            System.out.println("\n你可以胡牌，番数: " + fan);
            printHand(hand);
            return askYesNo("是否胡牌？(y/n): ");
        }

        @Override
        public int chooseDiscardIndex(List<Card> hand) {
            printHand(hand);
            if (game != null) {
                printDiscardPile(game.getDiscardPile());
            }
            while (true) {
                System.out.print("请选择要打出的牌索引: ");
                String line = scanner.nextLine();
                try {
                    int index = Integer.parseInt(line.trim());
                    if (index >= 0 && index < hand.size()) {
                        return index;
                    }
                } catch (NumberFormatException ignored) {
                }
                System.out.println("输入无效，请重新输入。");
            }
        }

        private boolean askYesNo(String prompt) {
            while (true) {
                System.out.print(prompt);
                String input = scanner.nextLine().trim().toLowerCase();
                if ("y".equals(input)) {
                    return true;
                }
                if ("n".equals(input)) {
                    return false;
                }
                System.out.println("请输入 y 或 n。");
            }
        }
    }
}
