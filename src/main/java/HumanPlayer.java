import java.util.ArrayList;
import java.util.Collections;
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
        if (engine.checkHu(hand)) {
            int fan = engine.calculateFan(hand);
            if (decisionProvider.shouldHu(snapshot, fan)) {
                return TurnAction.hu(fan);
            }
        }
        int index = decisionProvider.chooseDiscardIndex(snapshot);
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("出牌索引越界: " + index);
        }
        Card discarded = hand.remove(index);
        return TurnAction.discard(discarded);
    }

    private List<Card> snapshotHand() {
        return Collections.unmodifiableList(new ArrayList<>(hand));
    }
}
