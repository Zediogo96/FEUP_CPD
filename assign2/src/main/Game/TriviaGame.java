package main.Game;

import main.DataStructures.ConcurrentArrayList;
import main.DataStructures.ConcurrentMap;
import main.Player;
import main.Utils;

import java.io.*;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static main.Utils.sendMessage;

public class TriviaGame implements Runnable {
    private static int NUM_ROUNDS = 2;
    private final List<TriviaQuestion> questions;
    private final List<TriviaQuestion> gameQuestions;
    private final ConcurrentArrayList<Player> userSockets;
    private final ConcurrentMap<Player, Integer> scores;
    private final String game_UUID;
    public boolean isGameOver = false;
    public int num_disconnects = 0;
    public int TIMEOUT = 5000; // Timeout value in milliseconds

    public ConcurrentMap<String, Integer> disconnected_players_score;

    public TriviaGame(int number_of_rounds, ConcurrentArrayList<Player> userSockets, String uuid) throws IOException {

        this.questions = loadQuestions();
        this.gameQuestions = getRandomQuestions(NUM_ROUNDS);
        this.userSockets = userSockets;
        this.scores = new ConcurrentMap<>();
        NUM_ROUNDS = number_of_rounds;
        this.game_UUID = uuid;
        this.disconnected_players_score = new ConcurrentMap<>();

        for (Player player : userSockets.getList()) {
            scores.put(player, 0);
        }
    }

