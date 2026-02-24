package com.cardfive;

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

    /**
     * 响应其他玩家的弃牌，可选择碰或杠。
     *
     * @param tile    当前可吃碰的牌
     * @param hand    手牌快照
     * @param canPeng 是否可碰
     * @param canGang 是否可杠
     * @return 玩家选择的操作
     */
    ReactionType chooseReaction(Card tile, List<Card> hand, boolean canPeng, boolean canGang);

    /**
     * 是否对他人的弃牌宣告胡牌。
     */
    boolean shouldHuOnDiscard(Card tile, List<Card> hand, List<Meld> melds, List<WinCategory> categories, int fan);
}
