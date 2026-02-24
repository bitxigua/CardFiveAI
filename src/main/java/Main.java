import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 控制台版本游戏主循环。
 */
public class Main {
    private static final int INITIAL_HAND_SIZE = 13;

    public static void main(String[] args) {
        GameEngine engine = new GameEngine();
        Table table = new Table();
        table.shuffle();

        AIPlayer ai = new AIPlayer("AI", engine);
        List<Card> humanHand = new ArrayList<>();
        List<Card> discardPile = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);

        dealInitialHands(table, ai, humanHand);
        System.out.println("游戏开始，输入索引选择要打出的牌。\n");

        while (true) {
            if (table.getDeck().isEmpty()) {
                System.out.println("牌堆已被摸光，流局结束。");
                break;
            }
            Card humanDraw = table.deal();
            humanHand.add(humanDraw);
            System.out.println("你摸到: " + humanDraw);

            if (engine.checkHu(humanHand)) {
                int fan = engine.calculateFan(humanHand);
                System.out.println("恭喜，你胡牌啦！番数: " + fan);
                break;
            }

            printHand(humanHand);
            int discardIndex = promptDiscardIndex(scanner, humanHand.size());
            Card humanDiscard = humanHand.remove(discardIndex);
            discardPile.add(humanDiscard);
            System.out.println("你打出: " + humanDiscard);
            printDiscards(discardPile);

            if (table.getDeck().isEmpty()) {
                System.out.println("牌堆已被摸光，流局结束。");
                break;
            }

            Card aiDraw = table.deal();
            ai.draw(aiDraw);
            System.out.println("AI 摸到一张牌。");
            Player.TurnAction action = ai.playTurn();
            if (action.isHu()) {
                System.out.println("AI 宣布胡牌！番数: " + action.getFan());
                break;
            } else if (action.getDiscarded() != null) {
                discardPile.add(action.getDiscarded());
                System.out.println("AI 打出: " + action.getDiscarded());
                printDiscards(discardPile);
            }
        }

        scanner.close();
        System.out.println("游戏结束。");
    }

    private static void dealInitialHands(Table table, AIPlayer ai, List<Card> humanHand) {
        for (int i = 0; i < INITIAL_HAND_SIZE; i++) {
            humanHand.add(table.deal());
            ai.draw(table.deal());
        }
    }

    private static void printHand(List<Card> hand) {
        System.out.println("当前手牌：");
        for (int i = 0; i < hand.size(); i++) {
            System.out.println(i + ": " + hand.get(i));
        }
    }

    private static int promptDiscardIndex(Scanner scanner, int handSize) {
        while (true) {
            System.out.print("请选择要打出的牌索引 (0-" + (handSize - 1) + "): ");
            String line = scanner.nextLine();
            try {
                int index = Integer.parseInt(line.trim());
                if (index >= 0 && index < handSize) {
                    return index;
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("输入无效，请重新输入。");
        }
    }

    private static void printDiscards(List<Card> discardPile) {
        if (discardPile.isEmpty()) {
            return;
        }
        System.out.print("桌面牌：");
        for (Card discard : discardPile) {
            System.out.print(discard + " ");
        }
        System.out.println();
    }
}
