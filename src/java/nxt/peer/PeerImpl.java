/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.peer;

import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.util.Convert;
import nxt.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

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

    /** Peer state */
    private volatile State state = State.NON_CONNECTED;

    /** Peer downloaded volume */
    private final AtomicLong downloadedVolume = new AtomicLong();

    /** Peer uploaded volume */
    private final AtomicLong uploadedVolume = new AtomicLong();

    /** Connection address */
    private InetSocketAddress connectionAddress;

    /** Socket channel */
    private SocketChannel channel;

    /** Selection key */
    private SelectionKey key;

    /** Output message list */
    private final ConcurrentLinkedQueue<NetworkMessage> outputQueue = new ConcurrentLinkedQueue<>();

    /** Input buffer */
    private ByteBuffer inputBuffer;

    /** Input message count */
    private final AtomicInteger inputCount = new AtomicInteger();

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

    /**
     * Construct a PeerImpl
     *
     * The host address will be used for the announced address if the announced address is null
     *
     * @param   hostAddress             Host address
     * @param   announcedAddress        Announced address or null
     */
    PeerImpl(InetAddress hostAddress, String announcedAddress) {
        host = hostAddress.getHostAddress();
        setAnnouncedAddress(announcedAddress != null ? announcedAddress.toLowerCase().trim() : host);
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
     * Get the network selection key (used by NetworkHandler)
     *
     * @return                          Selection key
     */
    synchronized SelectionKey getKey() {
        return key;
    }

    /**
     * Update the network selection key (used by NetworkHandler)
     *
     * @param   addOps                  Interest operations to add
     * @param   removeOps               Interest operations to remove
     */
    synchronized void updateKey(int addOps, int removeOps) {
        if (key != null) {
            key.interestOps((key.interestOps() | addOps) & (~removeOps));
        }
    }

    /**
     * Set the network selection key (used by NetworkHandler)
     *
     * @param   key                     Selection key
     */
    synchronized void setKey(SelectionKey key) {
        this.key = key;
    }

    /**
     * Get the output message queue (used by NetworkHandler)
     */
    ConcurrentLinkedQueue<NetworkMessage> getOutputQueue() {
        return outputQueue;
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
    int getInputCount() {
        return inputCount.get();
    }

    /**
     * Increment the input message count (used by NetworkHandler)
     *
     * @return                          Updated message count
     */
    int incrementInputCount() {
        return inputCount.incrementAndGet();
    }

    /**
     * Decrement the input message count (used by MessageHandler)
     *
     * @return                          Updated message count
     */
    int decrementInputCount() {
        return inputCount.decrementAndGet();
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
    void setState(State state) {
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
        return downloadedVolume.get();
    }

    /**
     * Update the download volume
     *
     * @param   volume                  Volume update
     */
    void updateDownloadedVolume(long volume) {
        downloadedVolume.addAndGet(volume);
    }

    /**
     * Get the upload volume
     *
     * @return                          Upload volume
     */
    @Override
    public long getUploadedVolume() {
        return uploadedVolume.get();
    }

    /**
     * Update the upload volume
     *
     * @param   volume                  Volume update
     */
    void updateUploadedVolume(long volume) {
        uploadedVolume.addAndGet(volume);
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
            String[] versions;
            if (version == null || (versions = version.split("\\.")).length < Constants.MIN_VERSION.length) {
                isOldVersion = true;
            } else {
                for (int i=0; i<Constants.MIN_VERSION.length; i++) {
                    try {
                        int v = Integer.parseInt(versions[i]);
                        if (v > Constants.MIN_VERSION[i]) {
                            isOldVersion = false;
                            break;
                        } else if (v < Constants.MIN_VERSION[i]) {
                            isOldVersion = true;
                            break;
                        }
                    } catch (NumberFormatException e) {
                        isOldVersion = true;
                        break;
                    }
                }
            }
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
        }
        return false;
    }

    /**
     * Add a service for this peer
     *
     * @param   service                 Service
     * @param   doNotify                TRUE to notify listeners
     */
    void addService(Service service, boolean doNotify) {
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
    void removeService(Service service, boolean doNotify) {
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
     * Check if this is an inbound connection
     *
     * @return                          TRUE if inbound connection
     */
    @Override
    public boolean isInbound() {
        return isInbound;
    }

    /**
     * Indicate this is an inbound connection
     *
     * @return                          TRUE if inbound state accepted
     */
    boolean setInbound() {
        boolean accepted = false;
        connectLock.lock();
        try {
            if (state != Peer.State.CONNECTED) {
                isInbound = true;
                setState(Peer.State.CONNECTED);
                accepted = true;
                Logger.logInfoMessage("Connection from " + host + " accepted");
            }
        } finally {
            connectLock.unlock();
        }
        return accepted;
    }

    /**
     * Connect the peer
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
                }
                if (!connectCondition.await(NetworkHandler.peerConnectTimeout, TimeUnit.SECONDS)) {
                    Logger.logDebugMessage("Connect to " + host + " timed out");
                }
            }
        } catch (InterruptedException exc) {
            Logger.logDebugMessage("Connect to " + host + " interrupted");
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Connect has completed
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
            if (success) {
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
            NetworkHandler.closeConnection(this);
            outputQueue.clear();
            for (ResponseEntry entry : responseMap.values()) {
                entry.responseSignal(null);
            }
            responseMap.clear();
            isInbound = false;
            downloadedVolume.set(0);
            uploadedVolume.set(0);
            inputBuffer = null;
            outputBuffer = null;
            channel = null;
            key = null;
            connectionAddress = null;
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Send an asynchronous message
     *
     * @param   message                 Network message
     */
    @Override
    public void sendMessage(NetworkMessage message) {
        if (state == State.CONNECTED) {
            try {
                NetworkHandler.sendMessage(this, message);
            } catch (NetworkException exc) {
                Logger.logDebugMessage("Send to " + host + " failed: " + exc.getMessage());
                disconnectPeer();
            }
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
        NetworkMessage response = null;
        ResponseEntry entry = new ResponseEntry();
        responseMap.put(message.getMessageId(), entry);
        try {
            NetworkHandler.sendMessage(this, message);
            response = entry.responseWait();
        } catch (NetworkException exc) {
            Logger.logDebugMessage("Send to " + host + " failed: " + exc.getMessage());
            disconnectPeer();
        }
        responseMap.remove(message.getMessageId());
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
