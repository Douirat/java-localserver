package http.connecting;

import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import http.request.Requesting;
import http.response.Responding;
import java.nio.ByteBuffer;

public class Connection implements Connecting {
    private SocketChannel channel;
    private Requesting request;
    private Responding response;

    // buffers for reading and writing:
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);


// state
enum State {
    READING,
    PROCESSING,
    WRITING,
    CLOSED
}


private State state = State.READING;

        public Connection(SocketChannel channel) {
            this.channel = channel;
        }

 // getters and setters:
     @Override
     public SocketChannel getChannel() {
          return this.channel;
     }

     @Override
     public ByteBuffer getReadBuffer() {
            return this.readBuffer;
     }

     @Override
     public ByteBuffer getWriteBuffer() {
            return this.writeBuffer;
     }

     @Override
     public void ParseRequest(){
        if(state != State.READING) {
            return;
        }
        readBuffer.flip();
        String data = StandardCharsets.UTF_8.decode(readBuffer).toString();
        System.out.println(data);
        readBuffer.clear();
     }

    @Override
    public void prepareResponse(String body) {
        writeBuffer.clear();

        String response =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Length: " + body.length() + "\r\n" +
            "\r\n" +
            body;

        writeBuffer.put(response.getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip();
    }

}