package main;

import main.DataStructures.ConcurrentArrayList;
import main.DataStructures.ConcurrentMap;
import main.Game.TriviaGame;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static final ReentrantLock lock = new ReentrantLock();
    private static final int PORT = 1234;
    private static int PLAYERS_PER_GAME = 2;
    private static final int TIMEOUT = 5000;
    public static final int BUFFER_SIZE = 1024;
    public static int WAIT_TIME_MATCHMAKING_THRESHOLD = 10000; // 10 SECONDS
    public static int CURRENT_RANKING_DIFFERENCE_THRESHOLD = 50;
    private static List<Player> waitingPlayers;

    // CONCURRENT LIST OF ACTIVE GAMES AND THEIR UNIQUE IDENTIFIER
    static ConcurrentMap<TriviaGame, String> activeGames = new ConcurrentMap<>();
    /**
     * Map of players that have disconnected from the server, so they can be removed from the waiting list
     * but still be able to reconnect to it's position in the waiting queue
     * ---
     * Key: Player's Universal Token
     * Value: Player's position in the waiting queue
     */
    public static ConcurrentMap<String, Integer> disconnectedPlayers = new ConcurrentMap<>();
    /**
     * Map of players that have disconnected from the game, so they can be reconnected to it
     * ---
     * Key: Player's Universal Token
     * Value: Game's Unique Identifier
     */
    public static ConcurrentMap<String, String> disconnected_from_game = new ConcurrentMap<>();

    // THREAD TO PERIODICALLY PRINT INFORMATION ABOUT THE SERVER STATUS
    static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();


    public static void main(String[] args) throws IOException {

        if (!args[0].equals("ranked") && !args[0].equals("unranked")) {
            System.out.println("Invalid mode. Please specify a mode (ranked/unranked)");
            System.exit(1);
        }

        try {

            Integer.parseInt(args[1]);
            Integer.parseInt(args[2]);
            Integer.parseInt(args[3]);

        } catch (NumberFormatException e) {
            System.out.println("Invalid arguments. Please specify a valid maximum number of game rooms and players per game");
            System.exit(1);
        }

        int MAX_GAMES = Integer.parseInt(args[1]);
        PLAYERS_PER_GAME = Integer.parseInt(args[2]);
        int NUMBER_OF_ROUNDS = Integer.parseInt(args[3]);

        if (MAX_GAMES < 1 || PLAYERS_PER_GAME < 1 || NUMBER_OF_ROUNDS < 1) {
            System.out.println("Invalid arguments. Please specify a valid maximum number of game rooms and players per game");
            System.exit(1);
        }

        if (MAX_GAMES > 8) {
            System.out.println("Invalid arguments. Maximum number of game rooms cannot be greater than 8");
            System.exit(1);
        }

        if (PLAYERS_PER_GAME > 8) {
            System.out.println("Invalid arguments. Maximum number of players per game cannot be greater than 8");
            System.exit(1);
        }

        if (NUMBER_OF_ROUNDS > 8) {
            System.out.println("Invalid arguments. Maximum number of rounds per game cannot be greater than 8");
            System.exit(1);
        }

        // LIST OF PLAYERS THAT ARE WAITING FOR A GAME
        waitingPlayers = new ArrayList<>();
        // THREAD POOL FOR ACTIVE GAMES, WITH A MAXIMUM OF **MAX_GAMES** THREADS
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_GAMES);
        // CHECK IF SERVER IS IN RANKED MODE, DEFAULT IS UNRANKED
        boolean isRankedMode = args[0].equals("ranked");

        // OPEN SELECTOR AND SERVER SOCKET CHANNEL
        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

        // BIND SERVER SOCKET CHANNEL TO PORT AND CONFIGURE IT TO BE NON-BLOCKING
        serverSocketChannel.bind(new InetSocketAddress(PORT));
        serverSocketChannel.configureBlocking(false);

        // REGISTER SERVER SOCKET CHANNEL TO ACCEPT CONNECTIONS
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println(">> Server started on port " + PORT + " in " + (isRankedMode ? "ranked" : "unranked") + " mode");

        // MAP OF SOCKETS AND THEIR RESPECTIVE BUFFERS
        Map<SocketChannel, ByteBuffer> bufferMap = new HashMap<>();

        // THREAD TO PERIODICALLY PRINT INFORMATION ABOUT THE SERVER STATUS
        Runnable task = () -> {
            System.out.println("\n--------------------------------------------");
            System.out.println("Waiting players: " + waitingPlayers.size());
            System.out.println("Active games: " + activeGames.size());
            System.out.println("--------------------------------------------\n");

            System.out.println("Waiting players:");
            for (int i = 0; i < waitingPlayers.size(); i++) {
                System.out.println("[" + i + "] " + waitingPlayers.get(i).getUserName());
            }
        };

        // SCHEDULE THE TASK TO RUN EVERY 10 SECONDS
        executor.scheduleAtFixedRate(task, 0, 10, TimeUnit.SECONDS);

        while (true) {

            // CHECK THERE IS A ROOM AVAILABLE FOR A NEW GAME
            if (activeGames.size() < MAX_GAMES) {

                if (isRankedMode) {

                    ConcurrentArrayList<Player> players;

                    // IF THE METHOD DOESN'T RETURN NULL, IT MEANS IT WAS SUCCESSFUL
                    if ((players = (matchmakingAlgorithm(waitingPlayers))) != null) {
                        String uuid = UUID.randomUUID().toString();
                        System.out.println("NUMBER OF ROUNDS: " + NUMBER_OF_ROUNDS);
                        TriviaGame game = new TriviaGame(NUMBER_OF_ROUNDS, players, uuid);
                        // SET EVERY PLAYER IN GAME
                        players.getList().forEach(player -> player.associateGameUUID_andInGame(uuid));
                        activeGames.put(game, uuid);
                        threadPool.execute(game);
                    }
                } else {
                    if (waitingPlayers.size() >= PLAYERS_PER_GAME) {
                        ConcurrentArrayList<Player> players = new ConcurrentArrayList<>();

                        // ADD PLAYERS FROM THE WAITING LIST TO THE GAME
                        for (int i = 0; i < PLAYERS_PER_GAME; i++) {
                            players.add(waitingPlayers.remove(0));
                            players.getList().get(i).setIsInGame(true);
                        }

                        String uuid = UUID.randomUUID().toString();

                        TriviaGame game = new TriviaGame(NUMBER_OF_ROUNDS, players, uuid);
                        activeGames.put(game, uuid);
                        threadPool.execute(game);
                    }
                }
            }

            // HANDLE GAMES THAT ARE ALREADY OVER
            Iterator<TriviaGame> gameIterator = activeGames.keySet().iterator();
            while (gameIterator.hasNext()) {
                TriviaGame game = gameIterator.next();
                if (game.isGameOver()) {
                    System.out.println("> Removed game room with UUID: " + activeGames.get(game) + " -> Reason: Game over");
                    for (Player player : game.getUserSockets()) {
                        lock.lock();
                        try {
                            waitingPlayers.add(player);
                            player.setIsInGame(false);
                            Utils.sendMessage(player.getSocketChannel(), "Game over! You have been added to the waiting list.", "server");
                        } finally {
                            lock.unlock();
                        }
                    }
                    gameIterator.remove();
                } else if (game.roomEmpty()) {
                    System.out.println("> Removed game room with UUID: " + activeGames.get(game) + " -> Reason: Empty Game Room");
                    gameIterator.remove();
                }
            }

            // SELECTOR TIMEOUT
            int readyChannels = selector.select(TIMEOUT);
            if (readyChannels == 0) continue;

            // NIO CHANNELS THAT ARE READY FOR IO OPERATIONS
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            // ITERATE THROUGH READY CHANNELS
            while (keyIterator.hasNext()) {

                SelectionKey key = keyIterator.next();

                // CLIENT SOCKET CHANNEL IS READY TO ACCEPT A NEW CONNECTION
                if (key.isAcceptable()) {

                    SocketChannel clientSocketChannel = serverSocketChannel.accept();
                    clientSocketChannel.configureBlocking(false);
                    clientSocketChannel.register(selector, SelectionKey.OP_READ);
                    bufferMap.put(clientSocketChannel, ByteBuffer.allocate(BUFFER_SIZE));
                    System.out.println("> Client connected: " + clientSocketChannel.getRemoteAddress());

                } else if (key.isReadable()) {

                    SocketChannel clientSocketChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = bufferMap.get(clientSocketChannel);
                    buffer.clear();

                    int bytesRead = clientSocketChannel.read(buffer);

                    if (bytesRead == -1) {
                        handleDisconnect(clientSocketChannel, bufferMap, key);
                        continue;
                    }

                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    // SPLIT MESSAGE INTO PARTS
                    String message = new String(bytes);

                    if (message.isEmpty()) continue;

                    Message msg = Utils.deserializeMessage(message);

                    List<String> messageParts = Arrays.asList(msg.getContent().trim().split("\\s+"));

                    if (messageParts.get(0).equals("register") && messageParts.size() == 3) {
                        handleRegistration(clientSocketChannel, messageParts.get(1), messageParts.get(2));
                        clientSocketChannel.close();
                    } else if (messageParts.get(0).equals("login") && messageParts.size() == 3) {
                        handleAuthenticate(clientSocketChannel, messageParts.get(1), messageParts.get(2));
                    }
                }
                keyIterator.remove();
            }
            executor.shutdown();
        }
    }

    private static void handleDisconnect(SocketChannel clientSocketChannel, Map<SocketChannel, ByteBuffer> bufferMap, SelectionKey key) throws IOException {

        System.out.println("> Client disconnected: " + clientSocketChannel.getRemoteAddress());
        bufferMap.remove(clientSocketChannel);

        Player player = findPlayerByChannel(clientSocketChannel);

        // CHECK IF PLAYER IS IN GAME, SO HE CAN BE ADDED TO THE DISCONNECTED PLAYERS FROM GAMEMAP
        if (player.isInGame()) {
            String current_game_uuid = player.getCurrentGameUUID();
            disconnected_from_game.put(player.token, current_game_uuid);
            TriviaGame game = null;
            for (TriviaGame k : activeGames.keySet()) {
                if (k.getGame_UUID().equals(current_game_uuid)) {
                    game = k;
                    break;
                }
            }
            assert game != null;
            game.updateDisconnectedPlayer_Score_Table(player, game.getScores().get(player));
            game.removePlayer(player);
        }
        else {
            // FIND THE INDEX OF THE PLAYER IN THE WAITING LIST
            int index = waitingPlayers.indexOf(player);
            // REMOVE PLAYER FROM THE WAITING LIST
            waitingPlayers.remove(player);
            // SAVE ITS TOKEN ON THE DISCONNECTED PLAYERS MAP, AS WELL AS ITS CURRENT INDEX
            disconnectedPlayers.put(player.getToken(), index);
        }
        // CLOSE THE SOCKET CHANNEL AND CANCEL THE KEY
        clientSocketChannel.close();
        key.cancel();
    }

    private static void handleAuthenticate(SocketChannel socket, String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader("main/data/users.txt"))) {
            String line;

            // ITERATE THROUGH THE USERS FILE
            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(":");

                // CHECK IF THE USERNAME EXISTS
                if (!parts[0].equals(username)) continue;
                else {
                    // CHECK IF THE PASSWORD MATCHES
                    if (parts[1].equals(password)) {

                        System.out.println("> Authentication Successful for username: " + username);
                        Utils.sendMessage(socket, "> Authentication Successful for username: " + username, "server");
                        Player player = new Player(username, socket, parts[3], Integer.parseInt(parts[2]));

                        // IF THE PLAYER IS ON THE DISCONNECTED PLAYERS MAP, RESTORE IT TO ITS ORIGINAL POSITION
                        if (disconnected_from_game.containsKey(player.getToken())) {
                            String uuid = disconnected_from_game.get(player.getToken());
                            TriviaGame game = activeGames.keySet().stream().filter(g -> g.getGame_UUID().equals(uuid)).findFirst().orElse(null);
                            if (game != null) {
                                Utils.sendMessage(socket, "\nYou have been reconnected to your previous game.\n", "server");
                                System.out.println("> Player " + player.getUserName() + " reconnected to game with UUID: " + uuid);
                                game.addPlayer(player);
                                disconnected_from_game.remove(player.getToken());
                                break;
                            }
                        }
                        else if (disconnectedPlayers.containsKey(player.getToken())) {
                            int index = disconnectedPlayers.get(player.getToken());
                            // IF THE INDEX IS OUT OF BOUNDS, ADD IT TO THE END OF THE LIST
                            try {
                                waitingPlayers.add(index, player);
                            } catch (IndexOutOfBoundsException e) {
                                waitingPlayers.add(player);
                            }

                            // REMOVE IT FROM THE DISCONNECTED PLAYERS MAP
                            disconnectedPlayers.remove(player.getToken());

                            Utils.sendMessage(socket, "\nYour position in the waiting queue has been restored: " + (index + 1) + ".", "server");
                            break;
                        }
                        waitingPlayers.add(player);
                        Utils.sendMessage(socket, "\nYou have been added to the waiting list, please wait...", "server");
                        break;
                    } else {
                        System.out.println("> Authentication Failed for username: " + username);
                        Utils.sendMessage(socket, "Authentication Failed for username: " + username + ". Incorrect password.", "server");
                    }
                }

                System.out.println("> Authentication Failed for username: " + username);
                Utils.sendMessage(socket, "Authentication failed for username: " + username + ". Username does not exist. \n", "server");
                socket.close();
                return;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleRegistration(SocketChannel socket, String username, String password) {

        try (BufferedReader reader = new BufferedReader(new FileReader("main/data/users.txt"))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(":");

                // Check if username already exists
                if (parts[0].equals(username)) {
                    System.out.println("\nRegistration failed. Username already exists: " + username);
                    // Send message to client
                    Utils.sendMessage(socket, "Registration failed. Username already exists: " + username, "registration");
                    socket.close();
                    return;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (PrintWriter out = new PrintWriter(new FileWriter("main/data/users.txt", true))) {
            // GENERATE A RANDOM TOKEN FOR THE USER
            String token = UUID.randomUUID().toString();
            // 1200 IS THE BASE ELO RATING FOR ALL THE PLAYERS
            out.println(username + ":" + password + ":" + 1200 + ":" + token);
            out.flush();
            System.out.println("\nRegistration successful for username: " + username);
            Utils.sendMessage(socket, "Registration successful for username: " + username, "registration");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ConcurrentArrayList<Player> matchmakingAlgorithm(List<Player> waitingPlayers) {

        // TEMPORARY DATA STRUCTURE TO SUPPORT MATCHMAKING
        ConcurrentArrayList<Player> players = new ConcurrentArrayList<>();
        long now = System.currentTimeMillis();

        // CREATE GAMES ACCORDING TO PLAYER.GETRANK() AND THE MAXIMUM NUMBER OF PLAYERS PER GAME
        for (int i = 0; i < waitingPlayers.size(); i++) {

            Player curr_player = waitingPlayers.get(i);

            players.clear();
            players.add(curr_player);

            long waitTime = now - curr_player.getLastQueuedTime();

            if (waitTime >= WAIT_TIME_MATCHMAKING_THRESHOLD) {
                Utils.sendMessage(curr_player.getSocketChannel(), "You have been waiting for a long time, expanding your rank range...", "server");
                curr_player.incrementCurrentRankRange(CURRENT_RANKING_DIFFERENCE_THRESHOLD);
            }

            for (int j = i + 1; j < waitingPlayers.size(); j++) {

                // CHECK IF THE CURRENT PLAYER WE'RE TRYING TO MATCH RANK DIFFERENCE TO THIS PLAYER IS GREATER THAN IT'S CURRENT RANK THRESHOLD
                if (Math.abs(curr_player.getRank() - waitingPlayers.get(j).getRank()) <= curr_player.getCurrentRankRange()) {

                    players.add(waitingPlayers.get(j));

                    // MEANING A GAME IS READY TO BE CREATED
                    if (players.size() == PLAYERS_PER_GAME) {

                        // REMOVE PLAYERS FROM THE WAITING LIST AND SET THEIR RELATED DEFAULT VALUES
                        for (Player player : players.getList()) {
                            waitingPlayers.remove(player);
                            player.setLastQueuedTime(now);
                            player.setDefaultRankRange();
                        }

                        System.out.println("> Matchmaking successful!");
                        return players;
                    }
                }
            }
        }
        // THERE WERE ARE NOT ENOUGH PLAYERS TO CREATE A GAME
        return null;
    }

    public static Player findPlayerByChannel(SocketChannel channel) {
        Player player = waitingPlayers.stream().filter(p -> p.getSocketChannel().equals(channel)).findFirst().orElse(null);
        if (player == null) {
            for (TriviaGame game : activeGames.keySet()) {
                player = game.getUserSockets().stream().filter(p -> p.getSocketChannel().equals(channel)).findFirst().orElse(null);
                if (player != null) {
                    return player;
                }
            }
        }
        return player;
    }
}