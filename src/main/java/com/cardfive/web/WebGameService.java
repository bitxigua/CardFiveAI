package com.cardfive.web;

import com.cardfive.AIPlayer;
import com.cardfive.AsyncDecisionProvider;
import com.cardfive.Card;
import com.cardfive.Game;
import com.cardfive.GameEngine;
import com.cardfive.HumanPlayer;
import com.cardfive.Meld;
import com.cardfive.Player;
import com.cardfive.PlayerInteractionHandler;
import com.cardfive.ReactionType;
import com.cardfive.Table;
import com.cardfive.WinCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

@Service
public class WebGameService implements PlayerInteractionHandler {
    private final GameEngine engine = new GameEngine();
    private final Table table = new Table();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mahjong-loop");
        thread.setDaemon(true);
        return thread;
    });

    private final List<String> logEntries = new CopyOnWriteArrayList<>();

    private Game game;
    private HumanPlayer humanPlayer;
    private AIPlayer aiOne;
    private AIPlayer aiTwo;
    private AsyncDecisionProvider decisionProvider;

    private volatile IntConsumer pendingDiscardAction;
    private volatile Consumer<ReactionType> pendingReactionAction;
    private volatile Consumer<Boolean> pendingHuAction;
    private volatile PendingRequest pendingRequest;
    private volatile GameStatus gameStatus = GameStatus.IDLE;
    private volatile String statusMessage = "等待开始";
    private volatile List<HandTileView> latestHandView = Collections.emptyList();
    private volatile List<CardView> latestDiscardView = Collections.emptyList();
    private volatile List<CardView> aiOneHandView = Collections.emptyList();
    private volatile List<CardView> aiTwoHandView = Collections.emptyList();

    public synchronized void startNewRound() {
        if (gameStatus == GameStatus.RUNNING || gameStatus == GameStatus.WAITING_FOR_PLAYER) {
            throw new IllegalStateException("当前对局仍在进行中，请先完成或重置。");
        }
        resetState();
        decisionProvider = new AsyncDecisionProvider(this);
        humanPlayer = new HumanPlayer("玩家", engine, decisionProvider);
        aiOne = new AIPlayer("AI-1", engine);
        aiTwo = new AIPlayer("AI-2", engine);
        game = new Game(table, engine, Arrays.asList(humanPlayer, aiOne, aiTwo));
        gameStatus = GameStatus.RUNNING;
        statusMessage = "发牌中...";
        executor.submit(this::runGameLoop);
    }

    public GameStateView getCurrentState() {
        return new GameStateView(
                gameStatus,
                statusMessage,
                latestHandView,
                latestDiscardView,
                aiOneHandView,
                aiTwoHandView,
                new ArrayList<>(logEntries),
                pendingRequest
        );
    }

    public void submitDiscard(int index) {
        IntConsumer consumer = pendingDiscardAction;
        if (consumer == null) {
            throw new IllegalStateException("当前没有等待弃牌的操作。");
        }
        pendingDiscardAction = null;
        pendingRequest = null;
        gameStatus = GameStatus.RUNNING;
        consumer.accept(index);
    }

    public void submitReaction(ReactionType reaction) {
        Consumer<ReactionType> consumer = pendingReactionAction;
        if (consumer == null) {
            throw new IllegalStateException("当前没有可响应的碰杠请求。");
        }
        pendingReactionAction = null;
        pendingRequest = null;
        gameStatus = GameStatus.RUNNING;
        consumer.accept(reaction == null ? ReactionType.NONE : reaction);
    }

    public void submitHuDecision(boolean hu) {
        Consumer<Boolean> consumer = pendingHuAction;
        if (consumer == null) {
            throw new IllegalStateException("当前没有胡牌确认请求。");
        }
        pendingHuAction = null;
        pendingRequest = null;
        gameStatus = GameStatus.RUNNING;
        consumer.accept(hu);
    }

    private void runGameLoop() {
        try {
            List<Game.TurnOutcome> opening = game.startRound();
            processEvents(opening);
            while (game.isRoundActive()) {
                List<Game.TurnOutcome> events = game.playTurn();
                processEvents(events);
            }
            updateStatus("本局结束");
            gameStatus = GameStatus.FINISHED;
        } catch (Exception e) {
            logEntries.add("发生异常: " + e.getMessage());
            updateStatus("出现异常，详见日志");
            gameStatus = GameStatus.ERROR;
        } finally {
            clearPendingActions();
            refreshSnapshots();
        }
    }

    private void processEvents(List<Game.TurnOutcome> events) {
        for (Game.TurnOutcome outcome : events) {
            handleOutcome(outcome);
        }
        refreshSnapshots();
    }

    private void handleOutcome(Game.TurnOutcome outcome) {
        if (outcome == null) {
            return;
        }
        switch (outcome.getActionType()) {
            case INITIAL_DISCARD:
                logEntries.add(outcome.getPlayer().getName() + " 开始牌打出 " + outcome.getDiscardedCard());
                break;
            case DRAW_DISCARD:
                logEntries.add(outcome.getPlayer().getName() + " 摸到 " + outcome.getDrawnCard()
                        + "，打出 " + outcome.getDiscardedCard());
                break;
            case CLAIM_PENG:
                logEntries.add(formatClaimLog(outcome, "碰"));
                break;
            case CLAIM_GANG:
                logEntries.add(formatClaimLog(outcome, "杠"));
                break;
            case HU:
                EnumSet<WinCategory> categories = outcome.getCategories();
                String categoryText = categories != null && !categories.isEmpty()
                        ? formatCategories(categories)
                        : "屁胡";
                String style = outcome.isSelfDraw() ? "自摸" : "点炮";
                logEntries.add(outcome.getPlayer().getName() + " " + style + "胡牌！番数 "
                        + outcome.getFan() + "，牌型：" + categoryText);
                break;
            case WALL_DEPLETED:
                logEntries.add("牌堆摸空，流局结束。");
                updateStatus("流局");
                break;
            default:
                break;
        }
    }

    private String formatClaimLog(Game.TurnOutcome outcome, String action) {
        StringBuilder builder = new StringBuilder();
        builder.append(outcome.getPlayer().getName())
                .append(' ')
                .append(action)
                .append(' ')
                .append(outcome.getClaimedCard());
        if (outcome.getDiscardedCard() != null) {
            builder.append("，打出 ").append(outcome.getDiscardedCard());
        }
        return builder.toString();
    }

    private List<CardView> buildAiHand(Player aiPlayer) {
        if (aiPlayer == null) {
            return Collections.emptyList();
        }
        List<Card> cards = new ArrayList<>(aiPlayer.getHand());
        cards.sort(WebGameService::compareCards);
        return cards.stream()
                .map(CardView::fromCard)
                .collect(Collectors.toList());
    }

    private void refreshSnapshots() {
        if (humanPlayer != null) {
            List<IndexedCard> sorted = new ArrayList<>();
            List<Card> hand = humanPlayer.getHand();
            for (int i = 0; i < hand.size(); i++) {
                sorted.add(new IndexedCard(i, hand.get(i)));
            }
            sorted.sort((a, b) -> compareCards(a.card, b.card));
            List<HandTileView> tiles = new ArrayList<>();
            for (IndexedCard entry : sorted) {
                tiles.add(HandTileView.from(entry.index, entry.card));
            }
            latestHandView = tiles;
        } else {
            latestHandView = Collections.emptyList();
        }
        if (game != null) {
            List<CardView> discards = game.getDiscardPile()
                    .stream()
                    .map(CardView::fromCard)
                    .collect(Collectors.toList());
            latestDiscardView = discards;
        } else {
            latestDiscardView = Collections.emptyList();
        }

        aiOneHandView = buildAiHand(aiOne);
        aiTwoHandView = buildAiHand(aiTwo);
    }

    private void resetState() {
        clearPendingActions();
        pendingRequest = null;
        logEntries.clear();
        latestHandView = Collections.emptyList();
        latestDiscardView = Collections.emptyList();
        aiOneHandView = Collections.emptyList();
        aiTwoHandView = Collections.emptyList();
    }

    private void clearPendingActions() {
        pendingDiscardAction = null;
        pendingReactionAction = null;
        pendingHuAction = null;
    }

    private void updateStatus(String message) {
        this.statusMessage = message;
    }

    @Override
    public void requestDiscardSelection(List<Card> handSnapshot, IntConsumer consumer) {
        pendingDiscardAction = consumer;
        pendingReactionAction = null;
        pendingHuAction = null;
        pendingRequest = PendingRequest.forDiscard("请选择一张要打出的牌。");
        gameStatus = GameStatus.WAITING_FOR_PLAYER;
        refreshSnapshots();
    }

    @Override
    public void requestReaction(Card tile, boolean canPeng, boolean canGang, Consumer<ReactionType> consumer) {
        pendingReactionAction = consumer;
        pendingHuAction = null;
        pendingDiscardAction = null;
        pendingRequest = PendingRequest.forReaction(tile, canPeng, canGang);
        gameStatus = GameStatus.WAITING_FOR_PLAYER;
    }

    @Override
    public void requestSelfHu(List<Card> handSnapshot, int fan, Consumer<Boolean> consumer) {
        pendingHuAction = consumer;
        pendingReactionAction = null;
        pendingDiscardAction = null;
        pendingRequest = PendingRequest.forSelfHu(fan);
        gameStatus = GameStatus.WAITING_FOR_PLAYER;
        refreshSnapshots();
    }

    @Override
    public void requestDiscardHu(Card tile, List<Card> handSnapshot, List<Meld> meldsSnapshot,
                                 List<WinCategory> categories, int fan, Consumer<Boolean> consumer) {
        pendingHuAction = consumer;
        pendingReactionAction = null;
        pendingDiscardAction = null;
        pendingRequest = PendingRequest.forDiscardHu(tile, categories, fan);
        gameStatus = GameStatus.WAITING_FOR_PLAYER;
        refreshSnapshots();
    }

    @Override
    public void showMessage(String message) {
        updateStatus(message == null ? "" : message);
    }

    private static String formatCategories(EnumSet<WinCategory> categories) {
        return categories.stream()
                .map(WebGameService::categoryName)
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

    public enum GameStatus {
        IDLE,
        RUNNING,
        WAITING_FOR_PLAYER,
        FINISHED,
        ERROR
    }

    public static final class GameStateView {
        private final GameStatus status;
        private final String statusMessage;
        private final List<HandTileView> hand;
        private final List<CardView> discards;
        private final List<CardView> aiOneHand;
        private final List<CardView> aiTwoHand;
        private final List<String> logs;
        private final PendingRequest pendingRequest;

        public GameStateView(GameStatus status,
                             String statusMessage,
                             List<HandTileView> hand,
                             List<CardView> discards,
                             List<CardView> aiOneHand,
                             List<CardView> aiTwoHand,
                             List<String> logs,
                             PendingRequest pendingRequest) {
            this.status = status;
            this.statusMessage = statusMessage;
            this.hand = hand;
            this.discards = discards;
             this.aiOneHand = aiOneHand;
             this.aiTwoHand = aiTwoHand;
            this.logs = logs;
            this.pendingRequest = pendingRequest;
        }

        public GameStatus getStatus() {
            return status;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public List<HandTileView> getHand() {
            return hand;
        }

        public List<CardView> getDiscards() {
            return discards;
        }

        public List<CardView> getAiOneHand() {
            return aiOneHand;
        }

        public List<CardView> getAiTwoHand() {
            return aiTwoHand;
        }

        public List<String> getLogs() {
            return logs;
        }

        public PendingRequest getPendingRequest() {
            return pendingRequest;
        }
    }

    public static final class HandTileView extends CardView {
        private final int index;

        private HandTileView(int index, Card card) {
            super(card);
            this.index = index;
        }

        public static HandTileView from(int index, Card card) {
            return new HandTileView(index, card);
        }

        public int getIndex() {
            return index;
        }
    }

    public static class CardView {
        private final String label;
        private final String suit;
        private final Integer rank;
        private final String honor;
        private final boolean honorTile;

        protected CardView(Card card) {
            this.label = card.toString();
            this.suit = card.isHonor() ? null : card.getSuit().name();
            this.rank = card.isHonor() ? null : card.getRank();
            this.honor = card.isHonor() ? card.getHonor().name() : null;
            this.honorTile = card.isHonor();
        }

        public static CardView fromCard(Card card) {
            return new CardView(card);
        }

        public String getLabel() {
            return label;
        }

        public String getSuit() {
            return suit;
        }

        public Integer getRank() {
            return rank;
        }

        public String getHonor() {
            return honor;
        }

        public boolean isHonorTile() {
            return honorTile;
        }
    }

    public static final class PendingRequest {
        private final RequestType type;
        private final String message;
        private final CardView tile;
        private final boolean canPeng;
        private final boolean canGang;
        private final Integer fan;
        private final List<String> categories;

        private PendingRequest(RequestType type, String message, CardView tile,
                               boolean canPeng, boolean canGang, Integer fan, List<String> categories) {
            this.type = type;
            this.message = message;
            this.tile = tile;
            this.canPeng = canPeng;
            this.canGang = canGang;
            this.fan = fan;
            this.categories = categories;
        }

        public static PendingRequest forDiscard(String message) {
            return new PendingRequest(RequestType.DISCARD, message, null, false, false, null, null);
        }

        public static PendingRequest forReaction(Card tile, boolean canPeng, boolean canGang) {
            return new PendingRequest(RequestType.REACTION,
                    "是否对 " + tile + " 进行操作？",
                    CardView.fromCard(tile),
                    canPeng,
                    canGang,
                    null,
                    null);
        }

        public static PendingRequest forSelfHu(int fan) {
            return new PendingRequest(RequestType.SELF_HU,
                    "你可以自摸，番数：" + fan + "，是否胡牌？",
                    null,
                    false,
                    false,
                    fan,
                    null);
        }

        public static PendingRequest forDiscardHu(Card tile, List<WinCategory> categories, int fan) {
            List<String> names = categories == null ? Collections.emptyList()
                    : categories.stream().map(WebGameService::categoryName).collect(Collectors.toList());
            return new PendingRequest(RequestType.DISCARD_HU,
                    tile + " 可胡牌，番数：" + fan,
                    CardView.fromCard(tile),
                    false,
                    false,
                    fan,
                    names);
        }

        public RequestType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public CardView getTile() {
            return tile;
        }

        public boolean isCanPeng() {
            return canPeng;
        }

        public boolean isCanGang() {
            return canGang;
        }

        public Integer getFan() {
            return fan;
        }

        public List<String> getCategories() {
            return categories;
        }
    }

    public enum RequestType {
        DISCARD,
        REACTION,
        SELF_HU,
        DISCARD_HU
    }
}
