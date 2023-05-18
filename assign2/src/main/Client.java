package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static java.lang.System.exit;


public class Client {
    private static final int PORT = 1234;
    public static int TIMEOUT = 25000;

    static boolean waitingForAnswer = false;
    static long startTime = 0;

    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress("localhost", PORT));

        Selector selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (true) {
            selector.select();
            for (SelectionKey key : selector.selectedKeys()) {
                if (key.isConnectable()) {
                    if (socketChannel.finishConnect()) {
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        System.out.println("Connected to server");
                    }
                    if (args.length == 3 && args[0].equals("register")) {
                        Utils.sendMessage(socketChannel, "register " + args[1] + " " + args[2]);
                    } else if (args.length == 3 && args[0].equals("login")) {
                        Utils.sendMessage(socketChannel, "login " + args[1] + " " + args[2]);
                    } else {
                        System.out.println("Invalid arguments");
                        exit(1);
                    }
                } else if (key.isReadable()) {
                    buffer.clear();
                    int bytesRead = socketChannel.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    buffer.flip();
                    String serverMsg = new String(buffer.array(), 0, buffer.limit());

                    if (serverMsg.length() == 0) {
                        continue;
                    }

                    if (serverMsg.contains("Question:")) {
                        System.out.println("\n" + serverMsg.replace(";", "\n"));
                        System.out.println("\nEnter your answer: ");

                        BufferedReader reader = null;
                        if (!waitingForAnswer) {
                            reader = new BufferedReader(new InputStreamReader(System.in));
                            startTime = System.currentTimeMillis();
                            waitingForAnswer = true;
                        }

                        // Check for timeout
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        if (elapsedTime >= TIMEOUT) {
                            Utils.sendMessage(socketChannel, "0");
                            waitingForAnswer = false;
                            break;
                        }

                        if (waitingForAnswer && reader != null) {
                            if (reader.ready()) {
                                Integer answer = Integer.parseInt(reader.readLine());
                                if (answer >= 1 && answer <= 4) {
                                    Utils.sendMessage(socketChannel, String.valueOf(answer));
                                    waitingForAnswer = false;
                                    break;
                                } else {
                                    System.out.println("Invalid answer. Please enter a number between 1 and 4.");
                                }
                            }
                        }

                    } else if (serverMsg.contains("Scores:")) {
                        System.out.println("\n" + serverMsg.replace(";", "\n"));
                    } else if (serverMsg.contains("Registration")) {
                        if (serverMsg.contains("successful")) {
                            System.out.println("\n> Server: " + serverMsg);
                            System.out.println("Please login with your new credentials.");
                        } else {
                            System.out.println("\n> Server: " + serverMsg);
                        }
                        System.exit(0);
                    } else {
                        System.out.println("\n> Server: " + serverMsg);
                    }
                }
            }
            selector.selectedKeys().clear();
        }
    }
}
