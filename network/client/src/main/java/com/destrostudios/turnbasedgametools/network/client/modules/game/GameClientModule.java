package com.destrostudios.turnbasedgametools.network.client.modules.game;

import com.destrostudios.turnbasedgametools.network.shared.modules.game.GameModule;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.GameService;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameAction;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameActionRequest;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameJoinAck;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameJoinRequest;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.GameStartRequest;
import com.esotericsoftware.kryonet.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameClientModule<S, A> extends GameModule<S, A> {

    private static final Logger LOG = LoggerFactory.getLogger(GameClientModule.class);

    private final Connection connection;
    private final Map<UUID, ClientGameData<S, A>> games = new ConcurrentHashMap<>();

    public GameClientModule(GameService<S, A> gameService, Connection connection) {
        super(gameService);
        this.connection = connection;
    }

    @Override
    public void received(Connection connection, Object object) {
        if (object instanceof GameJoinAck) {
            GameJoinAck message = (GameJoinAck) object;
            onJoinGame(message.gameId, (S) message.state);
        } else if (object instanceof GameAction) {
            GameAction message = (GameAction) object;
            onAction(message.gameId, (A) message.action, message.randomHistory);
        }
    }

    private void onJoinGame(UUID gameId, S gameState) {
        games.put(gameId, new ClientGameData<>(gameId, gameState));
    }

    private void onAction(UUID gameId, A action, int[] randomHistory) {
        ClientGameData<S, A> game = games.get(gameId);
        game.enqueueAction(action, randomHistory);
    }

    public void startNewGame() {
        connection.sendTCP(new GameStartRequest());
    }

    public void sendAction(UUID gameId, Object action) {
        connection.sendTCP(new GameActionRequest(gameId, action));
    }

    public void join(UUID gameId) {
        connection.sendTCP(new GameJoinRequest(gameId));
    }

    public boolean applyNextAction(UUID id) {
        ClientGameData<S, A> game = getGame(id);
        if (game.isDesynced()) {
            return false;
        }
        try {
            return game.applyNextAction(gameService);
        } catch (Throwable t) {
            game.setDesynced();
            LOG.error("Game {} is likely desynced. Attempting to rejoin...", game.getId(), t);
            join(game.getId());
            return false;
        }
    }

    public boolean applyAllActions(UUID id) {
        ClientGameData<S, A> game = getGame(id);
        if (game.isDesynced()) {
            return false;
        }
        try {
            boolean updated = false;
            while (game.applyNextAction(gameService)) {
                updated = true;
            }
            return updated;
        } catch (Throwable t) {
            game.setDesynced();
            LOG.error("Game {} is likely desynced. Attempting to rejoin...", game.getId(), t);
            join(game.getId());
            return false;
        }
    }

    public ClientGameData<S, A> getGame(UUID id) {
        return games.get(id);
    }

    public List<ClientGameData<S, A>> getGames() {
        return List.copyOf(games.values());
    }

}