package main;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

public final class Utils {

    public static synchronized void sendMessage(SocketChannel socketChannel, String message) {

        try {

            message += "\n";
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            socketChannel.write(buffer);

            buffer.rewind();
            buffer.clear(); // Clear buffer for next message


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized String receiveMessage(SocketChannel socket) {
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
            buffer.clear();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return message;
    }
    
    private static String serializeMessage(Message message) {
        // Serialize the message object to a string representation
        // Example: "type:question;content:What is your name?"
        return "type:" + message.getType() + "//content:" + message.getContent();
    }

    private static Message deserializeMessage(String message) {
        // Deserialize the string representation to a message object
        // Example: "type:question;content:What is your name?"
        //          -> Message("question", "What is your name?")
        String[] split = message.split("//");
        String type = split[0].split(":")[1];
        String content = split[1].split(":")[1];
        return new Message(type, content);
    }
}
