package com.destrostudios.turnbasedgametools.network.client.modules.game;

import java.util.Arrays;

public class ActionReplay<A> {
    public final A action;
    public final int[] randomHistory;

    public ActionReplay(A action, int[] randomHistory) {
        this.action = action;
        this.randomHistory = randomHistory;
    }

    @Override
    public String toString() {
        return "ActionReplay{" +
                "action=" + action +
                ", randomHistory=" + Arrays.toString(randomHistory) +
                '}';
    }
}
