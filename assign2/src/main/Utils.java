package main;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

public final class Utils {

    public static synchronized void sendMessage(SocketChannel socketChannel, String message, String type) {

        Utils.wait_msg(75);

        try {
            message = Utils.serializeMessage(new Message(type, message));
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            buffer.put(message.getBytes());
            buffer.flip();
            socketChannel.write(buffer);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized Message receiveMessage(SocketChannel socket) {
        String message = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = socket.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                message = new String(bytes).trim();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assert message != null;
        return Utils.deserializeMessage(message);
    }
    
    private static String serializeMessage(Message message) {
        // Serialize the message object to a string representation
        // Example: "type:question;content:What is your name?"
        return "type->" + message.getType() + "~content->" + message.getContent();
    }

    static Message deserializeMessage(String message) {
        // Deserialize the string representation to a message object
        // Example: "type:question;content:What is your name?"
        //          -> Message("question", "What is your name?")
        String[] split = message.split("~");
        String type = split[0].split("->")[1];
        String content = split[1].split("->")[1];
        return new Message(type, content);
    }

    public static synchronized void wait_msg(long timeout) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeout); // Adjust the delay as needed (500 milliseconds in this example)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
