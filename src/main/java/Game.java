import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 负责管理三人局的摸打节奏。
 */
public class Game {
    public static final int PLAYER_COUNT = 3;
    public static final int INITIAL_HAND_SIZE = 13;

    private final Table table;
    private final List<Player> players;
    private final List<Card> discardPile = new ArrayList<>();
    private int currentPlayerIndex;
    private int dealerIndex = 0;
    private boolean roundActive;

    public Game(Table table, List<Player> players) {
        this.table = Objects.requireNonNull(table, "Table 不能为空");
        if (players == null || players.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException("三人局需要恰好三名玩家");
        }
        this.players = new ArrayList<>(players);
    }

    /**
     * 初始化一局：按本地规则发牌，并让庄家先行打出一张。
     *
     * @return 开始牌阶段产生的结果（可能胡牌或仅仅弃牌），若局已结束则 roundActive=false
     */
    public TurnOutcome startRound() {
        table.reset();
        table.shuffle();
        discardPile.clear();
        currentPlayerIndex = dealerIndex;
        roundActive = true;
        for (Player player : players) {
            player.resetHand();
        }
        dealInitialHandsByLocalRule();
        return performDealerOpeningMove();
    }

    /**
     * 执行当前玩家的一个出牌流程。
     */
    public TurnOutcome playTurn() {
        ensureRoundActive();
        if (table.getDeck().isEmpty()) {
            roundActive = false;
            return TurnOutcome.wallDepleted();
        }
        Player player = players.get(currentPlayerIndex);
        Card drawn = table.deal();
        player.draw(drawn);
        Player.TurnAction action = player.playTurn();
        if (action.isHu()) {
            roundActive = false;
            return TurnOutcome.hu(player, drawn, action.getFan());
        }
        Card discarded = action.getDiscarded();
        if (discarded == null) {
            throw new IllegalStateException("玩家未返回出牌信息");
        }
        discardPile.add(discarded);
        advanceTurn();
        return TurnOutcome.discard(player, drawn, discarded);
    }

    public boolean isRoundActive() {
        return roundActive;
    }

    public List<Card> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public int getDealerIndex() {
        return dealerIndex;
    }

    public void setDealerIndex(int dealerIndex) {
        if (dealerIndex < 0 || dealerIndex >= players.size()) {
            throw new IllegalArgumentException("庄家索引越界");
        }
        this.dealerIndex = dealerIndex;
    }

    private void ensureRoundActive() {
        if (!roundActive) {
            throw new IllegalStateException("当前没有正在进行的牌局");
        }
    }

    private void advanceTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    private void dealInitialHandsByLocalRule() {
        for (int round = 0; round < 3; round++) {
            drawBatch(dealerIndex, 4);
            drawBatch((dealerIndex + 1) % players.size(), 4);
            drawBatch((dealerIndex + 2) % players.size(), 4);
        }
        drawBatch(dealerIndex, 2);
        drawBatch((dealerIndex + 1) % players.size(), 1);
        drawBatch((dealerIndex + 2) % players.size(), 1);
    }

    private void drawBatch(int playerIndex, int count) {
        Player player = players.get(playerIndex);
        for (int i = 0; i < count; i++) {
            player.draw(table.deal());
        }
    }

    private TurnOutcome performDealerOpeningMove() {
        Player dealer = players.get(dealerIndex);
        Player.TurnAction action = dealer.playTurn();
        if (action.isHu()) {
            roundActive = false;
            return TurnOutcome.hu(dealer, null, action.getFan());
        }
        Card discarded = action.getDiscarded();
        if (discarded == null) {
            throw new IllegalStateException("庄家未打出任何牌");
        }
        discardPile.add(discarded);
        currentPlayerIndex = (dealerIndex + 1) % players.size();
        return TurnOutcome.discard(dealer, null, discarded);
    }

    /**
     * 表示单个回合的执行结果。
     */
    public static final class TurnOutcome {
        private final boolean hu;
        private final boolean wallDepleted;
        private final Player player;
        private final Card drawnCard;
        private final Card discardedCard;
        private final int fan;

        private TurnOutcome(boolean hu, boolean wallDepleted, Player player, Card drawnCard, Card discardedCard, int fan) {
            this.hu = hu;
            this.wallDepleted = wallDepleted;
            this.player = player;
            this.drawnCard = drawnCard;
            this.discardedCard = discardedCard;
            this.fan = fan;
        }

        public static TurnOutcome discard(Player player, Card drawn, Card discarded) {
            return new TurnOutcome(false, false, player, drawn, discarded, 0);
        }

        public static TurnOutcome hu(Player player, Card drawn, int fan) {
            return new TurnOutcome(true, false, player, drawn, null, fan);
        }

        public static TurnOutcome wallDepleted() {
            return new TurnOutcome(false, true, null, null, null, 0);
        }

        public boolean isHu() {
            return hu;
        }

        public boolean isWallDepleted() {
            return wallDepleted;
        }

        public Player getPlayer() {
            return player;
        }

        public Card getDrawnCard() {
            return drawnCard;
        }

        public Card getDiscardedCard() {
            return discardedCard;
        }

        public int getFan() {
            return fan;
        }
    }
}
