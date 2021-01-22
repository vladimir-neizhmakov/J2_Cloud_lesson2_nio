package nio;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class NioServer {

    private final ServerSocketChannel serverChannel = ServerSocketChannel.open();
    private final Selector selector = Selector.open();
    private final ByteBuffer buffer = ByteBuffer.allocate(5);
    private Path serverPath = Paths.get("serverDir");

    public NioServer() throws IOException {
        serverChannel.bind(new InetSocketAddress(8189));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (serverChannel.isOpen()) {
            selector.select(); // block
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new NioServer();
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        int read = 0;
        StringBuilder msg = new StringBuilder();
        while ((read = channel.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                msg.append((char) buffer.get());
            }
            buffer.clear();
        }
        String command = msg.toString().replaceAll("[\n|\r]", "");
        if (command.equals("ls")) {
            String files = Files.list(serverPath)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.joining(", "));
            files += "\n";
            channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
        }
        if (command.startsWith("cd")) {
            String[] args = command.split(" ");
            if (args.length != 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                String targetPath = args[1];
                Path serverDirBefore = serverPath;
                serverPath = serverPath.resolve(targetPath);
                if (!Files.isDirectory(serverPath) && !Files.exists(serverPath)) {
                    channel.write(ByteBuffer.wrap("Wrong arg for cd command\n".getBytes(StandardCharsets.UTF_8)));
                    serverPath = serverDirBefore;
                }
            }
        }
        if (command.startsWith("cat")) {
            String[] args = command.split(" ");
            if (args.length < 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                for(int i = 1;i<args.length;i++){
                    String targetPath = args[i];
                    if (!Files.isDirectory(Paths.get(targetPath)) && !Files.exists(Paths.get(targetPath))) {
                        channel.write(ByteBuffer.wrap(("File "+Paths.get(targetPath).toAbsolutePath().toString() +" not found\n").getBytes(StandardCharsets.UTF_8)));
                    } else {
                        InputStream is = new FileInputStream(targetPath);
                        byte[] buffer = new byte[1024];
                        channel.write(ByteBuffer.wrap(("File "+Paths.get(targetPath).toAbsolutePath().toString() +"\n").getBytes(StandardCharsets.UTF_8)));
                        while ((is.read(buffer)) != -1) {
                            channel.write(ByteBuffer.wrap(buffer));
                        }
                        channel.write(ByteBuffer.wrap(("\nFile ended\n").getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        if (command.startsWith("touch")) {
            String[] args = command.split(" ");
            if (args.length < 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                for (int i = 1; i < args.length; i++) {
                    String targetPath = args[i];
                    if (!Files.exists(Paths.get(targetPath))) {
                        Files.write(Paths.get(targetPath), "".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                    } else {
                        channel.write(ByteBuffer.wrap(("\nFile exists\n").getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        if (command.startsWith("mkdir")) {
            String[] args = command.split(" ");
            if (args.length != 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                String targetPath = args[1];
                if (!Files.isDirectory(Paths.get(targetPath))) {
                    Files.createDirectory(Paths.get(targetPath));
                } else {
                    channel.write(ByteBuffer.wrap(("\nDirectory exists\n").getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
    }
}