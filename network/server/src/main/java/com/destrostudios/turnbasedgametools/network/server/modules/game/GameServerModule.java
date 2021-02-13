package com.destrostudios.turnbasedgametools.network.server.modules.game;

import com.destrostudios.turnbasedgametools.network.shared.modules.game.GameModule;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.GameService;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameAction;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameActionRequest;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameJoinAck;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameJoinRequest;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameStartRequest;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Connection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServerModule<S, A> extends GameModule<S, A> {

    private static final Logger LOG = LoggerFactory.getLogger(GameServerModule.class);

    private final Map<UUID, ServerGameData<S, A>> games = new ConcurrentHashMap<>();
    private final Supplier<Connection[]> connectionsSupply;

    public GameServerModule(GameService<S, A> gameService, Supplier<Connection[]> connectionsSupply) {
        super(gameService);
        this.connectionsSupply = connectionsSupply;
    }

    @Override
    public void disconnected(Connection connection) {
        removeSpectator(connection.getID());
    }

    @Override
    public void received(Connection connection, Object object) {
        try {
            handleReceivedMessage(connection, object);
        } catch (Throwable t) {
            LOG.error("Exception when handling received message {} from connection {}.", object, connection.getID(), t);
        }
    }

    private void handleReceivedMessage(Connection connection, Object object) {
        if (object instanceof GameJoinRequest) {
            GameJoinRequest message = (GameJoinRequest) object;
            join(connection, message.gameId);
        } else if (object instanceof GameActionRequest) {
            GameActionRequest message = (GameActionRequest) object;
            applyAction(message.game, (A) message.action);
        } else if (object instanceof GameStartRequest) {
            UUID gameId = startNewGame();
            join(connection, gameId);
        }
    }

    public UUID startNewGame() {
        UUID id = UUID.randomUUID();
        S state = gameService.startNewGame();
        games.put(id, new ServerGameData<>(id, state, new SecureRandom()));
        return id;
    }

    public void join(Connection connection, UUID gameId) {
        ServerGameData<S, A> game = games.get(gameId);
        game.addConnection(connection.getID());
        connection.sendTCP(new GameJoinAck(game.id, game.state));
    }

    public void applyAction(UUID gameId, A action) {
        ServerGameData<S, A> game = games.get(gameId);
        MasterRandom random = new MasterRandom(game.random);

        ByteArrayOutputStream backupOutputStream = new ByteArrayOutputStream();
        Kryo kryo = new Kryo();
        gameService.initialize(kryo);
        try (Output output = new Output(backupOutputStream)) {
            kryo.writeObject(output, game.state);
            output.flush();
        }
        try {
            game.state = gameService.applyAction(game.state, action, random);
        } catch (Throwable t) {
            LOG.warn("Game {} was rolled back due to an exception when trying to apply {}.", gameId, action);
            try (Input input = new Input(new ByteArrayInputStream(backupOutputStream.toByteArray()))) {
                game.state = kryo.readObject(input, (Class<S>) game.state.getClass());
            }
            throw t;
        }
        int[] randomHistory = random.getHistory();

        for (Connection other : connectionsSupply.get()) {
            if (game.hasConnection(other.getID())) {
                other.sendTCP(new GameAction(game.id, action, randomHistory));
            }
        }
    }

    public void removeSpectator(int connectionId) {
        for (ServerGameData<S, A> game : games.values()) {
            game.removeConnection(connectionId);
        }
    }

    public ServerGameData<S, A> getGame(UUID gameId) {
        return games.get(gameId);
    }

    public List<ServerGameData<S, A>> getGames() {
        return List.copyOf(games.values());
    }

}