/*
 * Copyright 2013-2016 The Nxt Core Developers.
 *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at
 * the top-level directory of this distribution for the individual copyright
 * holder information and the developer policies on copyright and licensing.
 *
 * Unless otherwise agreed in a custom licensing agreement, no part of the
 * Nxt software, including this file, may be copied, modified, propagated,
 * or distributed except according to the terms contained in the LICENSE.txt
 * file.
 *
 * Removal or modification of this copyright notice is prohibited.
 */
package nxt.peer;

import nxt.Constants;
import nxt.Nxt;
import nxt.http.API;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.ThreadPool;
import nxt.util.UPnP;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The network handler creates outbound connections and adds them to the
 * network selector.  A new outbound connection will be created whenever
 * the number of outbound connections is less than the maximum number of
 * outbound connections.
 *
 * The network handler opens a local port and listens for incoming connections.
 * When a connection is received, it creates a socket channel and accepts the
 * connection as long as the maximum number of inbound connections has not been reached.
 * The socket is then added to the network selector.
 *
 * When a message is received from a peer node, it is processed by a message
 * handler executing on a separate thread.  The message handler processes the
 * message and then creates a response message to be returned to the originating node.
 *
 * The network handler terminates when its shutdown() method is called.
 */
public final class NetworkHandler implements Runnable {

    /** Default peer port */
    static final int DEFAULT_PEER_PORT = 7813;

    /** Testnet peer port */
    static final int TESTNET_PEER_PORT = 6813;

    /** Maximum number of pending input messages for a single peer */
    private static final int MAX_INPUT_MESSAGES = 10;

    /** Message header */
    private static final byte[] MESSAGE_HEADER = new byte[] {(byte)0x03, (byte)0x2c, (byte)0x05, (byte)0xc2};

    /** Maximum message size */
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;

    /** Server port */
    private static final int serverPort = Constants.isTestnet ? TESTNET_PEER_PORT :
            Nxt.getIntProperty("nxt.peerServerPort", DEFAULT_PEER_PORT);

    /** Enable UPnP */
    private static final boolean enablePeerUPnP = Nxt.getBooleanProperty("nxt.enablePeerUPnP");

    /** Share my address */
    private static final boolean shareAddress = Nxt.getBooleanProperty("nxt.shareMyAddress");

    /** Maximum number of outbound connections */
    private static final int maxOutbound = Nxt.getIntProperty("nxt.maxNumberOfOutboundConnections", 8);

    /** Maximum number of inbound connections */
    private static final int maxInbound = Nxt.getIntProperty("nxt.maxNumberOfInboundConnections", 64);

    /** Connect timeout (seconds) */
    static final int peerConnectTimeout = Nxt.getIntProperty("nxt.peerConnectTimeout", 10);

    /** Peer read timeout (seconds) */
    static final int peerReadTimeout = Nxt.getIntProperty("nxt.peerReadTimeout", 10);

    /** Listen address */
    private static final String listenAddress = Nxt.getStringProperty("nxt.peerServerHost", "0.0.0.0");

    /** GetInfo message which is sent each time an outbound connection is created */
    private static NetworkMessage getInfoMessage;

    /** My address */
    static String myAddress;

    /** My host name */
    static String myHost;

    /** My port */
    static int myPort = -1;

    /** Announced address */
    static String announcedAddress;

    static {
        try {
            myAddress = Convert.emptyToNull(Nxt.getStringProperty("nxt.myAddress"));
            if (myAddress != null) {
                myAddress = myAddress.toLowerCase().trim();
                URI uri = new URI("http://" + myAddress);
                myHost = uri.getHost();
                myPort = uri.getPort();
                if (myHost == null) {
                    throw new RuntimeException("nxt.myAddress is not a valid host address");
                }
                if (myPort == TESTNET_PEER_PORT && !Constants.isTestnet) {
                    throw new RuntimeException("Port " + TESTNET_PEER_PORT + " should only be used for testnet");
                }
                if (Constants.isTestnet) {
                    announcedAddress = myHost;
                } else if (myPort == -1 && serverPort != DEFAULT_PEER_PORT) {
                    announcedAddress = myHost + ":" + serverPort;
                } else if (myPort == DEFAULT_PEER_PORT) {
                    announcedAddress = myHost;
                } else {
                    announcedAddress = myAddress;
                }
            }
        } catch (URISyntaxException e) {
            Logger.logWarningMessage("Your announced address is not valid: " + e.toString());
            myAddress = null;
        }
    }

