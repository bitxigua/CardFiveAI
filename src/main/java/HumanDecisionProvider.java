import java.util.List;

/**
 * 为真人玩家提供操作输入。
 */
public interface HumanDecisionProvider {
    /**
     * @param hand 当前手牌快照
     * @param fan  预计番数
     * @return 是否决定胡牌
     */
    boolean shouldHu(List<Card> hand, int fan);

    /**
     * 选择要丢弃的牌索引。
     */
    int chooseDiscardIndex(List<Card> hand);
}
