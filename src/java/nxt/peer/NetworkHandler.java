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

import java.io.InputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

    /** Network handler instance */
    private static final NetworkHandler instance = new NetworkHandler();

    /** Default peer port */
    static final int DEFAULT_PEER_PORT = 7813;

    /** Testnet peer port */
    static final int TESTNET_PEER_PORT = 6813;

    /** Maximum number of pending input messages for a single peer */
    private static final int MAX_INPUT_MESSAGES = 10;

    /** Maximum number of pending output messages for a single peer */
    private static final int MAX_OUTPUT_MESSAGES = 500;

    /** GetInfo message which is sent each time an outbound connection is created */
    private static NetworkMessage getInfoMessage;

    /** Server port */
    static final int serverPort = Constants.isTestnet ? TESTNET_PEER_PORT :
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

    /** Connections list */
    private static final List<PeerImpl> connections = Collections.synchronizedList(new ArrayList<>(128));

    /** Unmodifiable view of collections list */
    private static final List<Peer> allConnections = Collections.unmodifiableList(connections);

    /** Connection map */
    private static final Map<InetAddress, PeerImpl> connectionMap = new HashMap<>();

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
        //
        // Start the network listener
        //
        ThreadPool.runAfterStart(() -> {
            if (enablePeerUPnP) {
                UPnP.addPort(serverPort);
            }
            listenerThread = new Thread(instance, "Network Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
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
                instance.wakeup();
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
     * Network handler
     */
    @Override
    public void run() {
        try {
            Logger.logInfoMessage("Network listener started");
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
     * Process OP_CONNECT event
     *
     * @param   connectKey              Selection key
     */
    private void processConnect(SelectionKey connectKey) {
        PeerImpl peer = (PeerImpl)connectKey.attachment();
        InetAddress address = peer.getHostAddress();
        SocketChannel channel = peer.getChannel();
        try {
            channel.finishConnect();
            Logger.logDebugMessage("Connection established to %s", address.getHostAddress());
            synchronized(peer) {
                peer.getOutputList().add(getInfoMessage);
                connectKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        } catch (SocketException exc) {
            Logger.logDebugMessage(String.format("%s: Peer %s", exc.getLocalizedMessage(), address.getHostAddress()));
            closeConnection(peer);
        } catch (IOException exc) {
            Logger.logDebugMessage(String.format("Connection failed to %s", address.getHostAddress()), exc);
            closeConnection(peer);
        }
    }

    /**
     * Process OP_ACCEPT event
     *
     * @param   acceptKey               Selection key
     */
    private void processAccept(SelectionKey acceptKey) {
        try {
            SocketChannel channel = listenChannel.accept();
            if (channel != null) {
                InetSocketAddress remoteAddress = (InetSocketAddress)channel.getRemoteAddress();
                PeerAddress address = new PeerAddress(remoteAddress);
                if (connections.size() >= maxConnections) {
                    channel.close();
                    log.info(String.format("Max connections reached: Connection rejected from %s", address));
                } else if (isBlacklisted(address.getAddress())) {
                    channel.close();
                    log.info(String.format("Connection rejected from banned address %s", address));
                } else if (connectionMap.get(address.getAddress()) != null) {
                    channel.close();
                    log.info(String.format("Duplicate connection rejected from %s", address));
                } else {
                    address.setTimeConnected(System.currentTimeMillis()/1000);
                    channel.configureBlocking(false);
                    channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    SelectionKey key = channel.register(networkSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    Peer peer = new Peer(address, channel, key);
                    key.attach(peer);
                    peer.setConnected(true);
                    address.setConnected(true);
                    log.info(String.format("Connection accepted from %s", address));
                    Message msg = VersionMessage.buildVersionMessage(peer, Parameters.listenAddress,
                                                                     Parameters.blockStore.getChainHeight());
                    synchronized(connections) {
                        connections.add(peer);
                        connectionMap.put(address.getAddress(), peer);
                        peer.getOutputList().add(msg);
                    }
                    log.info(String.format("Sent 'version' message to %s", address));
                }
            }
        } catch (IOException exc) {
            log.error("Unable to accept connection", exc);
            networkShutdown = true;
        }
    }

    /**
     * Process a read event
     *
     * @param   readKey                 Network selection key
     */
    private void processRead(SelectionKey readKey) {

    }

    /**
     * Process a write event
     *
     * @param   writeKey                Network selection key
     */
    private void processWrite(SelectionKey writeKey) {

    }

    /**
     * Close a connection
     *
     * @param   peer                    Peer connection to close
     */
    static void closeConnection(PeerImpl peer) {

    }

    /**
     * Send a message to a peer
     *
     * @param   peer                    Target peer
     * @param   message                 Message to send
     * @throws  NetworkException        Unable to send message
     */
    static void sendMessage(PeerImpl peer, NetworkMessage message) throws NetworkException {

    }

    /**
     * Send our GetInfo message
     *
     * @param   peer                    Target peer
     */
    static void sendGetInfoMessage(PeerImpl peer) {

    }

    /**
     * Create a new outbound connections
     *
     * @param   peer                    Target peer
     */
    public static void createConnection(Peer peer) {

    }

    /**
     * Broadcast a message to all connected peers
     *
     * @param   message                 Message to send
     */
    public static void broadcastMessage(NetworkMessage message) {

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
     * Get the number of outbound connections
     *
     * @return                          Number of outbound connections
     */
    public static int getOutboundCount() {
        return outboundCount;
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
     * Return the maximum number of outbound connections
     *
     * @return                          Number of outbound connections
     */
    public static int getMaxOutboundConnections() {
        return maxOutbound;
    }
}
