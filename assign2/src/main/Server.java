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

    /**
     * Map of players that have disconnected from the server, so they can be removed from the waiting list
     * but still be able to reconnect to it's position in the waiting queue
     * ---
     * Key: Player's Universal Token
     * Value: Player's position in the waiting queue
     */
    public static ConcurrentMap<String, Integer> disconnectedPlayers = new ConcurrentMap<>();

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

        if (MAX_GAMES > 5) {
            System.out.println("Invalid arguments. Maximum number of game rooms cannot be greater than 5");
            System.exit(1);
        }

        if (PLAYERS_PER_GAME > 8) {
            System.out.println("Invalid arguments. Maximum number of players per game cannot be greater than 8");
            System.exit(1);
        }

        if (NUMBER_OF_ROUNDS > 8) {
            System.out.println("Invalid arguments. Maximum number of rounds per game cannot be greater than 10");
            System.exit(1);
        }

        // LIST OF PLAYERS THAT ARE WAITING FOR A GAME
        waitingPlayers = new ArrayList<>();
        // CONCURRENT LIST OF ACTIVE GAMES
        ConcurrentArrayList<TriviaGame> activeGames = new ConcurrentArrayList<>();
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

        System.out.println("Server started on port " + PORT + " in " + (isRankedMode ? "ranked" : "unranked") + " mode");

        Map<SocketChannel, ByteBuffer> bufferMap = new HashMap<>();

        while (true) {

            System.out.println("\n--------------------------------------------");
            System.out.println("Waiting players: " + waitingPlayers.size());
            System.out.println("Active games: " + activeGames.size());
            System.out.println("--------------------------------------------\n");

            if (activeGames.size() < MAX_GAMES) {

                if (isRankedMode) {

                    List<Player> players;

                    if ((players = (matchmakingAlgorithm(waitingPlayers))) != null) {
                        TriviaGame game = new TriviaGame(NUMBER_OF_ROUNDS, players);
                        activeGames.add(game);
                        threadPool.execute(game);
                    }
                }
                else {
                    if (waitingPlayers.size() >= PLAYERS_PER_GAME) {
                        List<Player> players = new ArrayList<>();
                        for (int i = 0; i < PLAYERS_PER_GAME; i++) {
                            players.add(waitingPlayers.remove(0));
                        }
                        TriviaGame game = new TriviaGame(NUMBER_OF_ROUNDS, players);
                        activeGames.add(game);
                        threadPool.execute(game);
                    }
                }
            }

            // HANDLE GAMES THAT ARE ALREADY OVER
            Iterator<TriviaGame> gameIterator = activeGames.getList().iterator();
            while (gameIterator.hasNext()) {
                TriviaGame game = gameIterator.next();
                if (game.isGameOver()) {
                    for (Player player : game.getUserSockets()) {
                        lock.lock();
                        try {
                            waitingPlayers.add(player);
                            Utils.sendMessage(player.getSocketChannel(), "Game over! You have been added to the waiting list.");
                        } finally {
                            lock.unlock();
                        }
                    }
                    gameIterator.remove();
                }
            }

            int readyChannels = selector.select(TIMEOUT);
            if (readyChannels == 0) continue;

            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while (keyIterator.hasNext()) {

                SelectionKey key = keyIterator.next();

                if (key.isAcceptable()) {

                    SocketChannel clientSocketChannel = serverSocketChannel.accept();
                    clientSocketChannel.configureBlocking(false);
                    clientSocketChannel.register(selector, SelectionKey.OP_READ);
                    bufferMap.put(clientSocketChannel, ByteBuffer.allocate(BUFFER_SIZE));
                    System.out.println("Client connected: " + clientSocketChannel.getRemoteAddress());

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

                    // Split the message from the Client and trim it
                    String message = new String(bytes);

                    List<String> messageParts = Arrays.asList(message.trim().split("\\s+"));

                    if (messageParts.get(0).equals("register") && messageParts.size() == 3) {
                        handleRegistration(clientSocketChannel, messageParts.get(1), messageParts.get(2));
                        clientSocketChannel.close();
                    } else if (messageParts.get(0).equals("login") && messageParts.size() == 3) {
                        handleAuthenticate(clientSocketChannel, messageParts.get(1), messageParts.get(2));
                    }
                }
                keyIterator.remove();
            }
        }
    }

    private static void handleDisconnect(SocketChannel clientSocketChannel, Map<SocketChannel, ByteBuffer> bufferMap, SelectionKey key) throws IOException {

        System.out.println("Client disconnected: " + clientSocketChannel.getRemoteAddress());
        bufferMap.remove(clientSocketChannel);
        // FIND THE PLAYER ON THE WAITING LIST BASED ON ITS CLIENT SOCKET CHANNEL
        Player player = waitingPlayers.stream().filter(p -> p.getSocketChannel().equals(clientSocketChannel)).findFirst().orElse(null);
        // FIND INDEX OF PLAYER ON THE WAITING LIST
        int index = waitingPlayers.indexOf(player);

        // REMOVE PLAYER FROM THE WAITING LIST
        waitingPlayers.remove(player);
        assert player != null;

        // SAVE ITS TOKEN ON THE DISCONNECTED PLAYERS MAP, AS WELL AS ITS CURRENT INDEX
        disconnectedPlayers.put(player.getToken(), index);

        // CLOSE THE SOCKET CHANNEL AND CANCEL THE KEY
        clientSocketChannel.close();
        key.cancel();
    }

    private static void handleAuthenticate(SocketChannel socket, String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader("main/data/users.txt"))) {
            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(":");

                // CHECK IF THE USERNAME EXISTS
                if (!parts[0].equals(username)) continue;
                else {
                    // CHECK IF THE PASSWORD MATCHES
                    if (parts[1].equals(password)) {

                        System.out.println("Authentication Successful for username: " + username);
                        Utils.sendMessage(socket, "Authentication Successful for username: " + username);
                        Player player = new Player(username, socket, parts[3], Integer.parseInt(parts[2]));

                        // IF THE PLAYER IS ON THE DISCONNECTED PLAYERS MAP, RESTORE IT TO ITS ORIGINAL POSITION
                        if (disconnectedPlayers.containsKey(player.getToken())) {
                            int index = disconnectedPlayers.get(player.getToken());

                            // IF THE INDEX IS OUT OF BOUNDS, ADD IT TO THE END OF THE LIST
                            try {
                                waitingPlayers.add(index, player);
                            } catch (IndexOutOfBoundsException e) {
                                waitingPlayers.add(player);
                            }

                            // REMOVE IT FROM THE DISCONNECTED PLAYERS MAP
                            disconnectedPlayers.remove(player.getToken());

                            Utils.sendMessage(socket, "\nYour position in the waiting queue has been restored: " + (index + 1) + ".");
                            break;
                        }
                        waitingPlayers.add(player);
                        Utils.sendMessage(socket, "\nYou have been added to the waiting list, please wait...");
                        break;
                    } else {
                        System.out.println("Authentication Failed for username: " + username);
                        Utils.sendMessage(socket, "Authentication Failed for username: " + username + ". Incorrect password.");
                    }
                }

                System.out.println("Authentication Failed for username: " + username);
                Utils.sendMessage(socket, "Authentication failed for username: " + username + ". Username does not exist. --(kill)\n");
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
                    Utils.sendMessage(socket, "Registration failed. Username already exists: " + username);
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
            Utils.sendMessage(socket, "Registration successful for username: " + username);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Player> matchmakingAlgorithm(List<Player> waitingPlayers) {

        // TEMPORARY DATA STRUCTURE TO SUPPORT MATCHMAKING
        List<Player> players = new ArrayList<>();
        long now = System.currentTimeMillis();

        /* create games according to player.getRank() and the maximum number of players per game */
        for (int i = 0; i < waitingPlayers.size(); i++) {

            Player curr_player = waitingPlayers.get(i);

            players.clear();
            players.add(curr_player);

            long waitTime = now - curr_player.getLastQueuedTime();

            if (waitTime >= WAIT_TIME_MATCHMAKING_THRESHOLD) {
                Utils.sendMessage(curr_player.getSocketChannel(), "You have been waiting for a long time, expanding your rank range...");
                curr_player.incrementCurrentRankRange(CURRENT_RANKING_DIFFERENCE_THRESHOLD);
            }

            for (int j = i + 1; j < waitingPlayers.size(); j++) {

                // CHECK IF THE CURRENT PLAYER WE'RE TRYING TO MATCH RANK DIFFERENCE TO THIS PLAYER IS GREATER THAN IT'S CURRENT RANK THRESHOLD
                if (Math.abs(curr_player.getRank() - waitingPlayers.get(j).getRank()) <= curr_player.getCurrentRankRange()) {

                    players.add(waitingPlayers.get(j));

                    // MEANING A GAME IS READY TO BE CREATED
                    if (players.size() == PLAYERS_PER_GAME) {

                        // REMOVE PLAYERS FROM THE WAITING LIST AND SET THEIR RELATED DEFAULT VALUES
                        for (Player player : players) {
                            waitingPlayers.remove(player);
                            player.setLastQueuedTime(now);
                            player.setDefaultRankRange();
                        }

                        System.out.println("Matchmaking successful!");
                        return players;
                    }
                }
            }
        }

        // THERE WERE ARE NOT ENOUGH PLAYERS TO CREATE A GAME
        return null;
    }
}