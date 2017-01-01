/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.peer;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.blockchain.BlockchainProcessor;
import nxt.http.API;
import nxt.http.APIEnum;
import nxt.util.Convert;
import nxt.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class PeerImpl implements Peer {

    /** Host address */
    private final String host;

    /** Inbound connection */
    private volatile boolean isInbound = false;

    /** Announced address (including the port) */
    private volatile String announcedAddress;

    /** Share address */
    private volatile boolean shareAddress = false;

    /** Application platform */
    private volatile String platform;

    /** Application name */
    private volatile String application;

    /** Peer port */
    private volatile int port;

    /** Open API port */
    private volatile int apiPort;

    /** Open SSL port */
    private volatile int apiSSLPort;

    /** Application version */
    private volatile String version;

    private volatile EnumSet<APIEnum> disabledAPIs;

    private volatile int apiServerIdleTimeout;

    /** Old application version */
    private volatile boolean isOldVersion = false;

    /** Time peer was blacklisted */
    private volatile int blacklistingTime;

    /** Blacklist cause */
    private volatile String blacklistingCause;

    /** Time peer was last updated */
    private volatile int lastUpdated;

    /** Time of last connect attempt */
    private volatile int lastConnectAttempt;

    /** Peer services */
    private volatile long services;

    private volatile BlockchainState blockchainState;

    /** Peer state */
    private volatile State state = State.NON_CONNECTED;

    /** Peer downloaded volume */
    private volatile long downloadedVolume;

    /** Peer uploaded volume */
    private volatile long uploadedVolume;

    /** Connection address */
    private InetSocketAddress connectionAddress;

    /** Socket channel */
    private SocketChannel channel;

    /** Selection key event */
    private NetworkHandler.KeyEvent keyEvent;

    /** Output message list */
    private final ConcurrentLinkedQueue<NetworkMessage> outputQueue = new ConcurrentLinkedQueue<>();

    /** Handshake message */
    private NetworkMessage handshakeMessage;

    /** Input buffer */
    private ByteBuffer inputBuffer;

    /** Input message count */
    private volatile int inputCount;

    /** Output buffer */
    private ByteBuffer outputBuffer;

    /** Response list */
    private final ConcurrentHashMap<Long, ResponseEntry> responseMap = new ConcurrentHashMap<>();

    /** Connection lock */
    private final ReentrantLock connectLock = new ReentrantLock();

    /** Connection condition */
    private final Condition connectCondition = connectLock.newCondition();

    /** Connect in progress */
    private volatile boolean connectPending = false;

    /** Handshake in progress */
    private volatile boolean handshakePending = false;

    /**
     * Construct a PeerImpl
     *
     * The host address will be used for the announced address if the announced address is null
     *
     * @param   hostAddress             Host address
     * @param   announcedAddress        Announced address or null
     */
    PeerImpl(InetAddress hostAddress, String announcedAddress) {
        this.host = hostAddress.getHostAddress();
        setAnnouncedAddress(announcedAddress != null ? announcedAddress.toLowerCase().trim() : host);
        this.disabledAPIs = EnumSet.noneOf(APIEnum.class);
        this.apiServerIdleTimeout = API.apiServerIdleTimeout;
        this.blockchainState = BlockchainState.UP_TO_DATE;
    }

    /**
     * Close an active connection and remove the peer from the peer list
     */
    void remove() {
        disconnectPeer();
        Peers.removePeer(this);
    }

    /**
     * Get the peer state
     *
     * @return                          Current state
     */
    @Override
    public State getState() {
        return state;
    }

    /**
     * Set the peer state
     *
     * @param   state                   New state
     */
    synchronized void setState(State state) {
        if (this.state != state) {
            if (this.state == State.NON_CONNECTED) {
                this.state = state;
                Peers.notifyListeners(this, Peers.Event.ADD_ACTIVE_PEER);
            } else if (state != State.NON_CONNECTED) {
                this.state = state;
                Peers.notifyListeners(this, Peers.Event.CHANGE_ACTIVE_PEER);
            } else {
                this.state = state;
            }
        }
    }

    /**
     * Get the host address
     *
     * @return                          Host address
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * Get the announced address
     *
     * @return                          Announced address
     */
    @Override
    public String getAnnouncedAddress() {
        return announcedAddress;
    }

    /**
     * Set the announced address
     *
     * The announced address will be set to the host address if the announced address is null
     *
     * @param announcedAddress          Announced address or null if there is no announced address
     */
    void setAnnouncedAddress(String announcedAddress) {
        if (announcedAddress == null) {
            this.announcedAddress = host;
            this.port = -1;
        } else {
            if (announcedAddress.length() > Peers.MAX_ANNOUNCED_ADDRESS_LENGTH) {
                throw new IllegalArgumentException("Announced address too long: " + announcedAddress.length());
            }
            this.announcedAddress = announcedAddress;
            try {
                this.port = new URI("http://" + announcedAddress).getPort();
            } catch (URISyntaxException e) {
                this.port = -1;
            }
        }
    }

    /**
     * Get the announced address port
     *
     * @return                          Port
     */
    @Override
    public int getPort() {
        return port <= 0 ? NetworkHandler.getDefaultPeerPort() : port;
    }

    /**
     * Get the download volume
     *
     * @return                          Download volume
     */
    @Override
    public long getDownloadedVolume() {
        return downloadedVolume;
    }

    /**
     * Update the download volume
     *
     * @param   volume                  Volume update
     */
    void updateDownloadedVolume(long volume) {
        downloadedVolume += volume;
    }

    /**
     * Get the upload volume
     *
     * @return                          Upload volume
     */
    @Override
    public long getUploadedVolume() {
        return uploadedVolume;
    }

    /**
     * Update the upload volume
     *
     * @param   volume                  Volume update
     */
    void updateUploadedVolume(long volume) {
        uploadedVolume += volume;
    }

    /**
     * Get the application version
     *
     * @return                          Application version or null if no version
     */
    @Override
    public String getVersion() {
        return version;
    }

    /**
     * Set the application version
     *
     * The application name must be set before setting the version in order to perform version checking.
     * The peer will be blacklisted and disconnected if the version is obsolete.
     *
     * @param   version                 Application version
     * @return                          TRUE if the version is acceptable
     */
    boolean setVersion(String version) {
        if (version != null && version.length() > Peers.MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Invalid version length: " + version.length());
        }
        boolean versionChanged = (version == null || !version.equals(this.version));
        this.version = version;
        isOldVersion = false;
        if (Nxt.APPLICATION.equals(application)) {
            isOldVersion = Peers.isOldVersion(version, Constants.MIN_VERSION);
            if (isOldVersion) {
                if (versionChanged) {
                    Logger.logDebugMessage(String.format("Blacklisting %s version %s", getHost(), version));
                }
                blacklistingCause = "Old version: " + version;
                Peers.notifyListeners(this, Peers.Event.BLACKLIST);
            }
        }
        return !isOldVersion;
    }

    /**
     * Get the application name
     *
     * @return                          Application name or null
     */
    @Override
    public String getApplication() {
        return application;
    }

    /**
     * Set the application name
     *
     * @param   application             Application name
     */
    void setApplication(String application) {
        if (application == null || application.length() > Peers.MAX_APPLICATION_LENGTH) {
            throw new IllegalArgumentException("Invalid application");
        }
        this.application = application;
    }

    /**
     * Get the application platform
     *
     * @return                          Application platform or null
     */
    @Override
    public String getPlatform() {
        return platform;
    }

    /**
     * Set the application platform
     *
     * @param   platform                Application platform
     */
    void setPlatform(String platform) {
        if (platform != null && platform.length() > Peers.MAX_PLATFORM_LENGTH) {
            throw new IllegalArgumentException("Invalid platform length: " + platform.length());
        }
        this.platform = platform;
    }

    /**
     * Get the software description
     *
     * @return                          Software description
     */
    @Override
    public String getSoftware() {
        return Convert.truncate(application, "?", 10, false)
                + " (" + Convert.truncate(version, "?", 10, false) + ")"
                + " @ " + Convert.truncate(platform, "?", 10, false);
    }

    /**
     * Get the open API port
     *
     * @return                          Open API port
     */
    @Override
    public int getApiPort() {
        return apiPort;
    }

    /**
     * Set the open API port
     *
     * @param   apiPort                 Port
     */
    void setApiPort(int apiPort) {
        this.apiPort = apiPort;
    }

    /**
     * Get the open SSL port
     *
     * @return                          Port
     */
    @Override
    public int getApiSSLPort() {
        return apiSSLPort;
    }

    /**
     * Set the open SSL port
     *
     * @param   apiSSLPort              Port
     */
    void setApiSSLPort(int apiSSLPort) {
        this.apiSSLPort = apiSSLPort;
    }

    /**
     * Check if address should be shared
     *
     * @return                          TRUE if address should be shared
     */
    @Override
    public Set<APIEnum> getDisabledAPIs() {
        return Collections.unmodifiableSet(disabledAPIs);
    }

    void setDisabledAPIs(String apiSetBase64) {
        disabledAPIs = APIEnum.base64StringToEnumSet(apiSetBase64);
    }

    @Override
    public int getApiServerIdleTimeout() {
        return apiServerIdleTimeout;
    }

    void setApiServerIdleTimeout(int apiServerIdleTimeout) {
        this.apiServerIdleTimeout = apiServerIdleTimeout;
    }

    @Override
    public BlockchainState getBlockchainState() {
        return blockchainState;
    }

    void setBlockchainState(BlockchainState blockchainState) {
        this.blockchainState = blockchainState;
    }

    @Override
    public boolean shareAddress() {
        return shareAddress;
    }

    /**
     * Set address share
     *
     * @param   shareAddress            TRUE if address shoiuld be shared
     */
    void setShareAddress(boolean shareAddress) {
        this.shareAddress = shareAddress;
    }

    /**
     * Check if peer is blacklisted
     *
     * @return                          TRUE if peer is blacklisted
     */
    @Override
    public boolean isBlacklisted() {
        return blacklistingTime > 0 || isOldVersion || Peers.knownBlacklistedPeers.contains(getHost())
                || (announcedAddress != null && Peers.knownBlacklistedPeers.contains(announcedAddress));
    }

    /**
     * Get the blacklist cause
     *
     * @return                          Blacklist cause or null
     */
    @Override
    public String getBlacklistingCause() {
        return blacklistingCause;
    }

    /**
     * Blacklist the peer
     *
     * @param   cause                   Exception causing the blacklist
     */
    @Override
    public void blacklist(Exception cause) {
        if (cause instanceof NxtException.NotCurrentlyValidException
                || cause instanceof BlockchainProcessor.BlockOutOfOrderException
                || cause instanceof SQLException || cause.getCause() instanceof SQLException) {
            // don't blacklist peers just because a feature is not yet enabled, or because of database timeouts
            // prevents erroneous blacklisting during loading of blockchain from scratch
            return;
        }
        if (!isBlacklisted()) {
            if (cause instanceof IOException || cause instanceof IllegalArgumentException) {
                Logger.logDebugMessage("Blacklisting " + host + " because of: " + cause.toString());
            } else {
                Logger.logDebugMessage("Blacklisting " + host + " because of: " + cause.toString(), cause);
            }
        }
        blacklist(cause.toString() == null || Peers.hideErrorDetails ? cause.getClass().getName() : cause.toString());
    }

    /**
     * Blacklist the peer
     *
     * @param   cause                   Blacklist cause
     */
    @Override
    public void blacklist(String cause) {
        blacklistingTime = Nxt.getEpochTime();
        blacklistingCause = cause;
        disconnectPeer();
        Peers.notifyListeners(this, Peers.Event.BLACKLIST);
    }

    /**
     * Unblacklist the peer
     */
    @Override
    public void unBlacklist() {
        if (blacklistingTime == 0 ) {
            return;
        }
        Logger.logDebugMessage("Unblacklisting " + host);
        blacklistingTime = 0;
        blacklistingCause = null;
        Peers.notifyListeners(this, Peers.Event.UNBLACKLIST);
    }

    /**
     * Update the peer blacklist status
     *
     * @param   curTime                 The current EPOCH time
     */
    void updateBlacklistedStatus(int curTime) {
        if (blacklistingTime > 0 && blacklistingTime + Peers.blacklistingPeriod <= curTime) {
            unBlacklist();
        }
        if (isOldVersion && lastUpdated < curTime - 3600) {
            isOldVersion = false;
        }
    }

    /**
     * Get the last update time
     *
     * @return                          Epoch time
     */
    @Override
    public int getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Set the last update time
     *
     * @param   lastUpdated             Epoch time
     */
    void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Get the last connect attempt time
     *
     * @return                          Epoch time
     */
    @Override
    public int getLastConnectAttempt() {
        return lastConnectAttempt;
    }

    /**
     * Set the last connect attempt time
     *
     * @param   lastConnectAttempt      Epoch time
     */
    void setLastConnectAttempt(int lastConnectAttempt) {
        this.lastConnectAttempt = lastConnectAttempt;
    }

    /**
     * Verify the announced address
     *
     * @param   newAnnouncedAddress     The new announced address
     */
    boolean verifyAnnouncedAddress(String newAnnouncedAddress) {
        if (newAnnouncedAddress == null) {
            return true;
        }
        try {
            URI uri = new URI("http://" + newAnnouncedAddress);
            InetAddress address = InetAddress.getByName(host);
            for (InetAddress inetAddress : InetAddress.getAllByName(uri.getHost())) {
                if (inetAddress.equals(address)) {
                    return true;
                }
            }
            Logger.logDebugMessage("Announced address " + newAnnouncedAddress + " does not resolve to " + host);
        } catch (UnknownHostException | URISyntaxException e) {
            Logger.logDebugMessage(e.toString());
            blacklist(e);
        }
        return false;
    }


    /**
     * Add a service for this peer
     *
     * @param   service                 Service
     * @param   doNotify                TRUE to notify listeners
     */
    private void addService(Service service, boolean doNotify) {
        boolean notifyListeners;
        synchronized (this) {
            notifyListeners = ((services & service.getCode()) == 0);
            services |= service.getCode();
        }
        if (notifyListeners && doNotify) {
            Peers.notifyListeners(this, Peers.Event.CHANGE_SERVICES);
        }
    }

    /**
     * Remove a service for this peer
     *
     * @param   service                 Service
     * @param   doNotify                TRUE to notify listeners
     */
    private void removeService(Service service, boolean doNotify) {
        boolean notifyListeners;
        synchronized (this) {
            notifyListeners = ((services & service.getCode()) != 0);
            services &= (~service.getCode());
        }
        if (notifyListeners && doNotify) {
            Peers.notifyListeners(this, Peers.Event.CHANGE_SERVICES);
        }
    }

    /**
     * Get the services provided by this peer
     *
     * @return                          Services as a bit map
     */
    long getServices() {
        synchronized (this) {
            return services;
        }
    }

    /**
     * Set the services provided by this peer
     *
     * @param   services                Services as a bit map
     */
    void setServices(long services) {
        synchronized (this) {
            this.services = services;
        }
    }

    /**
     * Check if the peer provides a service
     *
     * @param   service                 Service to check
     * @return                          TRUE if the service is provided
     */
    @Override
    public boolean providesService(Service service) {
        boolean isProvided;
        synchronized (this) {
            isProvided = ((services & service.getCode()) != 0);
        }
        return isProvided;
    }

    /**
     * Check if the peer provides the specified services
     *
     * @param   services                Services as a bit map
     * @return                          TRUE if the services are provided
     */
    @Override
    public boolean providesServices(long services) {
        boolean isProvided;
        synchronized (this) {
            isProvided = (services & this.services) == services;
        }
        return isProvided;
    }

    /**
     * Get the connection address (used by NetworkHandler)
     *
     * @return                          Connection address
     */
    InetSocketAddress getConnectionAddress() {
        return connectionAddress;
    }

    /**
     * Set the connection address (used by NetworkHandler)
     *
     * @param   connectionAddress       Connection address
     */
    void setConnectionAddress(InetSocketAddress connectionAddress) {
        this.connectionAddress = connectionAddress;
    }

    /**
     * Get the network channel (used by NetworkHandler)
     *
     * @return                          Socket channel
     */
    SocketChannel getChannel() {
        return channel;
    }

    /**
     * Set the network channel (used by NetworkHandler)
     *
     * @param   channel                 Socket channel
     */
    void setChannel(SocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Get the network section key event
     *
     * @return                          Selection key event
     */
    NetworkHandler.KeyEvent getKeyEvent() {
        return keyEvent;
    }

    /**
     * Set the network selection key event (used by NetworkHandler and MessageHandler)
     *
     * @param   keyEvent                Selection key event
     */
    void setKeyEvent(NetworkHandler.KeyEvent keyEvent) {
        this.keyEvent = keyEvent;
    }

    /**
     * Get the input buffer (used by NetworkHandler)
     *
     * @return                          Input buffer
     */
    ByteBuffer getInputBuffer() {
        return inputBuffer;
    }

    /**
     * Set the input buffer (used by NetworkHandler)
     *
     * @param   inputBuffer             Input buffer
     */
    void setInputBuffer(ByteBuffer inputBuffer) {
        this.inputBuffer = inputBuffer;
    }

    /**
     * Get the input message count (used by NetworkHandler)
     *
     * @return                          Input message count
     */
    synchronized int getInputCount() {
        return inputCount;
    }

    /**
     * Increment the input message count (used by NetworkHandler)
     *
     * @return                          Updated message count
     */
    synchronized int incrementInputCount() {
        return ++inputCount;
    }

    /**
     * Decrement the input message count (used by MessageHandler)
     *
     * @return                          Updated message count
     */
    synchronized int decrementInputCount() {
        return --inputCount;
    }

    /**
     * Get the output buffer (used by NetworkHandler)
     *
     * @return                          Output buffer
     */
    ByteBuffer getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Set the output buffer (used by NetworkHandler)
     *
     * @param   outputBuffer            Output buffer
     */
    void setOutputBuffer(ByteBuffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }

    /**
     * Check if this is an inbound connection
     *
     * @return                          TRUE if inbound connection
     */
    @Override
    public boolean isInbound() {
        return isInbound;
    }

    /**
     * Indicate this is an inbound connection (used by NetworkHandler)
     *
     * No messages will be sent on this connection until the GetInfo message is received.
     * This include responses to requests sent by the peer.  The peer will be disconnected
     * if the output queue reaches the maximum number of pending messages.
     */
    void setInbound() {
        connectLock.lock();
        try {
            if (state != State.CONNECTED) {
                isInbound = true;
                handshakePending = true;
                setState(State.CONNECTED);
                Logger.logInfoMessage("Connection from " + host + " accepted");
            }
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Connect the peer
     *
     * Multiple connect requests will be queued until the connection is established and
     * the peer state is changed to CONNECTED.
     */
    @Override
    public void connectPeer() {
        connectLock.lock();
        try {
            if (state != State.CONNECTED) {
                if (!connectPending) {
                    unBlacklist();
                    isOldVersion = false;
                    setLastConnectAttempt(Nxt.getEpochTime());
                    NetworkHandler.createConnection(this);
                    connectPending = true;
                    handshakePending = true;
                }
                if (!connectCondition.await(NetworkHandler.peerConnectTimeout, TimeUnit.SECONDS)) {
                    Logger.logDebugMessage("Connect to " + host + " timed out");
                    disconnectPeer();
                }
            }
        } catch (InterruptedException exc) {
            Logger.logDebugMessage("Connect to " + host + " interrupted");
        } catch (Exception exc) {
            Logger.logErrorMessage("Unable to wait for connect to complete", exc);
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Connect has completed
     *
     * An outbound connection has been established and our GetInfo message has been sent.
     * We will not send any more messages until we receive the GetInfo message from the peer.
     *
     * @param   success                 TRUE if the connection is established
     */
    void connectComplete(boolean success) {
        connectLock.lock();
        try {
            if (connectPending) {
                connectPending = false;
                connectCondition.signalAll();
            }
            if (success && channel != null) {
                setState(State.CONNECTED);
                Logger.logInfoMessage("Connection to " + host + " completed");
            } else {
                disconnectPeer();
            }
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Connection handshake has completed
     *
     * Send any queued messages
     */
    void handshakeComplete() {
        handshakePending = false;
        if (!outputQueue.isEmpty()) {
            keyEvent.update(SelectionKey.OP_WRITE, 0);
        }
    }

    /**
     * Disconnect the peer
     */
    @Override
    public void disconnectPeer() {
        connectLock.lock();
        try {
            if (state == State.CONNECTED) {
                Logger.logInfoMessage("Connection to " + host + " closed");
            }
            setState(State.DISCONNECTED);
            if (connectPending) {
                connectPending = false;
                connectCondition.signalAll();
            }
            NetworkHandler.closeConnection(this);
            outputQueue.clear();
            for (ResponseEntry entry : responseMap.values()) {
                entry.responseSignal(null);
            }
            responseMap.clear();
            isInbound = false;
            handshakePending = false;
            handshakeMessage = null;
            downloadedVolume = 0;
            uploadedVolume = 0;
            inputBuffer = null;
            outputBuffer = null;
            inputCount = 0;
            channel = null;
            keyEvent = null;
            connectionAddress = null;
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Get the next queued message (used by NetworkHandler)
     *
     * All messages except the GetInfoMessage will be held until the connection handshake
     * has been completed.
     *
     * @return                          Next message or null
     */
    synchronized NetworkMessage getQueuedMessage() {
        NetworkMessage message;
        if (handshakePending) {
            message = handshakeMessage;
            handshakeMessage = null;
        } else {
            message = outputQueue.poll();
        }
        return message;
    }

    /**
     * Send a message
     *
     * All messages except the GetInfoMessage will be held until the connection handshake
     * has been completed.
     *
     * @param   message                 Network message
     */
    @Override
    public void sendMessage(NetworkMessage message) {
        boolean sendMessage = false;
        boolean disconnect = false;
        synchronized(this) {
            if (state == State.CONNECTED) {
                if (handshakePending && message instanceof NetworkMessage.GetInfoMessage) {
                    handshakeMessage = message;
                    sendMessage = true;
                } else if (outputQueue.size() >= NetworkHandler.MAX_PENDING_MESSAGES) {
                    Logger.logErrorMessage("Too many pending messages for " + host);
                    disconnect = true;
                } else {
                    outputQueue.add(message);
                    if (!handshakePending) {
                        sendMessage = true;
                    }
                }
            } else {
                Logger.logDebugMessage("Flushing " + message.getMessageName() + " message because "
                        + host + " is not connected");
            }
        }
        if (sendMessage) {
            keyEvent.update(SelectionKey.OP_WRITE, 0);
            if (Peers.communicationLogging == 1) {
                Logger.logDebugMessage(message.getMessageName() + " message sent to " + host);
            }
        } else if (disconnect) {
            disconnectPeer();
        }
    }

    /**
     * Send a request and wait for a response
     *
     * @param   message                 Request message
     * @return                          Response message or null if an error occurred
     */
    @Override
    public NetworkMessage sendRequest(NetworkMessage message) {
        if (state != State.CONNECTED) {
            return null;
        }
        ResponseEntry entry = new ResponseEntry();
        responseMap.put(message.getMessageId(), entry);
        sendMessage(message);
        if (state != State.CONNECTED) {
            responseMap.remove(message.getMessageId());
            return null;
        }
        NetworkMessage response = entry.responseWait();
        responseMap.remove(message.getMessageId());
        if (response == null) {
            disconnectPeer();
            return null;
        }
        if (response instanceof NetworkMessage.ErrorMessage) {
            NetworkMessage.ErrorMessage error = (NetworkMessage.ErrorMessage)response;
            if (error.isSevereError()) {
                Logger.logDebugMessage("Error returned by " + host + " for '" + error.getErrorName() +
                        "' message: " + error.getErrorMessage());
                disconnectPeer();
            }
            return null;
        }
        return response;
    }

    /**
     * Complete a pending request
     *
     * @param   message                 Response message
     */
    void completeRequest(NetworkMessage message) {
        ResponseEntry entry = responseMap.get(message.getMessageId());
        if (entry != null) {
            entry.responseSignal(message);
        } else {
            Logger.logErrorMessage("Request not found for '" + message.getMessageName() + "' message");
        }
    }

    @Override
    public boolean isOpenAPI() {
        return providesService(Peer.Service.API) || providesService(Peer.Service.API_SSL);
    }

    @Override
    public boolean isApiConnectable() {
        return isOpenAPI() && state == Peer.State.CONNECTED
                && !Peers.isOldVersion(version, Constants.MIN_PROXY_VERSION)
                && !Peers.isNewVersion(version)
                && blockchainState == Peer.BlockchainState.UP_TO_DATE;
    }

    @Override
    public StringBuilder getPeerApiUri() {
        StringBuilder uri = new StringBuilder();
        if (providesService(Peer.Service.API_SSL)) {
            uri.append("https://");
        } else {
            uri.append("http://");
        }
        uri.append(host).append(":");
        if (providesService(Peer.Service.API_SSL)) {
            uri.append(apiSSLPort);
        } else {
            uri.append(apiPort);
        }
        return uri;
    }

    @Override
    public String toString() {
        return "Peer{" +
                "state=" + state +
                ", announcedAddress='" + announcedAddress + '\'' +
                ", services=" + services +
                ", host='" + host + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    /**
     * Message response entry
     */
    private class ResponseEntry {

        /** Response latch */
        private final CountDownLatch responseLatch = new CountDownLatch(1);

        /** Response message */
        private NetworkMessage responseMessage;

        /**
         * Construct a response entry
         */
        private ResponseEntry() {
        }

        /**
         * Wait for a response
         *
         * @return                              Response message or null if there is no message
         */
        NetworkMessage responseWait() {
            try {
                if (!responseLatch.await(NetworkHandler.peerReadTimeout, TimeUnit.SECONDS)) {
                    Logger.logDebugMessage("Read from " + host + " timed out");
                }
            } catch (InterruptedException exc) {
                Logger.logDebugMessage("Read from " + host + " interrupted");
            }
            return responseMessage;
        }

        /**
         * Signal that a response has been received
         *
         * @param   responseMessage             Response message or null if there is no message
         */
        void responseSignal(NetworkMessage responseMessage) {
            this.responseMessage = responseMessage;
            responseLatch.countDown();
        }
    }

}
