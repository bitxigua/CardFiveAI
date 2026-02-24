import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 负责管理三人局的摸打节奏。
 */
public class Game {
    public static final int PLAYER_COUNT = 3;
    public static final int INITIAL_HAND_SIZE = 13;
    private static final EnumSet<WinCategory> DISCARD_WIN_TYPES = EnumSet.of(
            WinCategory.QING_YI_SE,
            WinCategory.PENG_PENG_HU,
            WinCategory.KA_WU,
            WinCategory.MING_SI_GUI,
            WinCategory.AN_SI_GUI,
            WinCategory.QI_DUI,
            WinCategory.LONG_QI_DUI
    );

    private final Table table;
    private final GameEngine engine;
    private final List<Player> players;
    private final List<Card> discardPile = new ArrayList<>();
    private int currentPlayerIndex;
    private int dealerIndex = 0;
    private boolean roundActive;

    public Game(Table table, GameEngine engine, List<Player> players) {
        this.table = Objects.requireNonNull(table, "Table 不能为空");
        this.engine = Objects.requireNonNull(engine, "GameEngine 不能为空");
        if (players == null || players.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException("三人局需要恰好三名玩家");
        }
        this.players = new ArrayList<>(players);
    }

    /**
     * 初始化一局：按本地规则发牌，并让庄家先行打出一张。
     *
     * @return 开始牌阶段产生的事件序列
     */
    public List<TurnOutcome> startRound() {
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
    public List<TurnOutcome> playTurn() {
        ensureRoundActive();
        List<TurnOutcome> events = new ArrayList<>();
        if (table.getDeck().isEmpty()) {
            roundActive = false;
            events.add(TurnOutcome.wallDepleted());
            return events;
        }
        Player player = players.get(currentPlayerIndex);
        Card drawn = table.deal();
        player.draw(drawn);
        Player.TurnAction action = player.playTurn();
        if (action.isHu()) {
            roundActive = false;
            events.add(TurnOutcome.hu(player, drawn, action.getFan(), true, EnumSet.of(WinCategory.PI_HU)));
            return events;
        }
        Card discarded = action.getDiscarded();
        if (discarded == null) {
            throw new IllegalStateException("玩家未返回出牌信息");
        }
        discardPile.add(discarded);
        events.add(TurnOutcome.drawDiscard(player, drawn, discarded));
        ClaimResult claimResult = resolveClaims(discarded, currentPlayerIndex, false);
        events.addAll(claimResult.events);
        if (claimResult.roundEnded) {
            roundActive = false;
            return events;
        }
        currentPlayerIndex = (claimResult.finalDiscarerIndex + 1) % players.size();
        return events;
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

    private List<TurnOutcome> performDealerOpeningMove() {
        List<TurnOutcome> events = new ArrayList<>();
        Player dealer = players.get(dealerIndex);
        Player.TurnAction action = dealer.playTurn();
        if (action.isHu()) {
            roundActive = false;
            events.add(TurnOutcome.hu(dealer, null, action.getFan(), true, EnumSet.of(WinCategory.PI_HU)));
            return events;
        }
        Card discarded = action.getDiscarded();
        if (discarded == null) {
            throw new IllegalStateException("庄家未打出任何牌");
        }
        discardPile.add(discarded);
        events.add(TurnOutcome.initialDiscard(dealer, discarded));
        ClaimResult claimResult = resolveClaims(discarded, dealerIndex, false);
        events.addAll(claimResult.events);
        if (claimResult.roundEnded) {
            roundActive = false;
            return events;
        }
        currentPlayerIndex = (claimResult.finalDiscarerIndex + 1) % players.size();
        return events;
    }

    private ClaimResult resolveClaims(Card tile, int discarderIndex, boolean allowPiHu) {
        ClaimResult result = new ClaimResult(discarderIndex);
        List<PendingWin> pendingWins = new ArrayList<>();
        // 先收集所有可以胡同一张的玩家，支持一炮多响
        for (int offset = 1; offset < players.size(); offset++) {
            int playerIndex = (discarderIndex + offset) % players.size();
            Player candidate = players.get(playerIndex);
            WinCheck winCheck = analyzeDiscardWin(candidate, tile, allowPiHu);
            if (winCheck.canHu && winCheck.allowDiscard && candidate.shouldHuOnDiscard(tile, winCheck.categories, winCheck.fan)) {
                pendingWins.add(new PendingWin(candidate, winCheck));
            }
        }
        if (!pendingWins.isEmpty()) {
            removeLastDiscard(tile);
            for (PendingWin win : pendingWins) {
                result.events.add(TurnOutcome.hu(win.player, tile, win.winCheck.fan, false, win.winCheck.categories));
            }
            result.finalDiscarerIndex = discarderIndex;
            result.roundEnded = true;
            return result;
        }

        // 若无人胡牌，再依次处理碰/杠
        for (int offset = 1; offset < players.size(); offset++) {
            int playerIndex = (discarderIndex + offset) % players.size();
            Player candidate = players.get(playerIndex);
            boolean canPeng = candidate.canPeng(tile);
            boolean canGang = candidate.canGang(tile);
            if (!canPeng && !canGang) {
                continue;
            }
            ReactionType reaction = candidate.chooseReaction(tile, canPeng, canGang);
            if (reaction == ReactionType.NONE) {
                continue;
            }
            Card claimedTile = removeLastDiscard(tile);
            candidate.claimTile(claimedTile, reaction);
            Card newDiscard = candidate.discardOne();
            discardPile.add(newDiscard);
            if (reaction == ReactionType.GANG) {
                result.events.add(TurnOutcome.claimGang(candidate, claimedTile, newDiscard));
            } else {
                result.events.add(TurnOutcome.claimPeng(candidate, claimedTile, newDiscard));
            }
            ClaimResult chained = resolveClaims(newDiscard, playerIndex, reaction == ReactionType.GANG);
            result.events.addAll(chained.events);
            result.finalDiscarerIndex = chained.finalDiscarerIndex;
            if (chained.roundEnded) {
                result.roundEnded = true;
            }
            return result;
        }
        result.finalDiscarerIndex = discarderIndex;
        return result;
    }

    private WinCheck analyzeDiscardWin(Player player, Card tile, boolean allowPiHu) {
        if (tile == null) {
            return WinCheck.noWin();
        }
        List<Card> evaluationTiles = new ArrayList<>(player.getEvaluationTiles());
        evaluationTiles.add(tile);
        boolean standardHu = engine.checkStandardPattern(evaluationTiles);
        boolean sevenPairs = player.getMelds().isEmpty() && engine.isSevenPairs(evaluationTiles);
        if (!standardHu && !sevenPairs) {
            return WinCheck.noWin();
        }
        EnumSet<WinCategory> categories = EnumSet.noneOf(WinCategory.class);
        if (sevenPairs) {
            categories.add(WinCategory.QI_DUI);
            if (engine.isLongSevenPairs(evaluationTiles)) {
                categories.add(WinCategory.LONG_QI_DUI);
            }
        }
        if (standardHu) {
            if (isQingYiSe(evaluationTiles)) {
                categories.add(WinCategory.QING_YI_SE);
            }
            if (isPengPengHu(engine.countTiles(evaluationTiles))) {
                categories.add(WinCategory.PENG_PENG_HU);
            }
            if (isKaWu(tile, evaluationTiles)) {
                categories.add(WinCategory.KA_WU);
            }
        }
        if (hasMingSiGui(player, tile)) {
            categories.add(WinCategory.MING_SI_GUI);
        } else if (hasAnSiGui(player, tile)) {
            categories.add(WinCategory.AN_SI_GUI);
        }
        if (categories.isEmpty()) {
            categories.add(WinCategory.PI_HU);
        }
        boolean allow = allowsDiscardWin(categories, allowPiHu);
        int fan = engine.calculateFan(evaluationTiles);
        return new WinCheck(true, allow, categories, fan);
    }

    private boolean allowsDiscardWin(EnumSet<WinCategory> categories, boolean allowPiHu) {
        for (WinCategory category : categories) {
            if (DISCARD_WIN_TYPES.contains(category) || (allowPiHu && category == WinCategory.PI_HU)) {
                return true;
            }
        }
        return false;
    }

    private boolean isQingYiSe(List<Card> tiles) {
        Card.Suit suit = null;
        for (Card card : tiles) {
            if (card.isHonor()) {
                return false;
            }
            if (suit == null) {
                suit = card.getSuit();
            } else if (card.getSuit() != suit) {
                return false;
            }
        }
        return suit != null;
    }

    private boolean isPengPengHu(int[] originalCounts) {
        int[] counts = originalCounts.clone();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] >= 2) {
                counts[i] -= 2;
                if (allMultiplesOfThree(counts)) {
                    counts[i] += 2;
                    return true;
                }
                counts[i] += 2;
            }
        }
        return false;
    }

    private boolean allMultiplesOfThree(int[] counts) {
        for (int count : counts) {
            if (count % 3 != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isKaWu(Card tile, List<Card> tiles) {
        if (tile == null || tile.isHonor() || tile.getRank() == null || tile.getRank() != 5) {
            return false;
        }
        boolean hasFour = false;
        boolean hasSix = false;
        for (Card card : tiles) {
            if (card.isHonor() || card.getSuit() != tile.getSuit()) {
                continue;
            }
            if (card.getRank() != null) {
                if (card.getRank() == 4) {
                    hasFour = true;
                } else if (card.getRank() == 6) {
                    hasSix = true;
                }
            }
        }
        return hasFour && hasSix;
    }

    private boolean hasMingSiGui(Player player, Card tile) {
        if (tile == null) {
            return false;
        }
        for (Meld meld : player.getMelds()) {
            if (!meld.isConcealed()
                    && (meld.getType() == MeldType.PENG || meld.getType() == MeldType.GANG)
                    && meldMatchesTile(meld, tile)) {
                int total = meld.getCards().size() + countTile(player.getHand(), tile);
                if (total >= 4) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnSiGui(Player player, Card tile) {
        if (tile == null) {
            return false;
        }
        return countTile(player.getHand(), tile) >= 3;
    }

    private boolean meldMatchesTile(Meld meld, Card tile) {
        if (meld.getCards().isEmpty()) {
            return false;
        }
        return isSameTile(meld.getCards().get(0), tile);
    }

    private int countTile(List<Card> cards, Card tile) {
        int count = 0;
        for (Card card : cards) {
            if (isSameTile(card, tile)) {
                count++;
            }
        }
        return count;
    }

    private Card removeLastDiscard(Card tile) {
        if (discardPile.isEmpty()) {
            throw new IllegalStateException("桌面没有可供吃碰的牌");
        }
        Card last = discardPile.remove(discardPile.size() - 1);
        if (!isSameTile(last, tile)) {
            throw new IllegalStateException("桌面最后一张牌与期望不符");
        }
        return last;
    }

    private boolean isSameTile(Card first, Card second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.isHonor() || second.isHonor()) {
            return first.isHonor() && second.isHonor() && first.getHonor() == second.getHonor();
        }
        return first.getSuit() == second.getSuit()
                && first.getRank() != null
                && first.getRank().equals(second.getRank());
    }

    /**
     * 表示单个回合的执行结果。
     */
    public static final class TurnOutcome {
        public enum ActionType {
            INITIAL_DISCARD,
            DRAW_DISCARD,
            CLAIM_PENG,
            CLAIM_GANG,
            HU,
            WALL_DEPLETED
        }

        private final ActionType actionType;
        private final Player player;
        private final Card drawnCard;
        private final Card discardedCard;
        private final Card claimedCard;
        private final Card winningCard;
        private final int fan;
        private final boolean selfDraw;
        private final EnumSet<WinCategory> categories;

        private TurnOutcome(ActionType actionType, Player player, Card drawnCard, Card discardedCard,
                            Card claimedCard, Card winningCard, int fan, boolean selfDraw, EnumSet<WinCategory> categories) {
            this.actionType = actionType;
            this.player = player;
            this.drawnCard = drawnCard;
            this.discardedCard = discardedCard;
            this.claimedCard = claimedCard;
            this.winningCard = winningCard;
            this.fan = fan;
            this.selfDraw = selfDraw;
            this.categories = categories == null ? null : EnumSet.copyOf(categories);
        }

        public static TurnOutcome initialDiscard(Player player, Card discarded) {
            return new TurnOutcome(ActionType.INITIAL_DISCARD, player, null, discarded, null, null, 0, false, null);
        }

        public static TurnOutcome drawDiscard(Player player, Card drawn, Card discarded) {
            return new TurnOutcome(ActionType.DRAW_DISCARD, player, drawn, discarded, null, null, 0, false, null);
        }

        public static TurnOutcome hu(Player player, Card winningCard, int fan, boolean selfDraw, EnumSet<WinCategory> categories) {
            return new TurnOutcome(ActionType.HU, player, selfDraw ? winningCard : null, null, null, winningCard, fan, selfDraw, categories);
        }

        public static TurnOutcome wallDepleted() {
            return new TurnOutcome(ActionType.WALL_DEPLETED, null, null, null, null, null, 0, false, null);
        }

        public static TurnOutcome claimPeng(Player player, Card claimed, Card discarded) {
            return new TurnOutcome(ActionType.CLAIM_PENG, player, null, discarded, claimed, null, 0, false, null);
        }

        public static TurnOutcome claimGang(Player player, Card claimed, Card discarded) {
            return new TurnOutcome(ActionType.CLAIM_GANG, player, null, discarded, claimed, null, 0, false, null);
        }

        public ActionType getActionType() {
            return actionType;
        }

        public boolean isHu() {
            return actionType == ActionType.HU;
        }

        public boolean isWallDepleted() {
            return actionType == ActionType.WALL_DEPLETED;
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

        public Card getWinningCard() {
            return winningCard;
        }

        public Card getClaimedCard() {
            return claimedCard;
        }

        public int getFan() {
            return fan;
        }

        public boolean isSelfDraw() {
            return selfDraw;
        }

        public EnumSet<WinCategory> getCategories() {
            return categories == null ? null : EnumSet.copyOf(categories);
        }
    }

    private static final class ClaimResult {
        private final List<TurnOutcome> events = new ArrayList<>();
        private int finalDiscarerIndex;
        private boolean roundEnded;

        private ClaimResult(int finalDiscarerIndex) {
            this.finalDiscarerIndex = finalDiscarerIndex;
        }
    }

    private static final class WinCheck {
        private final boolean canHu;
        private final boolean allowDiscard;
        private final EnumSet<WinCategory> categories;
        private final int fan;

        private WinCheck(boolean canHu, boolean allowDiscard, EnumSet<WinCategory> categories, int fan) {
            this.canHu = canHu;
            this.allowDiscard = allowDiscard;
            this.categories = categories;
            this.fan = fan;
        }

        private static WinCheck noWin() {
            return new WinCheck(false, false, EnumSet.noneOf(WinCategory.class), 0);
        }
    }

    private static final class PendingWin {
        private final Player player;
        private final WinCheck winCheck;

        private PendingWin(Player player, WinCheck winCheck) {
            this.player = player;
            this.winCheck = winCheck;
        }
    }
}
