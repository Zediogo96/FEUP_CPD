package main;

import java.nio.channels.SocketChannel;

public class Player {

    String userName;
    SocketChannel socket;
    String token;
    int rank;
    long lastQueuedTime;
    int currentRankRange;
    boolean isDisconnected;
    public static final int DEFAULT_RANK_THRESHOLD = 5;

    public Player(String userName, SocketChannel socket, String token, int rank) {
        this.userName = userName;
        this.socket = socket;
        this.token = token;
        this.rank = rank;
        this.currentRankRange = 5;
        this.lastQueuedTime = System.currentTimeMillis();
        this.isDisconnected = false;
    }

    public String getUserName() {
        return userName;
    }

    public SocketChannel getSocketChannel() {
        return socket;
    }

    public String getToken() {
        return token;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public long getLastQueuedTime() {
        return lastQueuedTime;
    }

    public void setLastQueuedTime(long lastQueuedTime) {
        this.lastQueuedTime = lastQueuedTime;
    }

    public int getCurrentRankRange() {
        return currentRankRange;
    }

    public void setDefaultRankRange() {
        this.currentRankRange = DEFAULT_RANK_THRESHOLD;
    }

    public void incrementCurrentRankRange(int amount) {
        this.currentRankRange += amount;
    }

    public boolean isDisconnected() {
        return isDisconnected;
    }
}
