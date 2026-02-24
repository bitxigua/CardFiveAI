import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 真人玩家，决策由 HumanDecisionProvider 提供。
 */
public class HumanPlayer extends Player {
    private final HumanDecisionProvider decisionProvider;

    public HumanPlayer(String name, GameEngine engine, HumanDecisionProvider decisionProvider) {
        super(name, engine);
        this.decisionProvider = Objects.requireNonNull(decisionProvider, "决策输入不能为空");
    }

    @Override
    public TurnAction playTurn() {
        List<Card> snapshot = snapshotHand();
        List<Card> evaluationTiles = getEvaluationTiles();
        if (engine.checkHu(evaluationTiles)) {
            int fan = engine.calculateFan(evaluationTiles);
            if (decisionProvider.shouldHu(snapshot, fan)) {
                return TurnAction.hu(fan);
            }
        }
        Card discarded = discardOne();
        return TurnAction.discard(discarded);
    }

    @Override
    protected int selectDiscardIndex() {
        return decisionProvider.chooseDiscardIndex(snapshotHand());
    }

    @Override
    public ReactionType chooseReaction(Card tile, boolean canPeng, boolean canGang) {
        return decisionProvider.chooseReaction(tile, snapshotHand(), canPeng, canGang);
    }

    @Override
    public boolean shouldHuOnDiscard(Card tile, EnumSet<WinCategory> categories, int fan) {
        return decisionProvider.shouldHuOnDiscard(tile, snapshotHand(), snapshotMelds(), new ArrayList<>(categories), fan);
    }

    private List<Card> snapshotHand() {
        return Collections.unmodifiableList(new ArrayList<>(hand));
    }

    private List<Meld> snapshotMelds() {
        return Collections.unmodifiableList(new ArrayList<>(melds));
    }
}
