package com.destrostudios.turnbasedgametools.network.samples.connect4;

import com.destrostudios.turnbasedgametools.network.shared.GameService;
import com.destrostudios.turnbasedgametools.network.shared.NetworkRandom;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Connect4Service implements GameService<Connect4Impl, Long> {

    @Override
    public void initialize(Kryo kryo) {
        kryo.register(Connect4Impl.class, new Serializer<Connect4Impl>() {
            @Override
            public void write(Kryo kryo, Output output, Connect4Impl object) {
                output.writeInt(object.width, true);
                output.writeInt(object.height, true);
                output.writeLong(object.own);
                output.writeLong(object.opp);
            }

            @Override
            public Connect4Impl read(Kryo kryo, Input input, Class type) {
                Connect4Impl state = new Connect4Impl(input.readInt(true), input.readInt(true));
                state.own = input.readLong();
                state.opp = input.readLong();
                return state;
            }
        });
    }

    @Override
    public Connect4Impl startNewGame() {
        return new Connect4Impl(7, 6);
    }

    @Override
    public Connect4Impl applyAction(Connect4Impl state, Long action, NetworkRandom random) {
        state.move(action);
        return state;
    }

}
