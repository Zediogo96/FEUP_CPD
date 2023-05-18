package main;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;

public final class Utils {

    public static synchronized void sendMessage(SocketChannel socketChannel, String message) {

        /*if (!socketChannel.isConnected()) return;*/
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


    public static synchronized void sendMessageToMultipleSockets(List<SocketChannel> sockets, String message) {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

        try {
            for (SocketChannel socket : sockets) {
                socket.write(buffer);
                buffer.rewind();
                buffer.clear(); // Clear buffer for next message
            }
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


}