    /** Network listener instance */
    private static final NetworkHandler listener = new NetworkHandler();

    /** Network listener thread */
    private static Thread listenerThread;

    /** Current number of inbound connections */
    private static int inboundCount;

    /** Current number of outbound connections */
    private static int outboundCount;

    /** Listen channel */
    private static ServerSocketChannel listenChannel;

    /** Listen selection key */
    private static SelectionKey listenKey;

    /** Network selector */
    private static Selector networkSelector;

    /** Connection map */
    static final ConcurrentHashMap<InetAddress, PeerImpl> connectionMap = new ConcurrentHashMap<>();

    /** Network shutdown */
    private static volatile boolean networkShutdown = false;

    /**
     * Construct a network handler
     */
    private NetworkHandler() { }

    /**
     * Initialize the network handler
     */
    public static void init() {
        //
        // Don't start the network handler if we are offline
        //
        if (Constants.isOffline) {
            networkShutdown = true;
            Logger.logInfoMessage("Network handler is offline");
            return;
        }
        //
        // Create the GetInfo message which is sent when an outbound connection is
        // completed.  The remote peer will send its GetInfo message in response.
        //
        if (serverPort == TESTNET_PEER_PORT && !Constants.isTestnet) {
            throw new RuntimeException("Port " + TESTNET_PEER_PORT + " should only be used for testnet");
        }
        String platform = Nxt.getStringProperty("nxt.myPlatform",
                                System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        if (platform.length() > Peers.MAX_PLATFORM_LENGTH) {
            platform = platform.substring(0, Peers.MAX_PLATFORM_LENGTH);
        }
        if (myAddress != null) {
            try {
                InetAddress[] myAddrs = InetAddress.getAllByName(myHost);
                boolean addrValid = false;
                Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
                chkAddr: while (intfs.hasMoreElements()) {
                    NetworkInterface intf = intfs.nextElement();
                    List<InterfaceAddress> intfAddrs = intf.getInterfaceAddresses();
                    for (InterfaceAddress intfAddr: intfAddrs) {
                        InetAddress extAddr = intfAddr.getAddress();
                        for (InetAddress myAddr : myAddrs) {
                            if (extAddr.equals(myAddr)) {
                                addrValid = true;
                                break chkAddr;
                            }
                        }
                    }
                }
                if (!addrValid) {
                    InetAddress extAddr = UPnP.getExternalAddress();
                    if (extAddr != null) {
                        for (InetAddress myAddr : myAddrs) {
                            if (extAddr.equals(myAddr)) {
                                addrValid = true;
                                break;
                            }
                        }
                    }
                }
                if (!addrValid) {
                    Logger.logWarningMessage("Your announced address does not match your external address");
                }
            } catch (SocketException e) {
                Logger.logErrorMessage("Unable to enumerate the network interfaces: " + e.toString());
            } catch (UnknownHostException e) {
                Logger.logWarningMessage("Your announced address is not valid: " + e.toString());
            }
        }
        long services = 0;
        for (Peer.Service service : Peers.myServices) {
            services |= service.getCode();
        }
        getInfoMessage = new NetworkMessage.GetInfoMessage(Nxt.APPLICATION, Nxt.VERSION, platform,
                shareAddress, announcedAddress, serverPort, API.openAPIPort, API.openAPISSLPort, services);
        try {
            //
            // Create the selector for listening for network events
            //
            networkSelector = Selector.open();
            //
            // Create the listen channel
            //
            listenChannel = ServerSocketChannel.open();
            listenChannel.configureBlocking(false);
            listenChannel.bind(new InetSocketAddress(listenAddress, serverPort), 10);
            listenKey = listenChannel.register(networkSelector, SelectionKey.OP_ACCEPT);
        } catch (IOException exc) {
            networkShutdown = true;
            throw new RuntimeException("Unable to create network listener", exc);
        }
        //
        // Start the network handler after server initialization has completed
        //
        ThreadPool.runAfterStart(() -> {
            if (enablePeerUPnP) {
                UPnP.addPort(serverPort);
            }
            //
            // Start the network listener
            //
            listenerThread = new Thread(listener, "Network Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            //
            // Start the message handlers
            //
            for (int i=0; i<8; i++) {
                MessageHandler handler = new MessageHandler();
                Thread handlerThread = new Thread(handler, "Message Handler " + i+1);
                handlerThread.setDaemon(true);
                handlerThread.start();
            }
        });
    }

    /**
     * Shutdown the network handler
     */
    public static void shutdown() {
        if (!networkShutdown) {
            networkShutdown = true;
            if (enablePeerUPnP) {
                UPnP.deletePort(serverPort);
            }
            if (networkSelector != null) {
                listener.wakeup();
            }
        }
    }

    /**
     * Wakes up the network listener
     */
    private void wakeup() {
        if (Thread.currentThread() != listenerThread) {
            networkSelector.wakeup();
        }
    }

    /**
     * Network listener
     */
    @Override
    public void run() {
        try {
            Logger.logInfoMessage("Network listener started");

            //
            // Process network events
            //
            while (!networkShutdown) {
                processEvents();
            }
        } catch (Throwable exc) {
            Logger.logErrorMessage("Network listener abnormally terminated", exc);
            networkShutdown = true;
        }
        Logger.logInfoMessage("Network listener stopped");
    }

    /**
     * Process network events
     */
    private void processEvents() {
        int count;
        try {
            //
            // Process selectable events
            //
            // Note that you need to remove the key from the selected key
            // set.  Otherwise, the selector will return immediately since
            // it thinks there are still unprocessed events.  Also, accessing
            // a key after the channel is closed will cause an exception to be
            // thrown, so it is best to test for just one event at a time for
            // each selection key.
            //
            count = networkSelector.select();
            if (count > 0 && !networkShutdown) {
                Set<SelectionKey> selectedKeys = networkSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext() && !networkShutdown) {
                    SelectionKey key = keyIterator.next();
                    SelectableChannel channel = key.channel();
                    if (channel.isOpen()) {
                        if (key.isAcceptable())
                            processAccept(key);
                        else if (key.isConnectable())
                            processConnect(key);
                        else if (key.isReadable())
                            processRead(key);
                        else if (key.isWritable())
                            processWrite(key);
                    }
                    keyIterator.remove();
                }
            }
        } catch (ClosedSelectorException exc) {
            Logger.logErrorMessage("Network selector closed unexpectedly", exc);
            networkShutdown = true;
        } catch (IOException exc) {
            Logger.logErrorMessage("I/O error while processing selection event", exc);
        }
    }

    /**
     * Create a new outbound connection
     *
     * @param   peer                    Target peer
     */
    static void createConnection(PeerImpl peer) {
        try {
            InetAddress address = InetAddress.getByName(peer.getHost());
            InetSocketAddress remoteAddress = new InetSocketAddress(address, peer.getPort());
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.bind(null);
            SelectionKey key = channel.register(networkSelector, SelectionKey.OP_CONNECT);
            key.attach(peer);
            connectionMap.put(address, peer);
            outboundCount++;
            peer.setConnectionAddress(remoteAddress);
            peer.setChannel(channel);
            peer.setKey(key);
            channel.connect(remoteAddress);
        } catch (BindException exc) {
            Logger.logErrorMessage("Unable to bind local port: " + exc.toString());
        } catch (UnknownHostException exc) {
            Logger.logErrorMessage("Unable to resolve host " + peer.getHost() + ": " + exc.getMessage());
        } catch (IOException exc) {
            Logger.logErrorMessage("Unable to open connection to " + peer.getHost() + ": " + exc.getMessage());
        }
    }

    /**
     * Process OP_CONNECT event (outbound connect completed)
     *
     * @param   connectKey              Selection key
     */
    private void processConnect(SelectionKey connectKey) {
        PeerImpl peer = (PeerImpl)connectKey.attachment();
        String hostAddress = peer.getConnectionAddress().getAddress().getHostAddress();
        SocketChannel channel = peer.getChannel();
        try {
            channel.finishConnect();
            peer.connectComplete(true);
            peer.updateKey(SelectionKey.OP_READ, 0);
            sendGetInfoMessage(peer);
        } catch (SocketException exc) {
            Logger.logDebugMessage(String.format("%s: Peer %s", exc.getMessage(), hostAddress));
            peer.connectComplete(false);
        } catch (IOException exc) {
            Logger.logDebugMessage("Connection failed to " + hostAddress + ": " + exc.getMessage());
            peer.connectComplete(false);
        }
    }

    /**
     * Process OP_ACCEPT event (inbound connect received)
     *
     * @param   acceptKey               Selection key
     */
    private void processAccept(SelectionKey acceptKey) {
        try {
            SocketChannel channel = listenChannel.accept();
            if (channel != null) {
                InetSocketAddress remoteAddress = (InetSocketAddress)channel.getRemoteAddress();
                String hostAddress = remoteAddress.getAddress().getHostAddress();
                PeerImpl peer = Peers.findOrCreatePeer(remoteAddress.getAddress());
                if (peer == null) {
                    channel.close();
                    Logger.logDebugMessage("Peer not accepted: Connection rejected from " + hostAddress);
                } else if (inboundCount >= maxInbound) {
                    channel.close();
                    Logger.logDebugMessage("Max inbound connections reached: Connection rejected from " + hostAddress);
                } else if (peer.isBlacklisted()) {
                    channel.close();
                    Logger.logDebugMessage("Peer is blacklisted: Connection rejected from " + hostAddress);
                } else if (connectionMap.get(remoteAddress.getAddress()) != null || !peer.setInbound()) {
                    channel.close();
                    Logger.logDebugMessage("Connection already established with " + hostAddress);
                } else {
                    channel.configureBlocking(false);
                    channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    SelectionKey key = channel.register(networkSelector, SelectionKey.OP_READ);
                    key.attach(peer);
                    peer.setConnectionAddress(remoteAddress);
                    peer.setChannel(channel);
                    peer.setKey(key);
                    connectionMap.put(remoteAddress.getAddress(), peer);
                    inboundCount++;
                    Peers.addPeer(peer);
                }
            }
        } catch (IOException exc) {
            Logger.logErrorMessage("Unable to accept connection", exc);
            networkShutdown = true;
        }
    }

    /**
     * Process OP_READ event (ready to read data)
     *
     * @param   readKey                 Network selection key
     */
    private void processRead(SelectionKey readKey) {
        PeerImpl peer = (PeerImpl)readKey.attachment();
        SocketChannel channel = peer.getChannel();
        ByteBuffer buffer = peer.getInputBuffer();
        peer.setLastUpdated(Nxt.getEpochTime());
        try {
            int count;
            //
            // Read data until we have a complete message or no more data is available
            //
            while (true) {
                //
                // Allocate a header buffer if no read is in progress
                //   4-byte identifier
                //   4-byte message length
                //
                if (buffer == null) {
                    buffer = ByteBuffer.wrap(new byte[MESSAGE_HEADER.length + 4]);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    peer.setInputBuffer(buffer);
                }
                //
                // Fill the input buffer (stop if no more data is available)
                //
                if (buffer.position() < buffer.limit()) {
                    count = channel.read(buffer);
                    if (count <= 0) {
                        if (count < 0)
                            peer.disconnectPeer();
                        break;
                    }
                }
                //
                // Process the message header
                //
                if (buffer.position() == buffer.limit() && buffer.limit() == MESSAGE_HEADER.length + 4) {
                    byte[] hdrBytes = new byte[MESSAGE_HEADER.length];
                    buffer.get(hdrBytes);
                    int length = buffer.getInt();
                    if (!Arrays.equals(hdrBytes, MESSAGE_HEADER)) {
                        Logger.logDebugMessage("Incorrect message header received from " + peer.getHost());
                        peer.disconnectPeer();
                        break;
                    }
                    if (length > MAX_MESSAGE_SIZE) {
                        Logger.logDebugMessage("Message length " + length + " for message from " + peer.getHost()
                                + " is too large");
                        peer.disconnectPeer();
                        break;
                    }
                    if (length > 0) {
                        byte[] msgBytes = new byte[hdrBytes.length + length];
                        System.arraycopy(hdrBytes, 0, msgBytes, 0, hdrBytes.length);
                        buffer = ByteBuffer.wrap(msgBytes);
                        buffer.order(ByteOrder.LITTLE_ENDIAN);
                        buffer.position(hdrBytes.length);
                        peer.setInputBuffer(buffer);
                    }
                }
                //
                // Queue the message for a message handler
                //
                // We will disable read operations for this peer if it has too many
                // pending messages.  Read operations will be re-enabled once
                // all of the messages have been processed.  We do this to keep
                // one node from flooding us with requests.
                //
                if (buffer.position() == buffer.limit()) {
                    peer.setInputBuffer(null);
                    buffer.position(MESSAGE_HEADER.length + 4);
                    MessageHandler.processMessage(peer, buffer);
                    int inputCount = peer.incrementInputCount();
                    if (inputCount >= MAX_INPUT_MESSAGES) {
                        peer.updateKey(0, SelectionKey.OP_READ);
                    }
                    break;
                }
            }
        } catch (IOException exc) {
            peer.disconnectPeer();
        }
    }

    /**
     * Process OP_WRITE event (ready to write data)
     *
     * @param   writeKey                Network selection key
     */
    private void processWrite(SelectionKey writeKey) {
        PeerImpl peer = (PeerImpl)writeKey.attachment();
        SocketChannel channel = peer.getChannel();
        ByteBuffer buffer = peer.getOutputBuffer();
        try {
            //
            // Write data until all pending messages have been sent or the socket buffer is full
            //
            while (true) {
                //
                // Get the next message if no write is in progress.  Disable write events
                // if there are no more messages to write.
                //
                if (buffer == null) {
                    NetworkMessage message = peer.getOutputQueue().poll();
                    if (message == null) {
                        peer.updateKey(0, SelectionKey.OP_WRITE);
                        break;
                    }
                    int length = message.getLength();
                    buffer = ByteBuffer.allocate(MESSAGE_HEADER.length + 4 + length);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put(MESSAGE_HEADER);
                    buffer.putInt(length);
                    try {
                        message.getBytes(buffer);
                    } catch (BufferOverflowException exc) {
                        Logger.logErrorMessage("Buffer is too short for '" + message.getMessageName()
                                + "' message from " + peer.getHost());
                        peer.disconnectPeer();
                        break;
                    }
                    peer.setOutputBuffer(buffer);
                }
                //
                // Write the current buffer to the channel
                //
                channel.write(buffer);
                if (buffer.position() < buffer.limit())
                    break;
                buffer = null;
                peer.setOutputBuffer(null);
            }
        } catch (IOException exc) {
            peer.disconnectPeer();
        }
    }

    /**
     * Close a connection
     *
     * @param   peer                    Peer connection to close
     */
    static void closeConnection(PeerImpl peer) {
        SocketChannel channel = peer.getChannel();
        if (channel == null) {
            return;
        }
        try {
            if (peer.isInbound()) {
                inboundCount = Math.max(0, inboundCount-1);
            } else {
                outboundCount = Math.max(0, outboundCount-1);
            }
            connectionMap.remove(peer.getConnectionAddress().getAddress());
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException exc) {
            // Ignore
        }
    }

    /**
     * Send a message to a peer
     *
     * @param   peer                    Target peer
     * @param   message                 Message to send
     * @throws  NetworkException        Unable to send message
     */
    static void sendMessage(PeerImpl peer, NetworkMessage message) throws NetworkException {
        if (peer.getState() == Peer.State.CONNECTED) {
            peer.getOutputQueue().add(message);
            peer.updateKey(SelectionKey.OP_WRITE, 0);
        }
    }

    /**
     * Send our GetInfo message
     *
     * @param   peer                    Target peer
     */
    static void sendGetInfoMessage(PeerImpl peer) {
        if (peer.getState() == Peer.State.CONNECTED) {
            peer.getOutputQueue().add(getInfoMessage);
            peer.updateKey(SelectionKey.OP_WRITE, 0);
        }
    }

    /**
     * Broadcast a message to all connected peers
     *
     * @param   message                 Message to send
     */
    public static void broadcastMessage(NetworkMessage message) {
        connectionMap.values().forEach(peer -> {
            if (peer.getState() == Peer.State.CONNECTED) {
                peer.getOutputQueue().add(message);
                peer.updateKey(SelectionKey.OP_WRITE, 0);
            }
        });
    }

    /**
     * Get the default peer port
     *
     * @return                          Default peer port
     */
    public static int getDefaultPeerPort() {
        return Constants.isTestnet ? TESTNET_PEER_PORT : DEFAULT_PEER_PORT;
    }

    /**
     * Get the connected peer count
     *
     * @return                          Connected peer count
     */
    public static int getConnectionCount() {
        return inboundCount + outboundCount;
    }

    /**
     * Get the number of inbound connections
     *
     * @return                          Number of inbound connections
     */
    public static int getInboundCount() {
        return inboundCount;
    }

    /**
     * Return the maximum number of inbound connections
     *
     * @return                          Number of inbound connections
     */
    public static int getMaxInboundConnections() {
        return maxInbound;
    }

    /**
     * Get the number of outbound connections
     *
     * @return                          Number of outbound connections
     */
    public static int getOutboundCount() {
        return outboundCount;
    }

    /**
     * Return the maximum number of outbound connections
     *
     * @return                          Number of outbound connections
     */
    public static int getMaxOutboundConnections() {
        return maxOutbound;
    }
}
