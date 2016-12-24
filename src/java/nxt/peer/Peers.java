/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
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
import nxt.blockchain.Bundler;
import nxt.blockchain.ChildChain;
import nxt.dbschema.Db;
import nxt.http.API;
import nxt.util.Filter;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.QueuedThreadPool;
import nxt.util.ThreadPool;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Peers {

    public enum Event {
        BLACKLIST,                      // Blackisted
        UNBLACKLIST,                    // Unblacklisted peer
        ADD_PEER,                       // Peer added to peer list
        CHANGE_ANNOUNCED_ADDRESS,       // Peer changed announced address
        CHANGE_SERVICES,                // Peer changed services
        REMOVE_PEER,                    // Removed peer from peer list
        ADD_ACTIVE_PEER,                // Peer is now active
        CHANGE_ACTIVE_PEER              // Active peer state changed
    }

    /** Maximum application version length */
    static final int MAX_VERSION_LENGTH = 10;

    /** Maximum application name length */
    static final int MAX_APPLICATION_LENGTH = 20;

    /** Maximum application platform length */
    static final int MAX_PLATFORM_LENGTH = 30;

    /** Maximum announced address length */
    static final int MAX_ANNOUNCED_ADDRESS_LENGTH = 100;

    /** Peer blacklist period (seconds) */
    static final int blacklistingPeriod = Nxt.getIntProperty("nxt.blacklistingPeriod", 600);

    /** Get more peers */
    private static final boolean getMorePeers = Nxt.getBooleanProperty("nxt.getMorePeers");

    /** Maximum number of known peers */
    private static final int maxNumberOfKnownPeers =
            Math.max(100, Nxt.getIntProperty("nxt.maxNumberOfKnownPeers", 2000));

    /** Minimum number of known peers */
    private static final int minNumberOfKnownPeers = Math.max(100,
            Math.min(maxNumberOfKnownPeers, Nxt.getIntProperty("nxt.minNumberOfKnownPeers", 1000)));

    /** Use peers database */
    private static final boolean usePeersDb = Nxt.getBooleanProperty("nxt.usePeersDb");

    /** Save peers */
    private static final boolean savePeers = Nxt.getBooleanProperty("nxt.savePeers");

    /** Hide error details */
    static final boolean hideErrorDetails = Nxt.getBooleanProperty("nxt.hideErrorDetails");

    /** Ignore peer announced address */
    static final boolean ignorePeerAnnouncedAddress = Nxt.getBooleanProperty("nxt.ignorePeerAnnouncedAddress");

    /** Local peer services */
    static final List<Peer.Service> myServices;

    static {
        List<Peer.Service> services = new ArrayList<>();
        if (!Constants.ENABLE_PRUNING && Constants.INCLUDE_EXPIRED_PRUNABLE) {
            services.add(Peer.Service.PRUNABLE);
        }
        if (API.openAPIPort > 0) {
            services.add(Peer.Service.API);
        }
        if (API.openAPISSLPort > 0) {
            services.add(Peer.Service.API_SSL);
        }
        if (API.apiServerCORS) {
            services.add(Peer.Service.CORS);
        }
        myServices = Collections.unmodifiableList(services);
    }

    /** Well-known peers */
    private static final List<String> wellKnownPeers = Constants.isTestnet ?
            Nxt.getStringListProperty("nxt.testnetPeers") : Nxt.getStringListProperty("nxt.wellKnownPeers");

    /** Known blacklisted peers */
    static final Set<String> knownBlacklistedPeers;
    static {
        List<String> knownBlacklistedPeersList = Nxt.getStringListProperty("nxt.knownBlacklistedPeers");
        if (knownBlacklistedPeersList.isEmpty()) {
            knownBlacklistedPeers = Collections.emptySet();
        } else {
            knownBlacklistedPeers = Collections.unmodifiableSet(new HashSet<>(knownBlacklistedPeersList));
        }
    }

    /** Peer event listeners */
    private static final Listeners<Peer, Event> listeners = new Listeners<>();

    /** Known peers */
    private static final ConcurrentMap<String, PeerImpl> peers = new ConcurrentHashMap<>();

    /** Known announced addresses */
    private static final ConcurrentMap<String, String> selfAnnouncedAddresses = new ConcurrentHashMap<>();

    /** Read-only peer list */
    private static final Collection<Peer> allPeers = Collections.unmodifiableCollection(peers.values());

    /** Peer executor service pool */
    static final ExecutorService peersService = new QueuedThreadPool(2, 15);

    /** Start time */
    private static final int startTime = Nxt.getEpochTime();

    /** Broadcast blockchain state */
    private static Peer.BlockchainState broadcastBlockchainState = Peer.BlockchainState.UP_TO_DATE;

    /** Bundler rates broadcast time */
    private static int ratesTime = startTime ;

    /** Bundler status has changed */
    private static volatile boolean bundlersChanged = false;

    /** Current bundler rates */
    private static final Map<String, NetworkMessage.BundlerRateMessage> bundlerRates = new ConcurrentHashMap<>();

    /**
     * Initialize peer processing
     */
    public static void init() {
        if (Constants.isOffline) {
            Logger.logInfoMessage("Peer services are offline");
            return;
        }
        final List<String> defaultPeers = Constants.isTestnet ?
                Nxt.getStringListProperty("nxt.defaultTestnetPeers") : Nxt.getStringListProperty("nxt.defaultPeers");
        final List<Future<String>> unresolvedPeers = Collections.synchronizedList(new ArrayList<>());
        if (!Constants.isOffline) {
            //
            // Build the peer list
            //
            ThreadPool.runBeforeStart(new Runnable() {
                private final Set<PeerDb.Entry> entries = new HashSet<>();

                @Override
                public void run() {
                    final int now = Nxt.getEpochTime();
                    wellKnownPeers.forEach(address -> entries.add(new PeerDb.Entry(address, 0, now)));
                    if (usePeersDb) {
                        Logger.logDebugMessage("Loading known peers from the database...");
                        defaultPeers.forEach(address -> entries.add(new PeerDb.Entry(address, 0, now)));
                        if (savePeers) {
                            List<PeerDb.Entry> dbPeers = PeerDb.loadPeers();
                            dbPeers.forEach(entry -> {
                                if (!entries.add(entry)) {
                                    // Database entries override entries from nxt.properties
                                    entries.remove(entry);
                                    entries.add(entry);
                                }
                            });
                        }
                    }
                    entries.forEach(entry -> {
                        Future<String> unresolvedAddress = peersService.submit(() -> {
                            PeerImpl peer = (PeerImpl)Peers.findOrCreatePeer(entry.getAddress(), true);
                            if (peer != null) {
                                peer.setShareAddress(true);
                                peer.setLastUpdated(entry.getLastUpdated());
                                peer.setServices(entry.getServices());
                                Peers.addPeer(peer);
                                return null;
                            }
                            return entry.getAddress();
                        });
                        unresolvedPeers.add(unresolvedAddress);
                    });
                }
            }, false);
        }
        //
        // Check the results
        //
        ThreadPool.runAfterStart(() -> {
            for (Future<String> unresolvedPeer : unresolvedPeers) {
                try {
                    String badAddress = unresolvedPeer.get(5, TimeUnit.SECONDS);
                    if (badAddress != null) {
                        Logger.logDebugMessage("Failed to resolve peer address: " + badAddress);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Logger.logDebugMessage("Failed to add peer", e);
                } catch (TimeoutException ignore) {
                }
            }
            Logger.logDebugMessage("Known peers: " + peers.size());
        });
        //
        // Schedule our background processing threads
        //
        ThreadPool.scheduleThread("PeerUnBlacklisting", peerUnBlacklistingThread, 60);
        ThreadPool.scheduleThread("PeerConnecting", peerConnectingThread, 60);
        if (getMorePeers) {
            ThreadPool.scheduleThread("GetMorePeers", getMorePeersThread, 10*60);
    	}
        if (savePeers) {
            ThreadPool.scheduleThread("UpdatePeerDb", updatePeerDbThread, 60*60);
        }
    }

    /**
     * Shutdown peer processing
     */
    public static void shutdown() {
        ThreadPool.shutdownExecutor("peersService", peersService, 5);
    }

    /**
     * Add a peer listener
     *
     * @param   listener                Listener
     * @param   eventType               Listener event
     * @return                          TRUE if the listener was added
     */
    public static boolean addListener(Listener<Peer> listener, Event eventType) {
        return Peers.listeners.addListener(listener, eventType);
    }

    /**
     * Remove a peer listener
     *
     * @param   listener                Listener
     * @param   eventType               Listener event
     * @return                          TRUE if the listener was removed
     */
    public static boolean removeListener(Listener<Peer> listener, Event eventType) {
        return Peers.listeners.removeListener(listener, eventType);
    }

    /**
     * Notify all listeners for the specified event
     *
     * @param   peer                    Peer
     * @param   eventType               Listener event
     */
    static void notifyListeners(Peer peer, Event eventType) {
        Peers.listeners.notify(peer, eventType);
    }

    /**
     * Add a peer to the peer list
     *
     * @param   peer                    Peer to add
     * @return                          TRUE if this is a new peer
     */
    public static boolean addPeer(Peer peer) {
        Peer oldPeer = peers.put(peer.getHost(), (PeerImpl)peer);
        if (oldPeer != null) {
            return false;
        }
        selfAnnouncedAddresses.put(peer.getAnnouncedAddress(), peer.getHost());
        listeners.notify(peer, Event.ADD_PEER);
        return true;
    }

    /**
     * Change the announced address for a peer
     *
     * @param   peer                    Peer
     * @param   newAnnouncedAddress     The new announced address
     */
    static void changePeerAnnouncedAddress(PeerImpl peer, String newAnnouncedAddress) {
        selfAnnouncedAddresses.remove(peer.getAnnouncedAddress());
        peer.setAnnouncedAddress(newAnnouncedAddress);
        selfAnnouncedAddresses.put(peer.getAnnouncedAddress(), peer.getHost());
        listeners.notify(peer,Event.CHANGE_ANNOUNCED_ADDRESS);
    }

    /**
     * Remove a peer from the peer list
     *
     * @param   peer                    Peer to remove
     * @return                          TRUE if the peer was removed
     */
    public static boolean removePeer(Peer peer) {
        if (peer.getAnnouncedAddress() != null) {
            selfAnnouncedAddresses.remove(peer.getAnnouncedAddress());
        }
        if (peers.remove(peer.getHost()) == null) {
            return false;
        }
        notifyListeners(peer, Event.REMOVE_PEER);
        return true;
    }

    /**
     * Return local peer services
     *
     * @return                      List of local peer services
     */
    public static List<Peer.Service> getServices() {
        return myServices;
    }

    /**
     * Find or create a peer
     *
     * The announced address will be used for the host address if a new peer is created
     *
     * @param   announcedAddress        Peer announced address
     * @param   create                  TRUE to create the peer if it is not found
     * @return                          Peer or null if the peer could not be created
     */
    public static Peer findOrCreatePeer(String announcedAddress, boolean create) {
        PeerImpl peer;
        if (announcedAddress == null) {
            return null;
        }
        String address = announcedAddress.toLowerCase().trim();
        String hostAddress = selfAnnouncedAddresses.get(address);
        if (hostAddress != null && (peer = peers.get(hostAddress)) != null) {
            return peer;
        }
        InetAddress inetAddress;
        String host;
        int port;
        try {
            URI uri = new URI("http://" + address);
            host = uri.getHost();
            if (host == null) {
                return null;
            }
            port = (uri.getPort() == -1 ? NetworkHandler.getDefaultPeerPort() : uri.getPort());
            inetAddress = InetAddress.getByName(host);
        } catch (UnknownHostException | URISyntaxException e) {
            return null;
        }
        if (Constants.isTestnet && port != NetworkHandler.TESTNET_PEER_PORT) {
            Logger.logDebugMessage("Peer " + host + " on testnet is not using port " + NetworkHandler.TESTNET_PEER_PORT + ", ignoring");
            return null;
        }
        if (!Constants.isTestnet && port == NetworkHandler.TESTNET_PEER_PORT) {
            Logger.logDebugMessage("Peer " + host + " is using testnet port " + NetworkHandler.TESTNET_PEER_PORT + ", ignoring");
            return null;
        }
        return findOrCreatePeer(inetAddress, address, create);
    }

    /**
     * Find or create a peer
     *
     * The announced address will be set to the host address if a new peer is created.
     * A new peer will be created if an existing peer is not found.
     *
     * @param   inetAddress             Peer address
     * @return                          Peer or null if the peer could not be created
     */
    static PeerImpl findOrCreatePeer(InetAddress inetAddress) {
        return findOrCreatePeer(inetAddress, inetAddress.getHostAddress(), true);
    }

    /**
     * Find or create a peer
     *
     * @param   inetAddress             Peer address
     * @param   announcedAddress        Announced address
     * @param   create                  TRUE to create the peer if it doesn't exist
     * @return                          Peer or null if the peer could not be created
     */
    private static PeerImpl findOrCreatePeer(InetAddress inetAddress, String announcedAddress, boolean create) {
        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
            return null;
        }
        PeerImpl peer;
        String host = inetAddress.getHostAddress();
        if ((peer = peers.get(host)) != null) {
            return peer;
        }
        if (!create) {
            return null;
        }
        if (NetworkHandler.announcedAddress != null && NetworkHandler.announcedAddress.equalsIgnoreCase(announcedAddress)) {
            return null;
        }
        if (announcedAddress != null && announcedAddress.length() > MAX_ANNOUNCED_ADDRESS_LENGTH) {
            return null;
        }
        peer = new PeerImpl(inetAddress, announcedAddress);
        return peer;
    }

    public static Peer getPeer(String host) {
        return peers.get(host);
    }

    /**
     * Get a random peer that satisfies the supplied filter
     *
     * @param   filter                  Filter
     * @return                          Selected peer or null
     */
    public static Peer getAnyPeer(Filter<Peer> filter) {
        List<? extends Peer> peerList = new ArrayList<>(peers.values());
        return getAnyPeer(peerList, filter);
    }

    /**
     * Get a random peer from the supplied list
     *
     * @param   peerList                Peer list
     * @return                          Selected peer or null
     */
    public static Peer getAnyPeer(List<? extends Peer> peerList) {
        return getAnyPeer(peerList, null);
    }

    /**
     * Get a random peer that satisfies the supplied filter
     *
     * @param   peerList                Peer list
     * @param   filter                  Filter or null if no filter supplied
     * @return                          Selected peer or null
     */
    public static Peer getAnyPeer(List<? extends Peer> peerList, Filter<Peer> filter) {
        if (peerList.isEmpty()) {
            return null;
        }
        Peer peer = null;
        int start = ThreadLocalRandom.current().nextInt(peerList.size());
        boolean foundPeer = false;
        for (int i=start; i<peerList.size(); i++) {
            peer = peerList.get(i);
            if (filter == null || filter.ok(peer)) {
                foundPeer = true;
                break;
            }
        }
        if (!foundPeer) {
            for (int i=0; i<start; i++) {
                peer = peerList.get(i);
                if (filter == null || filter.ok(peer)) {
                    foundPeer = true;
                    break;
                }
            }
        }
        return (foundPeer ? peer : null);
    }

    /**
     * Get a list of peers satisfying the supplied filter
     *
     * @param   filter                  Filter
     * @return                          List of peers
     */
    public static List<Peer> getPeers(Filter<Peer> filter) {
        return getPeers(filter, Integer.MAX_VALUE);
    }

    /**
     * Get a list of peers satisfying the supplied filter
     *
     * @param   filter                  Filter
     * @param   limit                   Maximum number of peers to return
     * @return                          List of peers
     */
    public static List<Peer> getPeers(Filter<Peer> filter, int limit) {
        List<Peer> result = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (filter.ok(peer)) {
                result.add(peer);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Return all known peers
     *
     * @return                          List of known peers
     */
    public static Collection<Peer> getAllPeers() {
        return allPeers;
    }

    /**
     * Return all connected peers
     *
     * @return                          List of connected peers
     */
    public static List<Peer> getConnectedPeers() {
        return new ArrayList<>(NetworkHandler.connectionMap.values());
    }

    /**
     * Check if we should get more peers
     *
     * @return                          TRUE if we should get more peers
     */
    static boolean shouldGetMorePeers() {
        return getMorePeers;
    }

    /**
     * Check if there are too many known peers
     *
     * @return                          TRUE if there are too many known peers
     */
    static boolean hasTooManyKnownPeers() {
        return peers.size() >= maxNumberOfKnownPeers;
    }

    /**
     * Update peer blacklist status
     */
    private static final Runnable peerUnBlacklistingThread = () -> {
        try {
            int curTime = Nxt.getEpochTime();
            for (PeerImpl peer : peers.values()) {
                peer.updateBlacklistedStatus(curTime);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Create outbound connections
     */
    private static final Runnable peerConnectingThread = () -> {
        try {
            final int now = Nxt.getEpochTime();
            //
            // Create new outbound connections
            //
            // The well-known peers are tried first.  If we need more outbound connections, we
            // will randomly select peers from the list of known peers.
            //
            int connectCount = Math.min(10, NetworkHandler.getMaxOutboundConnections() - NetworkHandler.getOutboundCount());
            List<PeerImpl> connectList = new ArrayList<>();
            if (connectCount > 0) {
                for (String wellKnownPeer : wellKnownPeers) {
                    PeerImpl peer = (PeerImpl)findOrCreatePeer(wellKnownPeer, true);
                    if (peer == null) {
                        Logger.logWarningMessage("Unable to create peer for well-known peer " + wellKnownPeer);
                        continue;
                    }
                    if (!peer.isBlacklisted()
                            && peer.getAnnouncedAddress() != null
                            && peer.shareAddress()
                            && peer.getState() != Peer.State.CONNECTED
                            && now - peer.getLastConnectAttempt() > 10*60) {
                        peer.setLastConnectAttempt(now);
                        connectList.add(peer);
                        connectCount--;
                        if (connectCount == 0) {
                            break;
                        }
                    }
                }
            }
            if (connectCount > 0) {
                List<Peer> resultList = getPeers(peer -> !peer.isBlacklisted()
                        && peer.getAnnouncedAddress() != null
                        && peer.shareAddress()
                        && peer.getState() != Peer.State.CONNECTED
                        && (now - peer.getLastUpdated() > 60*60 || peer.getLastUpdated() < startTime)
                        && now - peer.getLastConnectAttempt() > 10*60);
                while (!resultList.isEmpty() && connectCount > 0) {
                    int i = ThreadLocalRandom.current().nextInt(resultList.size());
                    PeerImpl peer = (PeerImpl)resultList.remove(i);
                    peer.setLastConnectAttempt(now);
                    connectList.add(peer);
                    connectCount--;
                }
            }
            if (!connectList.isEmpty()) {
                connectList.forEach(peer -> peersService.execute(() -> peer.connectPeer()));
            }
            //
            // Remove peers if we have too many
            //
            if (peers.size() > maxNumberOfKnownPeers) {
                int initialSize = peers.size();
                PriorityQueue<PeerImpl> sortedPeers = new PriorityQueue<>(peers.size(), (o1, o2) ->
                        o1.getLastUpdated() < o2.getLastUpdated() ? -1 :
                                o1.getLastUpdated() > o2.getLastUpdated() ? 1 : 0);
                sortedPeers.addAll(peers.values());
                while (peers.size() > minNumberOfKnownPeers) {
                    sortedPeers.poll().remove();
                }
                Logger.logDebugMessage("Reduced peer pool size from " + initialSize + " to " + peers.size());
            }
            //
            // Notify connected peers if our blockchain state has changed
            //
            Peer.BlockchainState currentState = getMyBlockchainState();
            if (currentState != broadcastBlockchainState) {
                Logger.logDebugMessage("Broadcasting blockchain state change from "
                        + broadcastBlockchainState.name() + " to " + currentState.name());
                NetworkMessage blockchainStateMessage = new NetworkMessage.BlockchainStateMessage(currentState);
                NetworkHandler.broadcastMessage(blockchainStateMessage);
                broadcastBlockchainState = currentState;
            }
            //
            // Notify connected peers if we have active bundlers or if we have stopped all bundlers
            //
            if (now - ratesTime >= 60 * 60 || bundlersChanged) {
                NetworkMessage.BundlerRateMessage msg = bundlerRates.get("localhost");
                if (msg == null && bundlersChanged) {
                    msg = new NetworkMessage.BundlerRateMessage("localhost", new ArrayList<>(0));
                }
                if (msg != null) {
                    Logger.logDebugMessage("Broadcasting our bundler rates");
                    NetworkHandler.broadcastMessage(msg);
                }
                ratesTime = now;
                bundlersChanged = false;
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Get more peers
     */
    private static final Runnable getMorePeersThread = () -> {
        try {
            Peer peer = getAnyPeer(getConnectedPeers());
            if (peer != null) {
                //
                // Request a list of connected peers (the response is asynchronous)
                //
                if (peers.size() < maxNumberOfKnownPeers) {
                    peer.sendMessage(new NetworkMessage.GetPeersMessage());
                }
                //
                // Send a list of our connected peers
                //
                List<Peer> peerList = getPeers(p -> !p.isBlacklisted()
                        && p.getState() == Peer.State.CONNECTED
                        && p.getAnnouncedAddress() != null
                        && p.shareAddress()
                        && !p.getAnnouncedAddress().equals(peer.getAnnouncedAddress()),
                    NetworkMessage.MAX_LIST_SIZE);
                if (!peerList.isEmpty()) {
                    peer.sendMessage(new NetworkMessage.AddPeersMessage(peerList));
                }
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Update the peer database
     */
    private static final Runnable updatePeerDbThread = () -> {
        try {
            int now = Nxt.getEpochTime();
            //
            // Load the current database entries and map announced address to database entry
            //
            List<PeerDb.Entry> oldPeers = PeerDb.loadPeers();
            Map<String, PeerDb.Entry> oldMap = new HashMap<>(oldPeers.size());
            oldPeers.forEach(entry -> oldMap.put(entry.getAddress(), entry));
            //
            // Create the current peer map (note that there can be duplicate peer entries with
            // the same announced address)
            //
            Map<String, PeerDb.Entry> currentPeers = new HashMap<>();
            peers.values().forEach(peer -> {
                if (peer.getAnnouncedAddress() != null
                        && peer.shareAddress()
                        && !peer.isBlacklisted()
                        && now - peer.getLastUpdated() < 7*24*3600) {
                    currentPeers.put(peer.getAnnouncedAddress(),
                            new PeerDb.Entry(peer.getAnnouncedAddress(), peer.getServices(), peer.getLastUpdated()));
                }
            });
            //
            // Build toDelete and toUpdate lists
            //
            List<PeerDb.Entry> toDelete = new ArrayList<>(oldPeers.size());
            oldPeers.forEach(entry -> {
                if (currentPeers.get(entry.getAddress()) == null)
                    toDelete.add(entry);
            });
            List<PeerDb.Entry> toUpdate = new ArrayList<>(currentPeers.size());
            currentPeers.values().forEach(entry -> {
                PeerDb.Entry oldEntry = oldMap.get(entry.getAddress());
                if (oldEntry == null || entry.getLastUpdated() - oldEntry.getLastUpdated() > 24*3600)
                    toUpdate.add(entry);
            });
            //
            // Nothing to do if all of the lists are empty
            //
            if (toDelete.isEmpty() && toUpdate.isEmpty()) {
                return;
            }
            //
            // Update the peer database
            //
            try {
                Db.db.beginTransaction();
                PeerDb.deletePeers(toDelete);
                PeerDb.updatePeers(toUpdate);
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Update the peer database when the services provided by a peer changes
     */
    static {
        Peers.addListener(peer -> peersService.submit(() -> {
            if (peer.getAnnouncedAddress() != null && !peer.isBlacklisted()) {
                try {
                    Db.db.beginTransaction();
                    PeerDb.updatePeer((PeerImpl) peer);
                    Db.db.commitTransaction();
                } catch (RuntimeException e) {
                    Logger.logErrorMessage("Unable to update peer database", e);
                    Db.db.rollbackTransaction();
                } finally {
                    Db.db.endTransaction();
                }
            }
        }), Peers.Event.CHANGE_SERVICES);
    }

    /**
     * Check for an old NRS version
     *
     * @param   version         Peer version
     * @param   minVersion      Minimum acceptable version
     * @return                  TRUE if this is an old version
     */
    static boolean isOldVersion(String version, int[] minVersion) {
        if (version == null) {
            return true;
        }
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        for (int i = 0; i < minVersion.length && i < versions.length; i++) {
            try {
                int v = Integer.parseInt(versions[i]);
                if (v > minVersion[i]) {
                    return false;
                } else if (v < minVersion[i]) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return versions.length < minVersion.length;
    }

    private static final int[] MAX_VERSION;
    static {
        String version = Nxt.VERSION;
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        MAX_VERSION = new int[versions.length];
        for (int i = 0; i < versions.length; i++) {
            MAX_VERSION[i] = Integer.parseInt(versions[i]);
        }
    }

    /**
     * Check for a new version
     *
     * @param   version         Peer version
     * @return                  TRUE if this is a newer version
     */
    static boolean isNewVersion(String version) {
        if (version == null) {
            return true;
        }
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        for (int i = 0; i < MAX_VERSION.length && i < versions.length; i++) {
            try {
                int v = Integer.parseInt(versions[i]);
                if (v > MAX_VERSION[i]) {
                    return true;
                } else if (v < MAX_VERSION[i]) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return versions.length > MAX_VERSION.length;
    }

    /**
     * Get the current blockchain state
     *
     * @return                  The blockchain state
     */
    public static Peer.BlockchainState getMyBlockchainState() {
        return Constants.isLightClient ? Peer.BlockchainState.LIGHT_CLIENT :
                (Nxt.getBlockchainProcessor().isDownloading() ||
                        Nxt.getBlockchain().getLastBlockTimestamp() < Nxt.getEpochTime() - 600) ?
                    Peer.BlockchainState.DOWNLOADING :
                        (Nxt.getBlockchain().getLastBlock().getBaseTarget() / Constants.INITIAL_BASE_TARGET > 10 &&
                                !Constants.isTestnet) ? Peer.BlockchainState.FORK :
                        Peer.BlockchainState.UP_TO_DATE;
    }

    /**
     * Update a bundler rate
     *
     * @param   sender          Peer sending the message
     * @param   rates           The bundler rates
     */
    static void updateBundlerRates(Peer sender, NetworkMessage.BundlerRateMessage msg) {
        boolean relayMessage = false;
        String address = msg.getAddress();
        if (address.equals("localhost")) {
            address = sender.getHost();
            msg.setAddress(address);
        }
        if (msg.getRates().isEmpty()) {
            if (bundlerRates.remove(address) != null) {
                relayMessage = true;
            }
        } else {
            NetworkMessage.BundlerRateMessage prevMsg = bundlerRates.get(address);
            if (prevMsg == null || prevMsg.getTimestamp() < msg.getTimestamp()) {
                bundlerRates.put(address, msg);
                relayMessage = true;
            }
        }
        if (relayMessage) {
            Logger.logDebugMessage("Relaying bundler rates for " + address);
            NetworkHandler.broadcastMessage(sender, msg);
        }
    }

    /**
     * Get the best bundler rates
     *
     * @return                  List of bundler rates
     */
    public static List<BundlerRate> getBestBundlerRates() {
        List<BundlerRate> bestRates = new ArrayList<>();
        int now = Nxt.getEpochTime();
        Map<ChildChain, Long> rateMap = new HashMap<>();
        Set<Map.Entry<String, NetworkMessage.BundlerRateMessage>> entries = bundlerRates.entrySet();
        Iterator<Map.Entry<String, NetworkMessage.BundlerRateMessage>> it = entries.iterator();
        while (it.hasNext()) {
            Map.Entry<String, NetworkMessage.BundlerRateMessage> entry = it.next();
            NetworkMessage.BundlerRateMessage msg = entry.getValue();
            if (msg.getTimestamp() < now - 65 * 60 && !entry.getKey().equals("localhost")) {
                it.remove();
                continue;
            }
            List<BundlerRate> rates = msg.getRates();
            rates.forEach(rate -> {
                Long prevRate = rateMap.get(rate.getChain());
                if (prevRate == null || rate.getRate() < prevRate) {
                    rateMap.put(rate.getChain(), rate.getRate());
                }
            });
        }
        rateMap.entrySet().forEach(entry -> bestRates.add(new BundlerRate(entry.getKey(), entry.getValue())));
        return bestRates;
    }

    /**
     * Broadcast bundler rates
     */
    public static void broadcastBundlerRates() {
        List<Bundler> bundlers = Bundler.getAllBundlers();
        if (bundlers.isEmpty()) {
            bundlerRates.remove("localhost");
        } else {
            List<BundlerRate> rates = new ArrayList<>();
            Map<ChildChain, Bundler> bundlerMap = new HashMap<>();
            bundlers.forEach(bundler -> {
                Bundler prevBundler = bundlerMap.get(bundler.getChildChain());
                if (prevBundler == null || bundler.getMinRateNQTPerFXT() < prevBundler.getMinRateNQTPerFXT()) {
                    bundlerMap.put(bundler.getChildChain(), bundler);
                }
            });
            bundlerMap.values().forEach(bundler ->
                rates.add(new BundlerRate(bundler.getChildChain(), bundler.getMinRateNQTPerFXT())));
            bundlerRates.put("localhost", new NetworkMessage.BundlerRateMessage("localhost", rates));
        }
        bundlersChanged = true;
    }

    /**
     * Send our bundler rates to a new peer
     *
     * @param   peer                Peer
     */
    public static void sendBundlerRates(Peer peer) {
        NetworkMessage.BundlerRateMessage msg = bundlerRates.get("localhost");
        if (msg != null) {
            peer.sendMessage(msg);
        }
    }

    private Peers() {} // never
}