    @Override
    public void run() {

        System.out.println("> Sending game start message to all players");
        String initMsg = "Game starting with " + userSockets.size() + " players";

        ConcurrentMap<Socket, Integer> answers = new ConcurrentMap<>();

        for (Player player : userSockets.getList()) {
            sendMessage(player.getSocketChannel(), initMsg);
        }

        for (int i = 0; i < NUM_ROUNDS; i++) {

            TriviaQuestion question = gameQuestions.get(i);

            System.out.println("> Sending question nr. " + (i + 1) + " to all players");

            for (Player player : userSockets.getList()) {
                sendQuestion(player.getSocketChannel(), question);
            }

            int numAnswersReceived = 0;

            long startTime = System.currentTimeMillis();

            while (numAnswersReceived < userSockets.size()) {
                for (Player socket : userSockets.getList()) {
                    try {
                        if (socket.getSocketChannel().socket().getInputStream().available() > 0) {
                            String answer = Utils.receiveMessage(socket.getSocketChannel());
                            answer = answer.replace("\n", "");
                            System.out.println("> Received answer: " + answer + " from " + socket.getUserName());
                            answers.put(socket.getSocketChannel().socket(), Integer.parseInt(answer));
                            numAnswersReceived++;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                // CHECK IF TIMEOUT HAS BEEN REACHED
                long elapsedTime = System.currentTimeMillis() - startTime;

                if (elapsedTime >= TIMEOUT) {
                    System.out.println();
                    for (Player player : userSockets.getList()) {
                        if (!answers.containsKey(player.getSocketChannel().socket())) {
                            sendMessage(player.getSocketChannel(), "You did not answer in time!");
                            answers.put(player.getSocketChannel().socket(), -1);
                        }
                    }
                    System.out.println("> Timeout reached, moving on to next question");
                    break;
                }
            }

            System.out.println("HERE");
            for (Player player : userSockets.getList()) {
                System.out.println("here 2");
                if (Objects.equals(answers.get(player.getSocketChannel().socket()), question.getCorrectAnswerIndex())) {
                    System.out.println("HERE 3");
                    scores.put(player, scores.get(player) + 1);
                    sendMessage(player.getSocketChannel(), "Your was correct!");
                } else {
                    System.out.println("here wrong");
                    sendMessage(player.getSocketChannel(), "Your answer was incorrect! The correct answer was: " + question.getCorrectAnswerIndex());
                }
            }

            StringBuilder scoresMsg = new StringBuilder("Scores:\n");
            for (int j = 0; j < userSockets.size(); j++) {
                scoresMsg.append("Player ").append(j + 1).append(": ").append(scores.get(userSockets.get(j))).append("\n");
            }

            for (Player player : userSockets.getList()) {
                sendMessage(player.getSocketChannel(), scoresMsg.toString());
            }

            answers.clear();
        }

        Player winner = getWinner();

        for (Player player : userSockets.getList()) {

            if (winner == null) sendMessage(player.getSocketChannel(), "It's a tie! Everyone wins!");
            else {
                if (player.equals(winner)) sendMessage(player.getSocketChannel(), "You won!");
                else
                    sendMessage(player.getSocketChannel(), "You lost! :( The winner was " + winner.getUserName() + ".");
            }
            try {
                updatePlayerLevel(player);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.isGameOver = true;
    }

    private void sendQuestion(SocketChannel socket, TriviaQuestion question) {

        String fullQuestion =
                "********************************************************************\n\n" +

                        "Question: " + question.getQuestion() + ";;" +
                        "1: " + question.getAnswers().get(0) + ";" +
                        "2: " + question.getAnswers().get(1) + ";" +
                        "3: " + question.getAnswers().get(2) + ";" +
                        "4: " + question.getAnswers().get(3) + "\n\n" +
                        "********************************************************************";

        Utils.sendMessage(socket, fullQuestion);
    }

    private Player getWinner() {
        Player winner = null;
        int maxScore = 0;

        /* check if all players have the same score */
        boolean allEqual = true;
        for (Player socket : scores.keySet()) {
            if (!Objects.equals(scores.get(socket), scores.get(userSockets.get(0)))) {
                allEqual = false;
                break;
            }
        }

        if (allEqual) return null;

        for (Player socket : scores.keySet()) {
            int score = scores.get(socket);
            if (score > maxScore) {
                winner = socket;
                maxScore = score;
            }
        }
        return winner;
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    private int updatedRank(Player player) {
        // NewRank = OldRank + K * (Score - Estimated_Probability_of_Winning)
        int ELO_K_CONSTANT_MULTIPLIER = 4;
        int calculation = player.getRank() + ELO_K_CONSTANT_MULTIPLIER * (Math.max(scores.get(player), 1) - calculate_Probability_of_Winning(player));
        // VERIFIES IF THE CALCULATION IS NEGATIVE, IF IT IS, IT RETURNS 0, IF NOT, IT RETURNS THE MINIMUM BETWEEN THE CALCULATION AND 3000
        return (calculation < 0) ? 0 : Math.min(calculation, 3000);
    }

    private int calculate_Probability_of_Winning(Player player) {
        // Probability of Winning = 1 / (1 + 10^((Opponents Average Rank - Player's Rank) / 400))
        return 1 / (1 + (int) Math.pow(10, (double) (getAverageRank() - player.getRank()) / 400));
    }

    private int getAverageRank() {
        int sum = 0;
        for (Player player : userSockets.getList()) sum += player.getRank();
        return sum / userSockets.size();
    }

    private void updatePlayerLevel(Player player) throws IOException {

        player.setRank(updatedRank(player));

        File file = new File("main/data/users.txt");
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        StringBuilder sb = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            String[] fields = line.split(":");
            if (fields[0].equals(player.getUserName())) {
                sb.append(fields[0]).append(":").append(fields[1]).append(":").append(player.getRank()).append(":").append(player.getToken()).append("\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        reader.close();

        FileWriter writer = new FileWriter(file);
        writer.write(sb.toString());
        writer.close();
    }

    public List<TriviaQuestion> getRandomQuestions(int number) {
        List<TriviaQuestion> randomQuestions = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < number; i++) {
            int index = random.nextInt(questions.size());
            randomQuestions.add(questions.get(index));
        }
        return randomQuestions;
    }

    public List<Player> getUserSockets() {
        return userSockets.getList();
    }

    public List<TriviaQuestion> loadQuestions() throws IOException {
        // read questions from json file questions.json
        String dir = System.getProperty("user.dir");
        String jsonString = new String(Files.readAllBytes(Paths.get(dir + "/main/Game/questions.json")));

        // parse json string into list of trivia questions
        List<TriviaQuestion> questions = new ArrayList<>();
        String patternString = "\\{\\s*\"question\"\\s*:\\s*\"([^\"]+)\",\\s*\"answers\"\\s*:\\s*\\[\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\"\\s*\\],\\s*\"correctAnswerIndex\"\\s*:\\s*(\\d+)\\s*\\}";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(jsonString);

        while (matcher.find()) {
            String question = matcher.group(1);
            List<String> answers = Arrays.asList(matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5));
            int correctAnswerIndex = Integer.parseInt(matcher.group(6));
            questions.add(new TriviaQuestion(question, answers, correctAnswerIndex));
        }
        return questions;
    }

    public String getGame_UUID() {
        return game_UUID;
    }

    public void incrementNumDisconnected() {
        num_disconnects++;
    }

    public void decrementNumDisconnected() {
        num_disconnects--;
    }

    public void addPlayer(Player player) {
        if (disconnected_players_score.containsKey(player.getToken())) {
            scores.put(player, disconnected_players_score.get(player.getToken()));
            disconnected_players_score.remove(player.getToken());
        }
        userSockets.add(player);
    }

    public void updateDisconnectedPlayer_Score_Table(Player player, int score) {
        disconnected_players_score.put(player.getToken(), score);
    }

    public ConcurrentMap<Player, Integer> getScores() {
        return scores;
    }


}