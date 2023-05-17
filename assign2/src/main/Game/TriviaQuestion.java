package main.Game;

import java.util.List;

public class TriviaQuestion {
    private final String question;
    private final List<String> answers;
    private final int correctAnswerIndex;

    public TriviaQuestion(String question, List<String> answers, int correctAnswerIndex) {
        this.question = question;
        this.answers = answers;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public Integer getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }

    public String getCorrectAnswer() {
        return answers.get(correctAnswerIndex - 1);
    }
}