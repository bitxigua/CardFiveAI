import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * JavaFX 图形界面入口。
 */
public class GuiApp extends Application implements GuiInteractionHandler {
    private final GameEngine engine = new GameEngine();
    private final Table table = new Table();

    private GuiDecisionProvider decisionProvider;
    private HumanPlayer humanPlayer;
    private AIPlayer aiOne;
    private AIPlayer aiTwo;
    private Game game;

    private FlowPane handPane;
    private FlowPane discardPane;
    private TextArea logArea;
    private Label statusLabel;
    private Button huButton;
    private Button pengButton;
    private Button gangButton;
    private Button passButton;

    private volatile IntConsumer pendingDiscardAction;
    private volatile Consumer<ReactionType> pendingReactionAction;
    private volatile Consumer<Boolean> pendingHuAction;

    private ExecutorService executor;

    @Override
    public void start(Stage stage) {
        decisionProvider = new GuiDecisionProvider(this);
        humanPlayer = new HumanPlayer("玩家", engine, decisionProvider);
        aiOne = new AIPlayer("AI-1", engine);
        aiTwo = new AIPlayer("AI-2", engine);
        game = new Game(table, engine, Arrays.asList(humanPlayer, aiOne, aiTwo));

        buildUI(stage);
        stage.show();

        executor = Executors.newSingleThreadExecutor();
        executor.submit(this::runGameLoop);
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void runGameLoop() {
        try {
            updateStatus("发牌中...");
            List<Game.TurnOutcome> opening = game.startRound();
            processEvents(opening);
            while (game.isRoundActive()) {
                List<Game.TurnOutcome> events = game.playTurn();
                processEvents(events);
            }
            updateStatus("本局结束");
        } catch (Exception e) {
            e.printStackTrace();
            appendLog("发生异常: " + e.getMessage());
            updateStatus("出现异常，详见日志");
        }
    }

    private void processEvents(List<Game.TurnOutcome> events) {
        for (Game.TurnOutcome outcome : events) {
            runOnFxAndWait(() -> handleOutcome(outcome));
        }
        runOnFxAndWait(() -> {
            refreshHand();
            refreshDiscardPile();
        });
    }

    private void handleOutcome(Game.TurnOutcome outcome) {
        switch (outcome.getActionType()) {
            case INITIAL_DISCARD:
                appendLog(outcome.getPlayer().getName() + " 开始牌打出 " + outcome.getDiscardedCard());
                break;
            case DRAW_DISCARD:
                appendLog(outcome.getPlayer().getName() + " 摸到 " + outcome.getDrawnCard()
                        + "，打出 " + outcome.getDiscardedCard());
                break;
            case CLAIM_PENG:
                appendLog(outcome.getPlayer().getName() + " 碰 " + outcome.getClaimedCard()
                        + "，打出 " + outcome.getDiscardedCard());
                break;
            case CLAIM_GANG:
                appendLog(outcome.getPlayer().getName() + " 杠 " + outcome.getClaimedCard()
                        + "，打出 " + outcome.getDiscardedCard());
                break;
            case HU:
                EnumSet<WinCategory> categories = outcome.getCategories();
                String categoryText = categories != null && !categories.isEmpty()
                        ? formatCategories(categories)
                        : "屁胡";
                String style = outcome.isSelfDraw() ? "自摸" : "点炮";
                appendLog(outcome.getPlayer().getName() + " " + style + "胡牌！番数 "
                        + outcome.getFan() + "，牌型：" + categoryText);
                break;
            case WALL_DEPLETED:
                appendLog("牌堆摸空，流局结束。");
                updateStatus("流局");
                break;
            default:
                break;
        }
    }

    private void buildUI(Stage stage) {
        statusLabel = new Label("初始化中...");
        handPane = new FlowPane();
        handPane.setHgap(8);
        handPane.setVgap(8);

        discardPane = new FlowPane();
        discardPane.setHgap(6);
        discardPane.setVgap(6);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);

        VBox centerBox = new VBox(10,
                new Label("手牌"),
                handPane,
                new Label("桌面弃牌"),
                discardPane,
                new Label("战况"),
                logArea);
        centerBox.setPrefHeight(500);

        HBox actionBar = buildActionBar();

        BorderPane root = new BorderPane();
        root.setTop(statusLabel);
        root.setCenter(centerBox);
        root.setBottom(actionBar);
        BorderPane.setMargin(centerBox, new javafx.geometry.Insets(10));

        Scene scene = new Scene(root, 900, 600);
        stage.setScene(scene);
        stage.setTitle("卡五星 - JavaFX 版");
    }

