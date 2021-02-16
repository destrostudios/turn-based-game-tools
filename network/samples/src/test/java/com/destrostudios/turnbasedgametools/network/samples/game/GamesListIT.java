package com.destrostudios.turnbasedgametools.network.samples.game;

import com.destrostudios.turnbasedgametools.network.BlockingMessageModule;
import com.destrostudios.turnbasedgametools.network.client.ToolsClient;
import com.destrostudios.turnbasedgametools.network.client.modules.game.GameStartClientModule;
import com.destrostudios.turnbasedgametools.network.client.modules.game.LobbyClientModule;
import com.destrostudios.turnbasedgametools.network.samples.game.connect4.Connect4StartInfo;
import com.destrostudios.turnbasedgametools.network.server.ToolsServer;
import com.destrostudios.turnbasedgametools.network.server.modules.game.GameStartServerModule;
import com.destrostudios.turnbasedgametools.network.server.modules.game.LobbyServerModule;
import com.destrostudios.turnbasedgametools.network.shared.NetworkUtil;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.ListGame;
import com.destrostudios.turnbasedgametools.network.shared.modules.game.messages.UnlistGame;
import com.destrostudios.turnbasedgametools.network.shared.modules.ping.PingModule;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GamesListIT {

    private ToolsServer server;
    private ToolsClient client;

    @Before
    public void setup() throws IOException {
        Server kryoServer = new Server();
        Consumer<Kryo> registerParams = kryo -> kryo.register(Connect4StartInfo.class);
        LobbyServerModule<Connect4StartInfo> lobbyServerModule = new LobbyServerModule<>(registerParams, kryoServer::getConnections);
        GameStartServerModule<Connect4StartInfo> gameStartServerModule = new GameStartServerModule<>(registerParams) {
            @Override
            public void startGameRequest(Connection connection, Connect4StartInfo params) {
                lobbyServerModule.listGame(UUID.randomUUID(), params);
            }
        };


        server = new ToolsServer(kryoServer, lobbyServerModule, gameStartServerModule, new PingModule());
        server.start(NetworkUtil.PORT);

        Client kryoClient = new Client();
        client = new ToolsClient(kryoClient, new LobbyClientModule<>(registerParams, kryoClient), new GameStartClientModule<>(registerParams, kryoClient), new PingModule(), new BlockingMessageModule());
        client.start(1000, "localhost", NetworkUtil.PORT);
    }

    @After
    public void cleanup() {
        client.stop();
        server.stop();

        server = null;
        client = null;
    }

    @Test(timeout = 1000)
    public void sampleGame() throws InterruptedException {
        LobbyClientModule<Connect4StartInfo> lobbyClient = client.getModule(LobbyClientModule.class);
        GameStartClientModule<Connect4StartInfo> startClient = client.getModule(GameStartClientModule.class);
        BlockingMessageModule block = client.getModule(BlockingMessageModule.class);

        startClient.startNewGame(new Connect4StartInfo());
        lobbyClient.subscribeToGamesList();
        block.takeUntil(ListGame.class);
        assertEquals(1, lobbyClient.getListedGames().size());

        startClient.startNewGame(new Connect4StartInfo());
        block.takeUntil(ListGame.class);
        assertEquals(2, lobbyClient.getListedGames().size());

        lobbyClient.unsubscribeFromGamesList();
        block.takeUntil(UnlistGame.class);
        block.takeUntil(UnlistGame.class);
        assertEquals(0, lobbyClient.getListedGames().size());
    }

}
