import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
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

    private static final double HAND_TILE_WIDTH = 56;
    private static final double HAND_TILE_HEIGHT = 86;
    private static final double DISCARD_TILE_WIDTH = 44;
    private static final double DISCARD_TILE_HEIGHT = 66;
    private static final Color TILE_IVORY = Color.web("#f8f4e8");
    private static final Color TILE_BORDER = Color.web("#b9ae9a");
    private static final Color DOT_GREEN = Color.web("#1b8e4e");
    private static final Color DOT_BLUE = Color.web("#1f6cb3");
    private static final Color DOT_RED = Color.web("#d6403b");

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
        BorderPane.setMargin(centerBox, new Insets(10));

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
        box.setPadding(new Insets(10));
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
        boolean canDiscard = pendingDiscardAction != null;
        for (IndexedCard entry : sorted) {
            Runnable action = canDiscard ? () -> handleDiscardSelection(entry.index) : null;
            Node tileNode = createTileNode(
                    entry.card,
                    HAND_TILE_WIDTH,
                    HAND_TILE_HEIGHT,
                    action,
                    String.valueOf(entry.index),
                    true,
                    true
            );
            handPane.getChildren().add(tileNode);
        }
    }

    private void refreshDiscardPile() {
        discardPane.getChildren().clear();
        for (Card card : game.getDiscardPile()) {
            Node tileNode = createTileNode(
                    card,
                    DISCARD_TILE_WIDTH,
                    DISCARD_TILE_HEIGHT,
                    null,
                    null,
                    false,
                    false
            );
            discardPane.getChildren().add(tileNode);
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

    private Node createTileNode(Card card, double width, double height, Runnable onClick,
                                String footerText, boolean showShadow, boolean dimWhenDisabled) {
        StackPane tile = new StackPane();
        tile.setPrefSize(width, height);
        tile.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        tile.setStyle("-fx-background-color: transparent;");

        Rectangle base = new Rectangle(width, height);
        double arc = Math.min(width, height) * 0.3;
        base.setArcWidth(arc);
        base.setArcHeight(arc);
        base.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffffff")),
                new Stop(1, Color.web("#e5dcc3"))));
        base.setStroke(Color.web("#c2c7cc"));
        base.setStrokeWidth(1.2);

        if (showShadow) {
            DropShadow shadow = new DropShadow();
            shadow.setRadius(6);
            shadow.setOffsetY(2);
            shadow.setColor(Color.color(0, 0, 0, 0.2));
            tile.setEffect(shadow);
        }

        double bottomPadding = footerText == null ? 8 : 20;
        double artWidth = Math.max(20, width - 16);
        double artHeight = Math.max(20, height - (8 + bottomPadding));
        Canvas canvas = new Canvas(artWidth, artHeight);
        drawCardArt(card, canvas.getGraphicsContext2D(), artWidth, artHeight);
        StackPane artHolder = new StackPane(canvas);
        artHolder.setAlignment(Pos.CENTER);
        artHolder.setPadding(new Insets(8, 8, bottomPadding, 8));
        artHolder.setMouseTransparent(true);

        tile.getChildren().addAll(base, artHolder);

        if (footerText != null) {
            Label label = new Label(footerText);
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
            label.setMouseTransparent(true);
            StackPane.setAlignment(label, Pos.BOTTOM_CENTER);
            StackPane.setMargin(label, new Insets(0, 0, 4, 0));
            tile.getChildren().add(label);
        }

        if (onClick != null) {
            tile.setCursor(Cursor.HAND);
            tile.setOnMouseClicked(e -> onClick.run());
            tile.setOnMouseEntered(e -> {
                tile.setScaleX(1.05);
                tile.setScaleY(1.05);
            });
            tile.setOnMouseExited(e -> {
                tile.setScaleX(1.0);
                tile.setScaleY(1.0);
            });
        } else if (dimWhenDisabled) {
            tile.setOpacity(0.55);
        }

        return tile;
    }

    private void drawCardArt(Card card, GraphicsContext gc, double width, double height) {
        drawTileBackground(gc, width, height);

        if (card.isHonor()) {
            drawHonor(gc, card.getHonor(), width, height);
        } else if (card.getSuit() == Card.Suit.TIAO) {
            drawTiao(gc, card.getRank(), width, height);
        } else {
            drawDotTile(gc, card.getRank(), width, height);
        }
    }

    private void drawTileBackground(GraphicsContext gc, double width, double height) {
        double arc = Math.min(width, height) * 0.35;
        gc.setFill(TILE_IVORY);
        gc.fillRoundRect(0, 0, width, height, arc, arc);

        LinearGradient highlight = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(1, 1, 1, 0.85)),
                new Stop(0.4, Color.color(1, 1, 1, 0.2)),
                new Stop(1, Color.color(1, 1, 1, 0)));
        gc.setFill(highlight);
        gc.fillRoundRect(1.5, 1.5, width - 3, height - 3, arc - 3, arc - 3);

        LinearGradient shade = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0, 0, 0, 0)),
                new Stop(1, Color.color(0, 0, 0, 0.08)));
        gc.setStroke(shade);
        gc.setLineWidth(3);
        gc.strokeRoundRect(1.5, 1.5, width - 3, height - 3, arc - 3, arc - 3);

        gc.setStroke(TILE_BORDER);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(0.6, 0.6, width - 1.2, height - 1.2, arc - 1.2, arc - 1.2);

        gc.setStroke(Color.color(1, 1, 1, 0.05));
        for (double y = 6; y < height - 6; y += 12) {
            gc.strokeLine(6, y, width - 6, y);
        }
    }

    private void drawDotTile(GraphicsContext gc, int rank, double width, double height) {
        List<DotSpec> specs = dotLayout(rank);
        double baseRadius = Math.min(width, height) * 0.11;
        for (DotSpec spec : specs) {
            double radius = baseRadius * spec.sizeMultiplier;
            drawDot(gc, width * spec.normX, height * spec.normY, radius, spec.color, spec.hasInnerRed);
        }
    }

    private void drawDot(GraphicsContext gc, double centerX, double centerY, double radius,
                         Color color, boolean hasInnerRed) {
        RadialGradient fill = new RadialGradient(-45, 0.3, centerX - radius * 0.2,
                centerY - radius * 0.2, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0, color.brighter().desaturate().interpolate(Color.WHITE, 0.25)),
                new Stop(0.6, color),
                new Stop(1, color.darker()));
        gc.setFill(fill);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        gc.setStroke(color.darker().darker());
        gc.setLineWidth(Math.max(2, radius * 0.15));
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        gc.setFill(Color.color(1, 1, 1, 0.4));
        gc.fillOval(centerX - radius * 0.65, centerY - radius * 0.9,
                radius * 0.9, radius * 0.6);

        if (hasInnerRed) {
            double innerRadius = radius * 0.35;
            drawDot(gc, centerX, centerY, innerRadius, DOT_RED, false);
        }
    }

    private List<DotSpec> dotLayout(int rank) {
        List<DotSpec> specs = new ArrayList<>();
        double left = 0.3;
        double right = 0.7;
        double top = 0.3;
        double bottom = 0.7;
        double middle = 0.5;
        double quarterX = 0.2;
        double threeQuarterX = 0.8;
        double upperBand = 0.28;
        double lowerBand = 0.72;

        switch (rank) {
            case 1:
                specs.add(new DotSpec(0.5, 0.5, DOT_BLUE, true, 1.3));
                break;
            case 2:
                specs.add(new DotSpec(0.5, top, DOT_GREEN));
                specs.add(new DotSpec(0.5, bottom, DOT_GREEN));
                break;
            case 3:
                specs.add(new DotSpec(left, bottom, DOT_GREEN));
                specs.add(new DotSpec(0.5, middle, DOT_GREEN));
                specs.add(new DotSpec(right, top, DOT_GREEN));
                break;
            case 4:
                addCornerDots(specs, left, right, top, bottom, DOT_GREEN);
                break;
            case 5:
                addCornerDots(specs, left, right, top, bottom, DOT_GREEN);
                specs.add(new DotSpec(0.5, middle, DOT_RED));
                break;
            case 6:
                specs.add(new DotSpec(0.4, upperBand, DOT_BLUE, false, 1.05));
                specs.add(new DotSpec(0.6, upperBand, DOT_BLUE, false, 1.05));
                specs.add(new DotSpec(quarterX, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(0.4, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(0.6, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(threeQuarterX, lowerBand, DOT_GREEN));
                break;
            case 7:
                specs.add(new DotSpec(quarterX, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(0.4, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(0.6, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(threeQuarterX, lowerBand, DOT_GREEN));
                specs.add(new DotSpec(0.3, 0.4, DOT_BLUE, false, 1.05));
                specs.add(new DotSpec(0.5, 0.28, DOT_BLUE, false, 1.05));
                specs.add(new DotSpec(0.7, 0.4, DOT_BLUE, false, 1.05));
                break;
            case 8:
                double topRowY = 0.32;
                double bottomRowY = 0.68;
                specs.add(new DotSpec(quarterX, topRowY, DOT_BLUE));
                specs.add(new DotSpec(0.4, topRowY, DOT_BLUE));
                specs.add(new DotSpec(0.6, topRowY, DOT_BLUE));
                specs.add(new DotSpec(threeQuarterX, topRowY, DOT_BLUE));
                specs.add(new DotSpec(quarterX, bottomRowY, DOT_GREEN));
                specs.add(new DotSpec(0.4, bottomRowY, DOT_GREEN));
                specs.add(new DotSpec(0.6, bottomRowY, DOT_GREEN));
                specs.add(new DotSpec(threeQuarterX, bottomRowY, DOT_GREEN));
                break;
            case 9:
                double[] positions = {0.28, 0.5, 0.72};
                for (int row = 0; row < 3; row++) {
                    double y = 0.25 + row * 0.25;
                    for (int col = 0; col < 3; col++) {
                        double x = positions[col];
                        Color color = (row == 1) ? DOT_BLUE : DOT_GREEN;
                        boolean centerRed = row == 1 && col == 1;
                        specs.add(new DotSpec(x, y, centerRed ? DOT_RED : color));
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported dot rank: " + rank);
        }
        return specs;
    }

    private void addCornerDots(List<DotSpec> specs, double left, double right,
                               double top, double bottom, Color color) {
        specs.add(new DotSpec(left, top, color));
        specs.add(new DotSpec(right, top, color));
        specs.add(new DotSpec(left, bottom, color));
        specs.add(new DotSpec(right, bottom, color));
    }

    private void drawTiao(GraphicsContext gc, int rank, double width, double height) {
        double availableHeight = height - 10;
        double spacing = availableHeight / rank;
        double stripeHeight = Math.max(4, spacing - 3);
        double stripeWidth = Math.min(width * 0.35, 14);
        double x = width / 2 - stripeWidth / 2;

        for (int i = 0; i < rank; i++) {
            double y = 5 + i * spacing + (spacing - stripeHeight) / 2;
            gc.setFill(Color.web("#2e8b57"));
            gc.fillRoundRect(x, y, stripeWidth, stripeHeight, 6, 6);
            gc.setStroke(Color.web("#135436"));
            gc.setLineWidth(1);
            gc.strokeRoundRect(x, y, stripeWidth, stripeHeight, 6, 6);

            double nodeDiameter = stripeWidth * 0.35;
            gc.setFill(Color.web("#f4d35e"));
            gc.fillOval(x + stripeWidth / 2 - nodeDiameter / 2,
                    y + stripeHeight / 2 - nodeDiameter / 2,
                    nodeDiameter,
                    nodeDiameter);
        }
    }

    private void drawHonor(GraphicsContext gc, Card.Honor honor, double width, double height) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        double baseSize = Math.min(width, height);
        switch (honor) {
            case HONG_ZHONG:
                gc.setFont(Font.font("System", FontWeight.BOLD, baseSize * 0.6));
                gc.setFill(Color.web("#c62828"));
                gc.fillText("中", width / 2, height / 2);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.2);
                gc.strokeText("中", width / 2, height / 2);
                break;
            case FA_CAI:
                gc.setFont(Font.font("System", FontWeight.BOLD, baseSize * 0.55));
                gc.setFill(Color.web("#1b5e20"));
                gc.fillText("发", width / 2, height / 2);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.2);
                gc.strokeText("发", width / 2, height / 2);
                break;
            case BAI_BAN:
                double margin = baseSize * 0.18;
                gc.setStroke(Color.web("#1565c0"));
                gc.setLineWidth(2.4);
                gc.strokeRoundRect(margin, margin,
                        width - margin * 2,
                        height - margin * 2,
                        12,
                        12);
                gc.setFont(Font.font("System", FontWeight.BOLD, baseSize * 0.38));
                gc.setFill(Color.web("#1565c0"));
                gc.fillText("白", width / 2, height / 2 + baseSize * 0.02);
                break;
            default:
                break;
        }
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

    private static final class DotSpec {
        private final double normX;
        private final double normY;
        private final Color color;
        private final boolean hasInnerRed;
        private final double sizeMultiplier;

        private DotSpec(double normX, double normY, Color color) {
            this(normX, normY, color, false, 1.0);
        }

        private DotSpec(double normX, double normY, Color color, boolean hasInnerRed, double sizeMultiplier) {
            this.normX = normX;
            this.normY = normY;
            this.color = color;
            this.hasInnerRed = hasInnerRed;
            this.sizeMultiplier = sizeMultiplier;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
