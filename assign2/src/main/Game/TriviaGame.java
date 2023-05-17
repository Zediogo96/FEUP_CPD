package main.Game;

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
    public boolean isGameOver = false;
    private final List<TriviaQuestion> questions;
    private final List<TriviaQuestion> gameQuestions;
    private final List<Player> userSockets;
    private final ConcurrentMap<Player, Integer> scores;

    public TriviaGame(int number_of_rounds, List<Player> userSockets) throws IOException {

        this.questions = loadQuestions();
        this.gameQuestions = getRandomQuestions(NUM_ROUNDS);
        this.userSockets = userSockets;
        this.scores = new ConcurrentMap<>();
        NUM_ROUNDS = number_of_rounds;

        for (Player player : userSockets) {
            scores.put(player, 0);
        }
    }

    @Override
    public void run() {

        System.out.println("> Sending game start message to all players");
        String initMsg = "Game starting with " + userSockets.size() + " players";

        ConcurrentMap<Socket, Integer> answers = new ConcurrentMap<>();

        for (Player player : userSockets) {
            sendMessage(player.getSocketChannel(), initMsg);
        }

        for (int i = 0; i < NUM_ROUNDS; i++) {

            TriviaQuestion question = gameQuestions.get(i);

            System.out.println("> Sending question nr. " + (i + 1) + " to all players");

            for (Player player : userSockets) {
                sendQuestion(player.getSocketChannel(), question);
            }

            int numAnswersReceived = 0;
            int timeout = 7000; // Timeout value in milliseconds
            long startTime = System.currentTimeMillis();

            while (numAnswersReceived < userSockets.size()) {
                for (Player socket : userSockets) {
                    try {
                        if (socket.getSocketChannel().socket().getInputStream().available() > 0) {
                            String answer = Utils.receiveMessage(socket.getSocketChannel());
                            answer = answer.replace("\n", "");
                            answers.put(socket.getSocketChannel().socket(), Integer.parseInt(answer));
                            numAnswersReceived++;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                // CHECK IF TIMEOUT HAS BEEN REACHED
                long elapsedTime = System.currentTimeMillis() - startTime;

                if (elapsedTime >= timeout) {
                    for (Player player : userSockets) {
                        if (!answers.containsKey(player.getSocketChannel().socket())) {
                            sendMessage(player.getSocketChannel(), "You did not answer in time!");
                            answers.put(player.getSocketChannel().socket(), -1);
                        }
                    }
                    System.out.println("> Timeout reached, moving on to next question");
                    break;
                }
            }

            for (Player player : userSockets) {
                if (Objects.equals(answers.get(player.getSocketChannel().socket()), question.getCorrectAnswerIndex())) {
                    scores.put(player, scores.get(player) + 1);
                    sendMessage(player.getSocketChannel(), "Your was correct!");
                } else {
                    sendMessage(player.getSocketChannel(), "Your answer was incorrect! The correct answer was: " + question.getCorrectAnswerIndex());
                }
            }

            StringBuilder scoresMsg = new StringBuilder("Scores:\n");
            for (int j = 0; j < userSockets.size(); j++) {
                scoresMsg.append("Player ").append(j + 1).append(": ").append(scores.get(userSockets.get(j))).append("\n");
            }

            for (Player player : userSockets) {
                sendMessage(player.getSocketChannel(), scoresMsg.toString());
            }

            answers.clear();
        }

        Player winner = getWinner();

        for (Player player : userSockets) {

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
        for (Player player : userSockets) sum += player.getRank();
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
        return userSockets;
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
}