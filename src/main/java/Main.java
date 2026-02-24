import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

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
            Game game = new Game(table, engine, Arrays.asList(human, aiOne, aiTwo));
            decisionProvider.bindGame(game);

            System.out.println("三人卡五星对决开始，祝你好运！");
            List<Game.TurnOutcome> opening = game.startRound();
            if (processOutcomes(game, opening)) {
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
            List<Game.TurnOutcome> outcomes = game.playTurn();
            if (processOutcomes(game, outcomes)) {
                break;
            }
        }
        System.out.println("本局结束。");
    }

    private static void printHand(List<Card> hand) {
        System.out.print("你的手牌：");
        List<IndexedCard> indexed = new java.util.ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            indexed.add(new IndexedCard(i, hand.get(i)));
        }
        indexed.sort((a, b) -> compareCards(a.card, b.card));
        for (IndexedCard entry : indexed) {
            System.out.print(" [" + entry.index + ":" + entry.card + "]");
        }
        System.out.println();
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

    private static boolean processOutcomes(Game game, List<Game.TurnOutcome> outcomes) {
        for (Game.TurnOutcome outcome : outcomes) {
            if (handleOutcome(game, outcome)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleOutcome(Game game, Game.TurnOutcome outcome) {
        if (outcome == null) {
            return false;
        }
        switch (outcome.getActionType()) {
            case WALL_DEPLETED:
                System.out.println("牌堆摸空，流局结束。");
                return true;
            case HU:
                String style = outcome.isSelfDraw() ? "自摸" : "点炮";
                EnumSet<WinCategory> categories = outcome.getCategories();
                String categoryText = categories != null && !categories.isEmpty()
                        ? formatCategories(categories)
                        : "屁胡";
                System.out.println(outcome.getPlayer().getName() + " " + style + "胡牌！番数: "
                        + outcome.getFan() + "，牌型：" + categoryText);
                return true;
            case INITIAL_DISCARD:
                System.out.println(outcome.getPlayer().getName() + " 开始牌打出: " + outcome.getDiscardedCard());
                printDiscardPile(game.getDiscardPile());
                return false;
            case DRAW_DISCARD:
                if (outcome.getDrawnCard() != null) {
                    System.out.println(outcome.getPlayer().getName() + " 摸到 " + outcome.getDrawnCard() + "，打出: " + outcome.getDiscardedCard());
                } else {
                    System.out.println(outcome.getPlayer().getName() + " 打出: " + outcome.getDiscardedCard());
                }
                printDiscardPile(game.getDiscardPile());
                return false;
            case CLAIM_PENG:
                System.out.println(outcome.getPlayer().getName() + " 碰了 " + outcome.getClaimedCard() + "，打出: " + outcome.getDiscardedCard());
                printDiscardPile(game.getDiscardPile());
                return false;
            case CLAIM_GANG:
                System.out.println(outcome.getPlayer().getName() + " 杠了 " + outcome.getClaimedCard() + "，打出: " + outcome.getDiscardedCard());
                printDiscardPile(game.getDiscardPile());
                return false;
            default:
                return false;
        }
    }

    private static String formatCategories(EnumSet<WinCategory> categories) {
        return categories.stream()
                .map(Main::categoryName)
                .collect(Collectors.joining("、"));
    }

    private static String categoryName(WinCategory category) {
        switch (category) {
            case QING_YI_SE:
                return "清一色";
            case PENG_PENG_HU:
                return "碰碰胡";
            case KA_WU:
                return "卡五星";
            case MING_SI_GUI:
                return "明四归";
            case AN_SI_GUI:
                return "暗四归";
            case QI_DUI:
                return "七对";
            case LONG_QI_DUI:
                return "龙七对";
            case PI_HU:
            default:
                return "屁胡";
        }
    }

    private static int compareCards(Card a, Card b) {
        int typeDiff = cardCategory(a) - cardCategory(b);
        if (typeDiff != 0) {
            return typeDiff;
        }
        if (!a.isHonor() && !b.isHonor()) {
            return Integer.compare(a.getRank(), b.getRank());
        }
        return 0;
    }

    private static int cardCategory(Card card) {
        if (!card.isHonor()) {
            return card.getSuit() == Card.Suit.TIAO ? 0 : 1;
        }
        switch (card.getHonor()) {
            case HONG_ZHONG:
                return 2;
            case FA_CAI:
                return 3;
            case BAI_BAN:
                return 4;
            default:
                return 5;
        }
    }

    private static final class IndexedCard {
        private final int index;
        private final Card card;

        private IndexedCard(int index, Card card) {
            this.index = index;
            this.card = card;
        }
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

        @Override
        public ReactionType chooseReaction(Card tile, List<Card> hand, boolean canPeng, boolean canGang) {
            if (!canPeng && !canGang) {
                return ReactionType.NONE;
            }
            System.out.println("\n轮到你选择是否对 " + tile + " 进行操作：");
            System.out.print("可选操作：");
            if (canPeng) {
                System.out.print("[p]碰 ");
            }
            if (canGang) {
                System.out.print("[g]杠 ");
            }
            System.out.println("[n]过");
            while (true) {
                System.out.print("请输入选择: ");
                String input = scanner.nextLine().trim().toLowerCase();
                switch (input) {
                    case "p":
                        if (canPeng) {
                            return ReactionType.PENG;
                        }
                        break;
                    case "g":
                        if (canGang) {
                            return ReactionType.GANG;
                        }
                        break;
                    case "n":
                        return ReactionType.NONE;
                    default:
                        break;
                }
                System.out.println("输入无效，请重新选择。");
            }
        }

        @Override
        public boolean shouldHuOnDiscard(Card tile, List<Card> hand, List<Meld> melds, List<WinCategory> categories, int fan) {
            String categoryText = categories == null || categories.isEmpty()
                    ? "屁胡"
                    : categories.stream().map(Main::categoryName).collect(Collectors.joining("、"));
            System.out.println("\n" + tile + " 可胡，牌型：" + categoryText + "，番数：" + fan);
            printHand(hand);
            if (melds != null && !melds.isEmpty()) {
                System.out.println("你的已落地牌组：");
                for (Meld meld : melds) {
                    System.out.println(meld.getType() + ": " + meld.getCards());
                }
            }
            return askYesNo("是否胡牌？(y/n): ");
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
