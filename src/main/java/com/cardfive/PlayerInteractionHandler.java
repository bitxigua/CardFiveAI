package com.cardfive;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 处理真人玩家与游戏引擎之间的交互。
 */
public interface PlayerInteractionHandler {
    void requestDiscardSelection(List<Card> handSnapshot, IntConsumer consumer);

    void requestReaction(Card tile, boolean canPeng, boolean canGang, Consumer<ReactionType> consumer);

    void requestSelfHu(List<Card> handSnapshot, int fan, Consumer<Boolean> consumer);

    void requestDiscardHu(Card tile, List<Card> handSnapshot, List<Meld> meldsSnapshot,
                          List<WinCategory> categories, int fan, Consumer<Boolean> consumer);

    void showMessage(String message);
}
