package com.destrostudios.turnbasedgametools.network.shared;

public interface NetworkRandom {
    int nextInt(int maxExclusive);

    default int nextInt(int minInclusive, int maxExclusive) {
        return minInclusive + nextInt(maxExclusive - minInclusive);
    }
}
