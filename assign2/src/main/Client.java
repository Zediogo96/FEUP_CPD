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
    public static long TIMEOUT = 0;
    private static final int PORT = 1234;
    static boolean waitingForAnswer = false;
    static long startTime = 0;

    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress("localhost", PORT));

        Selector selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);



        while (true) {
            selector.select();
            for (SelectionKey key : selector.selectedKeys()) {
                if (key.isConnectable()) {
                    if (socketChannel.finishConnect()) {
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        System.out.println("Connected to server");
                    }
                    if (args.length == 3 && args[0].equals("register")) {
                        Utils.sendMessage(socketChannel, "register " + args[1] + " " + args[2], "registration");
                    } else if (args.length == 3 && args[0].equals("login")) {
                        Utils.sendMessage(socketChannel, "login " + args[1] + " " + args[2], "auth");
                    } else {
                        System.out.println("Invalid arguments");
                        exit(1);
                    }
                } else if (key.isReadable()) {

                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int bytesRead = socketChannel.read(buffer);

                    if (bytesRead == -1) {
                        break;
                    }

                    buffer.flip();

                    String serverMsg = new String(buffer.array(), 0, buffer.limit());

                    Message msg = Utils.deserializeMessage(serverMsg);

                    if (serverMsg.length() == 0) {
                        continue;
                    }

                    if (msg.getType().equals("question")) {

                        String[] split = msg.getContent().split("//");

                        String question = split[0];
                        String time = split[1].replace("\n", "");

                        TIMEOUT = Long.parseLong(time);

                        System.out.println("\n" + question.replace(";", "\n"));
                        System.out.println("\nEnter your answer: ");

                        BufferedReader reader;

                        reader = new BufferedReader(new InputStreamReader(System.in));
                        startTime = System.currentTimeMillis();


                        boolean validAnswer = false;
                        while (!validAnswer && System.currentTimeMillis() - startTime <= TIMEOUT) {
                            try {
                                if (reader.ready()) {
                                    Integer answer = Integer.parseInt(reader.readLine());
                                    if (answer >= 1 && answer <= 4) {
                                        Utils.sendMessage(socketChannel, String.valueOf(answer), "answer");
                                        waitingForAnswer = false;
                                        validAnswer = true;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid input. Please enter a number between 1 and 4.");
                            }
                        }
                        if (!validAnswer) {
                            Utils.sendMessage(socketChannel, "0", "answer");
                            waitingForAnswer = false;
                        }
                    } else if (msg.getType().equals("scores")) {
                        System.out.println("\n" + msg.getContent().replace(";", "\n"));
                    } else if (msg.getType().equals("registration")) {
                        if (serverMsg.contains("successful")) {
                            System.out.println("\n> Server: " + msg.getContent());
                            System.out.println("Please login with your new credentials.");
                        } else {
                            System.out.println("\n> Server: " + msg.getContent());
                        }
                        System.exit(0);
                    } else {
                        System.out.println("\n> Server: " + msg.getContent());
                    }
                }
            }
            selector.selectedKeys().clear();
        }
    }
}
