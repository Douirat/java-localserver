import java.nio.channels.SocketChannel;
import java.util.*;

public class Connection implements Connecting {
    private SocketChannel channel;
    private Requesting request;
    private Responding response;

    // buffers for reading and writing:
    final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);


// state
enum State {
    READING,
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
   
}