    private HBox buildActionBar() {
        huButton = new Button("胡");
        huButton.setOnAction(e -> handleHuSelection(true));

        pengButton = new Button("碰");
        pengButton.setOnAction(e -> handleReactionSelection(ReactionType.PENG));

        gangButton = new Button("杠");
        gangButton.setOnAction(e -> handleReactionSelection(ReactionType.GANG));

        passButton = new Button("过");
        passButton.setOnAction(e -> {
            if (pendingHuAction != null) {
                handleHuSelection(false);
            } else if (pendingReactionAction != null) {
                handleReactionSelection(ReactionType.NONE);
            }
        });

        HBox box = new HBox(10, huButton, pengButton, gangButton, passButton);
        box.setPadding(new javafx.geometry.Insets(10));
        clearActionButtons();
        return box;
    }

    private void refreshHand() {
        handPane.getChildren().clear();
        List<Card> hand = humanPlayer.getHand();
        List<IndexedCard> sorted = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            sorted.add(new IndexedCard(i, hand.get(i)));
        }
        sorted.sort((a, b) -> compareCards(a.card, b.card));
        for (IndexedCard entry : sorted) {
            Button button = new Button(entry.card.toString() + "\n(" + entry.index + ")");
            button.setDisable(pendingDiscardAction == null);
            button.setOnAction(e -> handleDiscardSelection(entry.index));
            button.getStyleClass().add("card-button");
            handPane.getChildren().add(button);
        }
    }

    private void refreshDiscardPile() {
        discardPane.getChildren().clear();
        for (Card card : game.getDiscardPile()) {
            discardPane.getChildren().add(new Label(card.toString()));
        }
    }

    private void handleDiscardSelection(int index) {
        IntConsumer consumer = pendingDiscardAction;
        if (consumer != null) {
            pendingDiscardAction = null;
            consumer.accept(index);
            refreshHand();
            updateStatus("你打出了索引 " + index + " 的牌。");
        }
    }

    private void handleReactionSelection(ReactionType type) {
        Consumer<ReactionType> consumer = pendingReactionAction;
        if (consumer != null) {
            pendingReactionAction = null;
            clearActionButtons();
            consumer.accept(type);
        }
    }

    private void handleHuSelection(boolean hu) {
        Consumer<Boolean> consumer = pendingHuAction;
        if (consumer != null) {
            pendingHuAction = null;
            clearActionButtons();
            consumer.accept(hu);
        }
    }

    private void appendLog(String message) {
        logArea.appendText(message + System.lineSeparator());
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void clearActionButtons() {
        huButton.setDisable(true);
        pengButton.setDisable(true);
        gangButton.setDisable(true);
        passButton.setDisable(true);
    }

    private void runOnFxAndWait(Runnable runnable) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // GuiInteractionHandler implementations

    @Override
    public void requestDiscardSelection(List<Card> handSnapshot, IntConsumer consumer) {
        Platform.runLater(() -> {
            pendingDiscardAction = consumer;
            statusLabel.setText("请选择一张要打出的牌。");
            refreshHand();
        });
    }

    @Override
    public void requestReaction(Card tile, boolean canPeng, boolean canGang, Consumer<ReactionType> consumer) {
        Platform.runLater(() -> {
            pendingReactionAction = consumer;
            pendingHuAction = null;
            statusLabel.setText("是否对 " + tile + " 进行操作？");
            pengButton.setDisable(!canPeng);
            gangButton.setDisable(!canGang);
            huButton.setDisable(true);
            passButton.setDisable(false);
        });
    }

    @Override
    public void requestSelfHu(List<Card> handSnapshot, int fan, Consumer<Boolean> consumer) {
        Platform.runLater(() -> {
            pendingHuAction = consumer;
            pendingReactionAction = null;
            statusLabel.setText("你可以自摸，番数：" + fan + "，是否胡牌？");
            huButton.setDisable(false);
            pengButton.setDisable(true);
            gangButton.setDisable(true);
            passButton.setDisable(false);
        });
    }

    @Override
    public void requestDiscardHu(Card tile, List<Card> handSnapshot, List<Meld> meldsSnapshot,
                                 List<WinCategory> categories, int fan, Consumer<Boolean> consumer) {
        Platform.runLater(() -> {
            pendingHuAction = consumer;
            pendingReactionAction = null;
            String categoryText = categories == null || categories.isEmpty()
                    ? "屁胡"
                    : formatCategories(EnumSet.copyOf(categories));
            statusLabel.setText(tile + " 可胡牌，牌型：" + categoryText + "，番数：" + fan);
            huButton.setDisable(false);
            pengButton.setDisable(true);
            gangButton.setDisable(true);
            passButton.setDisable(false);
        });
    }

    @Override
    public void showMessage(String message) {
        updateStatus(message);
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

    private static String formatCategories(EnumSet<WinCategory> categories) {
        List<String> names = new ArrayList<>();
        for (WinCategory category : categories) {
            names.add(categoryName(category));
        }
        return String.join("、", names);
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

    private static final class IndexedCard {
        private final int index;
        private final Card card;

        private IndexedCard(int index, Card card) {
            this.index = index;
            this.card = card;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
