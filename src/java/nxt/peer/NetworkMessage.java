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

import nxt.Block;
import nxt.Nxt;
import nxt.NxtException.NotValidException;
import nxt.Transaction;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NetworkMessage represents the messages exchanged between peers.
 * <p>
 * Each network message has a common prefix followed by the message payload
 * <ul>
 * <li>Message name (string)
 * <li>Protocol level (short)
 * </ul>
 */
public abstract class NetworkMessage {

    /** Current protocol level - change this whenever a message format changes */
    private static final int PROTOCOL_LEVEL = 1;

    /** Minimum protocol level - message with a lower protocol level will be rejected */
    private static final int MIN_PROTOCOL_LEVEL = 1;

    /** Maximum byte array length */
    public static final int MAX_ARRAY_LENGTH = 4096;

    /** Maximum list size */
    public static final int MAX_LIST_SIZE = 1500;

    /** UTF-8 character set */
    private static final Charset UTF8;
    static {
        try {
            UTF8 = Charset.forName("UTF-8");
        } catch (Exception exc) {
            Logger.logErrorMessage("Unable to create UTF-8 character set", exc);
            throw new ExceptionInInitializerError("Unable to create UTF-8 character set");
        }
    }

    /** Message identifier counter */
    private static final AtomicLong nextMessageId = new AtomicLong();

    /** Message request processor map */
    private static final Map<String, NetworkMessage> processors = new HashMap<>();
    static {
        processors.put("AddPeers", new AddPeersMessage());
        processors.put("BlockIds", new BlockIdsMessage());
        processors.put("BlockInventory", new BlockInventoryMessage());
        processors.put("Blocks", new BlocksMessage());
        processors.put("CumulativeDifficulty", new CumulativeDifficultyMessage());
        processors.put("Error", new ErrorMessage());
        processors.put("GetBlocks", new GetBlocksMessage());
        processors.put("GetCumulativeDifficulty", new GetCumulativeDifficultyMessage());
        processors.put("GetInfo", new GetInfoMessage());
        processors.put("GetMilestoneBlockIds", new GetMilestoneBlockIdsMessage());
        processors.put("GetNextBlockIds", new GetNextBlockIdsMessage());
        processors.put("GetNextBlocks", new GetNextBlocksMessage());
        processors.put("GetPeers", new GetPeersMessage());
        processors.put("GetTransactions", new GetTransactionsMessage());
        processors.put("GetUnconfirmedTransactions", new GetUnconfirmedTransactionsMessage());
        processors.put("MilestoneBlockIds", new MilestoneBlockIdsMessage());
        processors.put("Transactions", new TransactionsMessage());
        processors.put("TransactionsInventory", new TransactionsInventoryMessage());
    }

    /** Message protocol level */
    private int protocolLevel;

    /** Message name bytes */
    private byte[] messageNameBytes;

    /** Message identifier */
    protected long messageId;

    /**
     * Create a new network message
     *
     * @param   messageName             Message name
     */
    private NetworkMessage(String messageName) {
        this.messageNameBytes = messageName.getBytes(UTF8);
        this.protocolLevel = PROTOCOL_LEVEL;
    }

    /**
     * Create a new network message
     *
     * @param   messageName                 Message name
     * @param   bytes                       Message bytes
     * @throws  BufferUnderflowException    Message is too short
     * @throws  NetworkException            Message is not valid
     */
    private NetworkMessage(String messageName, ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
        this.messageNameBytes = messageName.getBytes(UTF8);
        this.protocolLevel = (int)bytes.getShort() & 0xffff;
        if (this.protocolLevel < MIN_PROTOCOL_LEVEL) {
            throw new NetworkException("Protocol level " + this.protocolLevel + " is not accepted");
        }
    }

    /**
     * Get the network message from the message bytes
     *
     * @param   bytes                   Message bytes
     * @return                          Message
     * @throws  NetworkException        Message is not valid
     */
    static NetworkMessage getMessage(ByteBuffer bytes) throws NetworkException {
        NetworkMessage networkMessage;
        int length = (int)bytes.get() & 0xff;
        if (length < 1) {
            throw new NetworkException("Message name missing");
        }
        byte[] nameBytes = new byte[length];
        bytes.get(nameBytes);
        String messageName = new String(nameBytes);
        NetworkMessage processor = processors.get(messageName);
        try {
            if (processor != null) {
                networkMessage = processor.constructMessage(bytes);
            } else {
                throw new NetworkException("'" + messageName + "' is not a valid peer message");
            }
        } catch (BufferUnderflowException exc) {
            throw new NetworkException("'" + messageName + "' message is too short", exc);
        } catch (BufferOverflowException exc) {
            throw new NetworkException("'" + messageName + "' message buffer is too small", exc);
        }
        return networkMessage;
    }

    /**
     * Process the network message
     *
     * @param   peer                    Peer
     * @return                          Response message or null if this is a response
     */
    NetworkMessage processMessage(PeerImpl peer) {
        return null;
    }

    /**
     * Construct the message
     *
     * @param   bytes                       Message bytes following the message name
     * @return                              Message
     * @throws  BufferOverflowException     Message buffer is too small
     * @throws  BufferUnderflowException    Message is too short
     * @throws  NetworkException            Message is not valid
     */
    protected NetworkMessage constructMessage(ByteBuffer bytes)
                                            throws BufferOverflowException, BufferUnderflowException, NetworkException {
        throw new RuntimeException("Required message processor missing");  // Should never happen
    }

    /**
     * Get the message length
     *
     * @return                          Message length
     */
    int getLength() {
        return 1 + messageNameBytes.length + 2;
    }

    /**
     * Get the network bytes
     *
     * @param   bytes                       Byte buffer
     * @throws  BufferOverflowException     Message buffer is too small
     */
    void getBytes(ByteBuffer bytes) throws BufferOverflowException {
        bytes.put((byte)messageNameBytes.length).put(messageNameBytes).putShort((short)protocolLevel);
    }

    /**
     * Get the message identifier
     *
     * @return                              Message identifier
     */
    long getMessageId() {
        return messageId;
    }

    /**
     * Get the message name
     *
     * @return                              Message name
     */
    String getMessageName() {
        return new String(messageNameBytes, UTF8);
    }

    /**
     * Check if the message requires a response
     *
     * @return                              TRUE if the message requires a response
     */
    boolean requiresResponse() {
        return false;
    }

    /**
     * Check if the message is a response
     *
     * @return                              TRUE if this is a response message
     */
    boolean isResponse() {
        return false;
    }

    /**
     * Check if blockchain download is not allowed
     *
     * @return                              TRUE if blockchain download is not allowed
     */
    boolean downloadNotAllowed() {
        return false;
    }

    /**
     * Get the length of an encoded array
     *
     * @return                              Encoded array length
     */
    private static int getEncodedArrayLength(byte[] bytes) {
        int length = bytes.length;
        if (length < 254) {
            length++;
        } else if (length < 65536) {
            length += 3;
        } else {
            length += 5;
        }
        return length;
    }

    /**
     * Encode a byte array
     *
     * A byte array is encoded as a variable length field followed by the array bytes
     *
     * @param   bytes                       Byte buffer
     * @param   arrayBytes                  Array bytes
     * @throws  BufferOverflowException     Byte buffer is too small
     */
    private static void encodeArray(ByteBuffer bytes, byte[] arrayBytes) throws BufferOverflowException {
        if (arrayBytes.length > MAX_ARRAY_LENGTH) {
            throw new RuntimeException("Array length " + arrayBytes.length + " exceeds the maximum of " + MAX_ARRAY_LENGTH);
        }
        if (arrayBytes.length < 254) {
            bytes.put((byte)arrayBytes.length);
        } else if (arrayBytes.length < 65536) {
            bytes.put((byte)254).putShort((short)arrayBytes.length);
        } else {
            bytes.put((byte)255).putInt(arrayBytes.length);
        }
        if (arrayBytes.length > 0) {
            bytes.put(arrayBytes);
        }
    }

    /**
     * Decode a byte array
     *
     * A byte array is encoded as a variable length field followed by the array bytes
     *
     * @param   bytes                       Byte buffer
     * @return                              Array bytes
     * @throws  BufferUnderflowException    Message is too short
     * @throws  NetworkException            Message is not valid
     */
    private static byte[] decodeArray(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
        int length = (int)bytes.get() & 0xff;
        if (length == 254) {
            length = (int)bytes.getShort() & 0xffff;
        } else if (length == 255) {
            length = bytes.getInt();
        }
        if (length > MAX_ARRAY_LENGTH) {
            throw new NetworkException("Array length " + length + " exceeds the maximum of " + MAX_ARRAY_LENGTH);
        }
        byte[] arrayBytes = new byte[length];
        if (length > 0) {
            bytes.get(arrayBytes);
        }
        return arrayBytes;
    }

    /**
     * The GetInfo message is exchanged when a peer connection is established.  There is no response message.
     * <ul>
     * <li>Application name (string)
     * <li>Application version (string)
     * <li>Application platform (string)
     * <li>Share address (boolean)
     * <li>Announced address (string)
     * <li>API port (short)
     * <li>SSL port (short)
     * <li>Available services (long)
     * <li>Disabled APIs (string)
     * <li>APIServer idle timeout (int)
     * </ul>
     */
    public static class GetInfoMessage extends NetworkMessage {

        /** Application name */
        private final byte[] appNameBytes;

        /** Application platform */
        private final byte[] appPlatformBytes;

        /** Application version */
        private final byte[] appVersionBytes;

        /** Share address */
        private final boolean shareAddress;

        /** Announced address */
        private final byte[] announcedAddressBytes;

        /** API port */
        private final int apiPort;

        /** SSL port */
        private final int sslPort;

        /** Available services */
        private final long services;

        /** Disabled API (base64 encoded) */
        private final byte[] disabledAPIsBytes;

        /** APIServer idle timeout */
        private final int apiServerIdleTimeout;

        /** Blockchain state */
        //TODO: use a separate NetworkMessage to carry blockchainState
        //private final int blockchainState;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetInfoMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message or null
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetInfo.processRequest(peer, this);
        }

        /**
         * Construct a GetInfo message
         */
        private GetInfoMessage() {
            super("GetInfo");
            this.appNameBytes = null;
            this.appVersionBytes = null;
            this.appPlatformBytes = null;
            this.shareAddress = false;
            this.announcedAddressBytes = null;
            this.apiPort = 0;
            this.sslPort = 0;
            this.services = 0;
            this.disabledAPIsBytes = null;
            this.apiServerIdleTimeout = 0;
        }

        /**
         * Construct a GetInfo message
         *
         * @param   appName             Application name
         * @param   appVersion          Application version
         * @param   appPlatform         Application platform
         * @param   shareAddress        TRUE to share the network address with peers
         * @param   announcedAddress    Announced address or null
         * @param   apiPort             API port
         * @param   sslPort             API SSL port
         * @param   services            Available application services
         */
        public GetInfoMessage(String appName, String appVersion, String appPlatform,
                              boolean shareAddress, String announcedAddress,
                              int apiPort, int sslPort, long services,
                              String disabledAPIs, int apiServerIdleTimeout) {
            super("GetInfo");
            this.appNameBytes = appName.getBytes(UTF8);
            this.appVersionBytes = appVersion.getBytes(UTF8);
            this.appPlatformBytes = appPlatform.getBytes(UTF8);
            this.shareAddress = shareAddress;
            this.announcedAddressBytes = (announcedAddress != null ? announcedAddress.getBytes(UTF8) : Convert.EMPTY_BYTE);
            this.apiPort = apiPort;
            this.sslPort = sslPort;
            this.services = services;
            this.disabledAPIsBytes = (disabledAPIs != null ? disabledAPIs.getBytes(UTF8) : Convert.EMPTY_BYTE);
            this.apiServerIdleTimeout = apiServerIdleTimeout;
        }

        /**
         * Construct a GetInfo message
         *
         * @param   bytes                       Message bytes following the message name
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        private GetInfoMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetInfo", bytes);
            this.appNameBytes = decodeArray(bytes);
            this.appVersionBytes = decodeArray(bytes);
            this.appPlatformBytes = decodeArray(bytes);
            this.shareAddress = (bytes.get() != 0);
            this.announcedAddressBytes = decodeArray(bytes);
            this.apiPort = (int)bytes.getShort() & 0xffff;
            this.sslPort = (int)bytes.getShort() & 0xffff;
            this.services = bytes.getLong();
            this.disabledAPIsBytes = decodeArray(bytes);
            this.apiServerIdleTimeout = bytes.getInt();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength()
                    + getEncodedArrayLength(appNameBytes)
                    + getEncodedArrayLength(appVersionBytes)
                    + getEncodedArrayLength(appPlatformBytes)
                    + 1
                    + getEncodedArrayLength(announcedAddressBytes)
                    + 2 + 2 + 8
                    + getEncodedArrayLength(disabledAPIsBytes)
                    + 4;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                   Byte buffer
         * @throws  BufferOverflowException Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            encodeArray(bytes, appNameBytes);
            encodeArray(bytes, appVersionBytes);
            encodeArray(bytes, appPlatformBytes);
            bytes.put(shareAddress ? (byte)1 : (byte)0);
            encodeArray(bytes, announcedAddressBytes);
            bytes.putShort((short)apiPort).putShort((short)sslPort).putLong(services);
            encodeArray(bytes, disabledAPIsBytes);
            bytes.putInt(apiServerIdleTimeout);
        }

        /**
         * Get the application name
         *
         * @return                      Application name or null if no name specified
         */
        public String getApplicationName() {
            return (appNameBytes.length > 0 ? new String(appNameBytes, UTF8) : null);
        }

        /**
         * Get the application version
         *
         * @return                      Application version or null if no version specified
         */
        public String getApplicationVersion() {
            return (appVersionBytes.length > 0 ? new String(appVersionBytes, UTF8) : null);
        }

        /**
         * Get the application platform
         *
         * @return                      Application platform or null if no platform specified
         */
        public String getApplicationPlatform() {
            return (appPlatformBytes.length > 0 ? new String(appPlatformBytes, UTF8) : null);
        }

        /**
         * Check if the network address should be shared
         *
         * @return                      TRUE if the address should be shared
         */
        public boolean getShareAddress() {
            return shareAddress;
        }

        /**
         * Get the announced address
         *
         * @return                      Announced address or null if no address specified
         */
        public String getAnnouncedAddress() {
            return (announcedAddressBytes.length > 0 ? new String(announcedAddressBytes, UTF8) : null);
        }

        /**
         * Get the API port
         *
         * @return                      API port
         */
        public int getApiPort() {
            return apiPort;
        }

        /**
         * Get the SSL port
         *
         * @return                      SSL port
         */
        public int getSslPort() {
            return sslPort;
        }

        /**
         * Get the available services
         *
         * @return                      Service bits
         */
        public long getServices() {
            return services;
        }

        /**
         * Get the disabledAPIs
         *
         * @return                      disabledAPIs as base64 encoded string
         */
        public String getDisabledAPIs() {
            return (disabledAPIsBytes.length > 0 ? new String(disabledAPIsBytes, UTF8) : null);
        }

        /**
         * Get the API server idle timeout
         *
         * @return                      APIServer idle timeout
         */
        public int getApiServerIdleTimeout() {
            return apiServerIdleTimeout;
        }
    }

    /**
     * The GetCumulativeDifficulty message is sent to a peer to get the current blockchain status.  The
     * peer responds with a CumulativeDifficulty message.
     * <ul>
     * <li>Message identifier (long)
     * </ul>
     */
    public static class GetCumulativeDifficultyMessage extends NetworkMessage {

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetCumulativeDifficultyMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message or null
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetCumulativeDifficulty.processRequest(peer, this);
        }

        /**
         * Construct a GetCumulativeDifficulty message
         */
        public GetCumulativeDifficultyMessage() {
            super("GetCumulativeDifficulty");
            this.messageId = nextMessageId.incrementAndGet();
        }

        /**
         * Construct a GetCumulativeDifficulty message
         *
         * @param   bytes                       Message bytes following the message name
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetCumulativeDifficultyMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetCumulativeDifficulty", bytes);
            this.messageId = bytes.getLong();
        }

        /**
         * Get the message length
         *
         * @return                          Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                   Byte buffer
         * @throws  BufferOverflowException Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }
    }

    /**
     * The CumulativeDifficulty message is returned in response to the GetCumulativeDifficulty message.
     * The message identifier is obtained from the GetCumulativeDifficulty message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Cumulative difficulty (big integer)
     * <li>Block height (integer)
     * </ul>
     */
    public static class CumulativeDifficultyMessage extends NetworkMessage {

        /** Cumulative difficulty */
        private final byte[] cumulativeDifficultyBytes;

        /** Block height */
        private final int blockHeight;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new CumulativeDifficultyMessage(bytes);
        }

        /**
         * Construct a CumulaltiveDifficulty message
         */
        private CumulativeDifficultyMessage() {
            super("CumulativeDifficulty");
            this.messageId = 0;
            this.cumulativeDifficultyBytes = null;
            this.blockHeight = 0;
        }

        /**
         * Construct a CumulativeDifficulty message
         *
         * @param   messageId               Message identifier from the GetCumulativeDifficulty message
         * @param   cumulativeDifficulty    Cumulative difficulty
         * @param   blockHeight             Block height
         */
        public CumulativeDifficultyMessage(long messageId, BigInteger cumulativeDifficulty, int blockHeight) {
            super("CumulativeDifficulty");
            this.messageId = messageId;
            this.cumulativeDifficultyBytes = cumulativeDifficulty.toByteArray();
            this.blockHeight = blockHeight;
        }

        /**
         * Construct a CumulativeDifficulty message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private CumulativeDifficultyMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("CumulativeDifficulty", bytes);
            this.messageId = bytes.getLong();
            this.cumulativeDifficultyBytes = decodeArray(bytes);
            this.blockHeight = bytes.getInt();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + getEncodedArrayLength(cumulativeDifficultyBytes) + 4;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            encodeArray(bytes, cumulativeDifficultyBytes);
            bytes.putInt(blockHeight);
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the cumulative difficulty
         *
         * @return                      Cumulative difficulty
         */
        public BigInteger getCumulativeDifficulty() {
            return new BigInteger(cumulativeDifficultyBytes);
        }

        /**
         * Get the block height
         *
         * @return                      Block height
         */
        public int getBlockHeight() {
            return blockHeight;
        }
    }

    /**
     * The GetPeers message is sent to a peer to request a list of connected peers.  The AddPeers
     * message is returned as an asynchronous response.
     */
    public static class GetPeersMessage extends NetworkMessage {
/**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetPeersMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetPeers.processRequest(peer, this);
        }

        /**
         * Construct a GetPeers message
         */
        public GetPeersMessage() {
            super("GetPeers");
        }

        /**
         * Construct a GetPeers message
         *
         * @param   bytes                       Message bytes following the message name
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetPeersMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetPeers", bytes);
        }

        /**
         * Get the message length
         *
         * @return                          Message length
         */
        @Override
        int getLength() {
            return super.getLength();
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                   Byte buffer
         * @throws  BufferOverflowException Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
        }
    }

    /**
     * The AddPeers message is sent to a peer to update its peer list and it is also returned
     * as an asynchronous response to the GetPeers message
     * <ul>
     * <li>Peer list
     * </ul>
     * <p>
     * Each entry in the peer list has the following format:
     * <ul>
     * <li>Announced address (string)
     * <li>Available services (long)
     * </ul>
     */
    public static class AddPeersMessage extends NetworkMessage {

        /** Announced addresses */
        private final List<byte[]> announcedAddressesBytes;

        /** Announced addresses length */
        private int announcedAddressesLength;

        /** Services */
        private final List<Long> services;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new AddPeersMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return AddPeers.processRequest(peer, this);
        }

        /**
         * Construct an AddPeers message
         */
        private AddPeersMessage() {
            super("AddPeers");
            this.announcedAddressesBytes = null;
            this.announcedAddressesLength = 0;
            this.services = null;
        }

        /**
         * Construct an AddPeers message
         *
         * @param   peerList                Peer list
         */
        public AddPeersMessage(List<? extends Peer> peerList) {
            super("AddPeers");
            if (peerList.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + peerList.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            announcedAddressesBytes = new ArrayList<>(peerList.size());
            announcedAddressesLength = 0;
            services = new ArrayList<>(peerList.size());
            peerList.forEach(peer -> {
                String addr = peer.getAnnouncedAddress();
                if (addr != null) {
                    byte[] addrBytes = addr.getBytes(UTF8);
                    announcedAddressesBytes.add(addrBytes);
                    announcedAddressesLength += getEncodedArrayLength(addrBytes);
                    services.add(((PeerImpl)peer).getServices());
                }
            });
        }

        /**
         * Construct an AddPeers message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private AddPeersMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("AddPeers", bytes);
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + "exceeds the maximum of " + MAX_LIST_SIZE);
            }
            announcedAddressesBytes = new ArrayList<>(count);
            announcedAddressesLength = 0;
            services = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                byte[] addressBytes = decodeArray(bytes);
                announcedAddressesBytes.add(addressBytes);
                announcedAddressesLength += getEncodedArrayLength(addressBytes);
                services.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 2 + announcedAddressesLength + (8 * services.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putShort((short)announcedAddressesBytes.size());
            for (int i=0; i<announcedAddressesBytes.size(); i++) {
                encodeArray(bytes, announcedAddressesBytes.get(i));
                bytes.putLong(services.get(i));
            }
        }

        /**
         * Get the announced addresses
         *
         * @return                          Announced addresses
         */
        public List<String> getAnnouncedAddresses() {
            List<String> addresses = new ArrayList<>(announcedAddressesBytes.size());
            announcedAddressesBytes.forEach((addressBytes) -> addresses.add(new String(addressBytes, UTF8)));
            return addresses;
        }

        /**
         * Get the services
         *
         * @return                          Services
         */
        public List<Long> getServices() {
            return services;
        }
    }

    /**
     * The GetMilestoneBlockIds message is sent when a peer is downloading the blockchain.
     * The MilestoneBlockIds message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Last block identifier (long)
     * <li>Last milestone block identifier (long)
     * </ul>
     */
    public static class GetMilestoneBlockIdsMessage extends NetworkMessage {

        /** Last block identifier */
        private final long lastBlockId;

        /** Last milestone block identifier */
        private final long lastMilestoneBlockId;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetMilestoneBlockIdsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetMilestoneBlockIds.processRequest(peer, this);
        }

        /**
         * Construct a GetMilestoneBlockIds message
         */
        private GetMilestoneBlockIdsMessage() {
            super("GetMilestoneBlockIds");
            this.messageId = 0;
            this.lastBlockId = 0;
            this.lastMilestoneBlockId = 0;
        }

        /**
         * Construct a GetMilestoneBlockIds message
         *
         * @param   lastBlockId             Last block identifier or 0
         * @param   lastMilestoneBlockId    Last milestone block identifier or 0
         */
        public GetMilestoneBlockIdsMessage(long lastBlockId, long lastMilestoneBlockId) {
            super("GetMilestoneBlockIds");
            this.messageId = nextMessageId.incrementAndGet();
            this.lastBlockId = lastBlockId;
            this.lastMilestoneBlockId = lastMilestoneBlockId;
        }

        /**
         * Construct a GetMilestoneBlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetMilestoneBlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetMilestoneBlockIds", bytes);
            this.messageId = bytes.getLong();
            this.lastBlockId = bytes.getLong();
            this.lastMilestoneBlockId = bytes.getLong();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 8;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId).putLong(lastBlockId).putLong(lastMilestoneBlockId);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the last block identifier
         *
         * @return                          Last block identifier or 0
         */
        public long getLastBlockId() {
            return lastBlockId;
        }

        /**
         * Get the last milestone block identifier
         *
         * @return                          Last milestone block identifier or 0
         */
        public long getLastMilestoneBlockIdentifier() {
            return lastMilestoneBlockId;
        }
    }

    /**
     * The MilestoneBlockIds message is returned in response to the GetMilestoneBlockIds message.
     * The message identifier is obtained from the GetMilestoneBlockIds message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Last block indicator (boolean)
     * <li>Block identifier list (long)
     * </ul>
     */
    public static class MilestoneBlockIdsMessage extends NetworkMessage {

        /** Last block indicator */
        private final boolean isLastBlock;

        /** Block identifiers */
        private final List<Long> blockIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new MilestoneBlockIdsMessage(bytes);
        }

        /**
         * Construct a MilestoneBlockIds message
         */
        private MilestoneBlockIdsMessage() {
            super("MilestoneBlockIds");
            this.messageId = 0;
            this.isLastBlock = false;
            this.blockIds = null;
        }

        /**
         * Construct a MilestoneBlockIds message
         *
         * @param   messageId               Message identifier
         * @param   isLastBlock             Last block indicator
         * @param   blockIds                Block identifier list
         */
        public MilestoneBlockIdsMessage(long messageId, boolean isLastBlock, List<Long> blockIds) {
            super("MilestoneBlockIds");
            if (blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            this.isLastBlock = isLastBlock;
            this.blockIds = blockIds;
        }

        /**
         * Construct a MilestoneBlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private MilestoneBlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("MilestoneBlockIds", bytes);
            this.messageId = bytes.getLong();
            this.isLastBlock = (bytes.get() != 0);
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 1 + 2 + (8 * blockIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.put(isLastBlock ? (byte)1 : (byte)0);
            bytes.putShort((short)blockIds.size());
            blockIds.forEach((id) -> bytes.putLong(id));
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the last block indicator
         *
         * @return                          Last block indicator
         */
        public boolean isLastBlock() {
            return isLastBlock;
        }

        /**
         * Get the milestone block identifiers
         *
         * @return                          Milestone block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }
    }

    /**
     * The GetNextBlockIds message is sent when a peer is downloading the blockchain.
     * The BlockIds message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Start block identifier (long)
     * <li>Maximum number of blocks (integer)
     * </ul>
     */
    public static class GetNextBlockIdsMessage extends NetworkMessage {

        /** Start block identifier */
        private final long blockId;

        /** Maximum number of blocks */
        private final int limit;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetNextBlockIdsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetNextBlockIds.processRequest(peer, this);
        }

        /**
         * Construct a GetNextBlockIds message
         */
        private GetNextBlockIdsMessage() {
            super("GetNextBlockIds");
            this.messageId = 0;
            this.blockId = 0;
            this.limit = 0;
        }

        /**
         * Construct a GetNextBlockIds message
         *
         * @param   blockId                 Start block identifier
         * @param   limit                   Maximum number of blocks
         */
        public GetNextBlockIdsMessage(long blockId, int limit) {
            super("GetNextBlockIds");
            this.messageId = nextMessageId.incrementAndGet();
            this.blockId = blockId;
            this.limit = limit;
        }

        /**
         * Construct a GetNextBlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetNextBlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetNextBlockIds", bytes);
            this.messageId = bytes.getLong();
            this.blockId = bytes.getLong();
            this.limit = bytes.getInt();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 4;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId).putLong(blockId).putInt(limit);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the start block identifier
         *
         * @return                          Start block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the limit
         *
         * @return                          Limit
         */
        public int getLimit() {
            return limit;
        }
    }

    /**
     * The BlockIds message is returned in response to the GetNextBlockIds message.
     * The message identifier is obtained from the GetNextBlockIds message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Block identifier list (long)
     * </ul>
     */
    public static class BlockIdsMessage extends NetworkMessage {

        /** Block identifiers */
        private final List<Long> blockIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlockIdsMessage(bytes);
        }

        /**
         * Construct a BlockIds message
         */
        private BlockIdsMessage() {
            super("BlockIds");
            messageId = 0;
            blockIds = null;
        }

        /**
         * Construct a BlockIds message
         *
         * @param   messageId               Message identifier
         * @param   blockIds                Block identifier list
         */
        public BlockIdsMessage(long messageId, List<Long> blockIds) {
            super("BlockIds");
            if (blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            this.blockIds = blockIds;
        }

        /**
         * Construct a BlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("BlockIds", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + (8 * blockIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)blockIds.size());
            blockIds.forEach((id) -> bytes.putLong(id));
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the block identifiers
         *
         * @return                          Block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }
    }

    /**
     * The GetNextBlocks message is sent when a peer is downloading the blockchain.
     * The Blocks message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Start block identifier (long)
     * <li>Maximum number of blocks (integer)
     * <li>Block identifier list (long)
     * </ul>
     */
    public static class GetNextBlocksMessage extends NetworkMessage {

        /** Start block identifier */
        private final long blockId;

        /** Maximum number of blocks */
        private final int limit;

        /** Block identifier list */
        private final List<Long> blockIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetNextBlocksMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetNextBlocks.processRequest(peer, this);
        }

        /**
         * Construct a GetNextBlocks message
         */
        private GetNextBlocksMessage() {
            super("GetNextBlocks");
            this.messageId = 0;
            this.blockId = 0;
            this.limit = 0;
            this.blockIds = null;
        }

        /**
         * Construct a GetNextBlocks message
         *
         * @param   blockId                 Start block identifier
         * @param   limit                   Maximum number of blocks
         * @param   blockIds                Block identifier list or null
         */
        public GetNextBlocksMessage(long blockId, int limit, List<Long> blockIds) {
            super("GetNextBlocks");
            if (blockIds != null && blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.blockId = blockId;
            this.limit = limit;
            this.blockIds = (blockIds != null ? blockIds : Collections.emptyList());
        }

        /**
         * Construct a GetNextBlocks message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetNextBlocksMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetNextBlocks", bytes);
            this.messageId = bytes.getLong();
            this.blockId = bytes.getLong();
            this.limit = bytes.getInt();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 4 + 2 + (8 * blockIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putLong(blockId).putInt(limit).putShort((short)blockIds.size());
            blockIds.forEach((id) -> bytes.putLong(id));
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the start block identifier
         *
         * @return                          Start block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the maximum number of blocks
         *
         * @return                          Maximum number of blocks
         */
        public int getLimit() {
            return limit;
        }

        /**
         * Get the block identifiers
         *
         * @return                          Block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }
    }

    /**
     * The GetBlocks message is sent when a peer is notified that a new block is available.
     * The Blocks message is returned in response.  The sender can include a list of transactions
     * to be excluded when creating the Blocks message.  The sender must then supply
     * the excluded transactions when it receives the Blocks message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Block identifier list (long)
     * <li>Transaction exclusion list (long)
     * </ul>
     */
    public static class GetBlocksMessage extends NetworkMessage {

        /** Block identifier list */
        private final List<Long> blockIds;

        /** Transaction exclusion list */
        private final List<Long> excludedTransactions;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetBlocksMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetBlocks.processRequest(peer, this);
        }

        /**
         * Construct a GetBlocks message
         */
        private GetBlocksMessage() {
            super("GetBlocks");
            messageId = 0;
            blockIds = null;
            excludedTransactions = null;
        }

        /**
         * Construct a GetBlocks message
         *
         * @param   blockIds                Block identifiers
         */
        public GetBlocksMessage(List<Long> blockIds) {
            this(blockIds, null);
        }

        /**
         * Construct a GetBlocks message
         *
         * Transactions can be excluded
         *
         * @param   blockIds                Block identifiers
         * @param   excludedTransactions    Excluded transactions or null
         */
        public GetBlocksMessage(List<Long> blockIds, List<Long> excludedTransactions) {
            super("GetBlocks");
            if (blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            if (excludedTransactions != null && excludedTransactions.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + excludedTransactions.size() + " exceeds maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.blockIds = blockIds;
            this.excludedTransactions = (excludedTransactions != null ? excludedTransactions : Collections.emptyList());
        }

        /**
         * Construct a GetBlocks message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetBlocksMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetBlocks", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
            count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            excludedTransactions = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                excludedTransactions.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + (8 * blockIds.size()) + 2 + (8 * excludedTransactions.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)blockIds.size());
            blockIds.forEach((id) -> bytes.putLong(id));
            bytes.putShort((short)excludedTransactions.size());
            excludedTransactions.forEach((id) -> bytes.putLong(id));
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the block identifiers
         *
         * @return                          Block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }

        /**
         * Get the excluded transaction identifiers
         *
         * @return                          Transaction identifiers
         */
        public List<Long> getExcludedTransactions() {
            return excludedTransactions;
        }
    }

    /**
     * The Blocks message is returned in response to the GetBlocks and GetNextBlocks message.
     * The message identifier is obtained from the request message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Block list
     * </ul>
     */
    public static class BlocksMessage extends NetworkMessage {

        /** Blocks */
        private final List<BlockBytes> blockBytes;

        /** Total length */
        private int totalBlockLength;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlocksMessage(bytes);
        }

        /**
         * Construct a Blocks message
         */
        private BlocksMessage() {
            super("Blocks");
            messageId = 0;
            blockBytes = null;
            totalBlockLength = 0;
        }

        /**
         * Construct a Blocks message
         *
         * @param   messageId               Message identifier
         * @param   blocks                  Block list
         */
        public BlocksMessage(long messageId, List<? extends Block> blocks) {
            super("Blocks");
            if (blocks.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blocks.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            blockBytes = new ArrayList<>(blocks.size());
            totalBlockLength = 0;
            blocks.forEach((block) -> {
                BlockBytes bytes = new BlockBytes(block);
                blockBytes.add(bytes);
                totalBlockLength += bytes.getLength();
            });
        }

        /**
         * Construct a Blocks message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlocksMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("Blocks", bytes);
            messageId = bytes.getLong();
            int blockCount = (int)bytes.getShort() & 0xffff;
            if (blockCount > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + blockCount + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            blockBytes = new ArrayList<>(blockCount);
            totalBlockLength = 0;
            for (int i=0; i<blockCount; i++) {
                BlockBytes block = new BlockBytes(bytes);
                blockBytes.add(block);
                totalBlockLength += block.getLength();
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + totalBlockLength;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)blockBytes.size());
            blockBytes.forEach((block) -> block.getBytes(bytes));
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the number of blocks
         *
         * @return                          Number of blocks
         */
        public int getBlockCount() {
            return blockBytes.size();
        }

        /**
         * Get the blocks
         *
         * This method cannot be used if blocks were excluded in the GetBlocks message
         *
         * @return                          Block list
         * @throws  NotValidException       Block is not valid
         */
        public List<Block> getBlocks() throws NotValidException {
            List<Block> blocks = new ArrayList<>(blockBytes.size());
            for (BlockBytes bytes : blockBytes) {
                blocks.add(bytes.getBlock());
            }
            return blocks;
        }

        /**
         * Get the blocks
         *
         * This method must be used if blocks were excluded in the GetBlocks message
         *
         * @param   excludedTransactions    Transactions that were excluded from the blocks
         * @return                          Block list
         * @throws  NotValidException       Block is not valid
         */
        public List<Block> getBlocks(List<Transaction> excludedTransactions) throws NotValidException {
            List<Block> blocks = new ArrayList<>(blockBytes.size());
            for (BlockBytes bytes : blockBytes) {
                blocks.add(bytes.getBlock(excludedTransactions));
            }
            return blocks;
        }
    }

    /**
     * The GetTransactions message is sent to retrieve one or more transactions.
     * The Transactions message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Transaction identifier list (long)
     * </ul>
     */
    public static class GetTransactionsMessage extends NetworkMessage {

        /** Transaction identifier list */
        private final List<Long> transactionIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetTransactionsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetTransactions.processRequest(peer, this);
        }

        /**
         * Construct a GetTransactions message
         */
        private GetTransactionsMessage() {
            super("GetTransactions");
            this.messageId = 0;
            this.transactionIds = null;
        }

        /**
         * Construct a GetTransactions message
         *
         * @param   transactionIds              Transaction identifiers
         */
        public GetTransactionsMessage(List<Long> transactionIds) {
            super("GetTransactions");
            if (transactionIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactionIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.transactionIds = transactionIds;
        }

        /**
         * Construct a GetTransactions message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetTransactionsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetTransactions", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.transactionIds = new ArrayList<>(count);
            for (int i=0; i < count; i++) {
                transactionIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        public int getLength() {
            return super.getLength() + 8 + 2 + (8 * transactionIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        public void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)transactionIds.size());
            transactionIds.forEach((id) -> bytes.putLong(id));
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the transaction identifiers
         *
         * @return                          Transaction identifiers
         */
        public List<Long> getTransactionIds() {
            return transactionIds;
        }
    }

    /**
     * The GetUnconfirmedTransactions message is sent to retrieve the current set
     * of unconfirmed transactions.
     * The Transactions message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Transaction exclusion list (long)
     * </ul>
     */
    public static class GetUnconfirmedTransactionsMessage extends NetworkMessage {

        /** Transaction exclusion list */
        private final List<Long> exclusionIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetUnconfirmedTransactionsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return GetUnconfirmedTransactions.processRequest(peer, this);
        }

        /**
         * Construct a GetUnconfirmedTransactions message
         */
        private GetUnconfirmedTransactionsMessage() {
            super("GetUnconfirmedTransactions");
            this.messageId = 0;
            this.exclusionIds = null;
        }

        /**
         * Construct a GetUnconfirmedTransactions message
         *
         * @param   exclusionIds                Sorted list of excluded transaction identifiers
         */
        public GetUnconfirmedTransactionsMessage(List<Long> exclusionIds) {
            super("GetUnconfirmedTransactions");
            if (exclusionIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + exclusionIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.exclusionIds = exclusionIds;
        }

        /**
         * Construct a GetUnconfirmedTransactions message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetUnconfirmedTransactionsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetUnconfirmedTransactions", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.exclusionIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                exclusionIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + (8 * exclusionIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)exclusionIds.size());
            exclusionIds.forEach((id) -> bytes.putLong(id));
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the exclusions
         *
         * @return                          Exclusions
         */
        public List<Long> getExclusions() {
            return exclusionIds;
        }
    }

    /**
     * The Transactions message is returned in response to the GetTransactions and
     * GetUnconfirmedTransactions messages.
     * The message identifier is obtained from the request message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Transaction list
     * </ul>
     */
    public static class TransactionsMessage extends NetworkMessage {

        /** Transactions */
        private final List<TransactionBytes> transactionBytes;

        /** Total length */
        private int totalTransactionLength;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new TransactionsMessage(bytes);
        }

        /**
         * Construct a Transactions message
         */
        private TransactionsMessage() {
            super("Transactions");
            this.messageId = 0;
            this.transactionBytes = null;
            this.totalTransactionLength = 0;
        }

        /**
         * Construct a Transactions message
         *
         * @param   messageId               Message identifier
         * @param   transactions            Transaction list
         */
        public TransactionsMessage(long messageId, List<? extends Transaction> transactions) {
            super("Transactions");
            if (transactions.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactions.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            this.transactionBytes = new ArrayList<>(transactions.size());
            totalTransactionLength = 0;
            transactions.forEach((tx) -> {
                TransactionBytes bytes = new TransactionBytes(tx);
                transactionBytes.add(bytes);
                totalTransactionLength += bytes.getLength();
            });
        }

        /**
         * Construct a Transactions message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private TransactionsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("Transactions", bytes);
            this.messageId = bytes.getLong();
            int transactionCount = (int)bytes.getShort() & 0xffff;
            if (transactionCount > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + transactionCount + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.transactionBytes = new ArrayList<>(transactionCount);
            totalTransactionLength = 0;
            for (int i=0; i<transactionCount; i++) {
                TransactionBytes txBytes = new TransactionBytes(bytes);
                transactionBytes.add(txBytes);
                totalTransactionLength += txBytes.getLength();
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + totalTransactionLength;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)transactionBytes.size());
            transactionBytes.forEach((tx) -> tx.getBytes(bytes));
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the transaction count
         *
         * @return                          Transaction count
         */
        public int getTransactionCount() {
            return transactionBytes.size();
        }

        /**
         * Get the transactions
         *
         * @return                          Transaction list
         * @throws  NotValidException       Transaction is not valid
         */
        public List<Transaction> getTransactions() throws NotValidException {
            List<Transaction> transactions = new ArrayList<>(transactionBytes.size());
            for (TransactionBytes bytes : transactionBytes) {
                transactions.add(bytes.getTransaction());
            }
            return transactions;
        }
    }

    /**
     * The BlockInventory message is sent when a peer has received a new block.
     * The peer responds with a GetBlocks request if it wants to get the block.
     * <ul>
     * <li>Block identifier (long)
     * <li>Previous block identifier (long)
     * <li>Block timestamp (integer)
     * <li>Transaction identifier list (long)
     * </ul>
     */
    public static class BlockInventoryMessage extends NetworkMessage {

        /** Block identifier */
        private final long blockId;

        /** Previous block identifier */
        private final long previousBlockId;

        /** Block timestamp */
        private final int timestamp;

        /** Transaction identifiers */
        private final List<Long> transactionIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlockInventoryMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return BlockInventory.processRequest(peer, this);
        }

        /**
         * Construct a BlockInventory message
         */
        private BlockInventoryMessage() {
            super("BlockInventory");
            blockId = 0;
            previousBlockId = 0;
            timestamp = 0;
            transactionIds = null;
        }

        /**
         * Construct a BlockInventory message
         *
         * @param   block                   Block
         */
        public BlockInventoryMessage(Block block) {
            super("BlockInventory");
            blockId = block.getId();
            previousBlockId = block.getPreviousBlockId();
            timestamp = block.getTimestamp();
            List<? extends Transaction> transactions = block.getTransactions();
            if (transactions.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactions.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            transactionIds = new ArrayList<>(transactions.size());
            transactions.forEach(tx -> transactionIds.add(tx.getId()));
        }

        /**
         * Construct a BlockInventory message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlockInventoryMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("BlockInventory", bytes);
            blockId = bytes.getLong();
            previousBlockId = bytes.getLong();
            timestamp = bytes.getInt();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            transactionIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                transactionIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 4 + 2 + (8 * transactionIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(blockId).putLong(previousBlockId).putInt(timestamp);
            bytes.putShort((short)transactionIds.size());
            transactionIds.forEach(id -> bytes.putLong(id));
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the block identifier
         *
         * @return                          Block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the previous block identifier
         *
         * @return                          Block identifier
         */
        public long getPreviousBlockId() {
            return previousBlockId;
        }

        /**
         * Get the timestamp
         *
         * @return                          Timestamp
         */
        public int getTimestamp() {
            return timestamp;
        }

        /**
         * Get the block transaction identifiers
         *
         * @return                          Transaction identifiers
         */
        public List<Long> getTransactionIds() {
            return transactionIds;
        }
    }

    /**
     * The TransactionsInventory message is sent when a peer has received new transactions.
     * The peer responds with a GetTransactions message if it wants to
     * receive the transactions.
     * <ul>
     * <li>Transaction list
     * </ul>
     * <p>
     * Each transaction list entry has the following format:
     * <ul>
     * <li>Transaction identifier (long)
     * <li>Transaction timestamp (integer)
     * </ul>
     */
    public static class TransactionsInventoryMessage extends NetworkMessage {

        /** Transaction identifier */
        private final List<Long> transactionIds;

        /** Transaction timestamp */
        private final List<Integer> timestamps;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new TransactionsInventoryMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   peer                        Peer
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(PeerImpl peer) {
            return TransactionsInventory.processRequest(peer, this);
        }

        /**
         * Construct a TransactionsInventory message
         */
        private TransactionsInventoryMessage() {
            super("TransactionsInventory");
            transactionIds = null;
            timestamps = null;
        }

        /**
         * Construct a TransactionsInventory message
         *
         * @param   transactions                Transaction list
         */
        public TransactionsInventoryMessage(List<? extends Transaction> transactions) {
            super("TransactionsInventory");
            if (transactions.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactions.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            transactionIds = new ArrayList<>(transactions.size());
            timestamps = new ArrayList<>(transactions.size());
            for (Transaction transaction : transactions) {
                transactionIds.add(transaction.getId());
                timestamps.add(transaction.getTimestamp());
            }
        }

        /**
         * Construct a TransactionsInventory message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private TransactionsInventoryMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("TransactionsInventory", bytes);
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            transactionIds = new ArrayList<>(count);
            timestamps = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                transactionIds.add(bytes.getLong());
                timestamps.add(bytes.getInt());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 2 + (8 * transactionIds.size()) + (4 * timestamps.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putShort((short)transactionIds.size());
            for (int i=0; i<transactionIds.size(); i++) {
                bytes.putLong(transactionIds.get(i));
                bytes.putInt(timestamps.get(i));
            }
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the transaction identifiers
         *
         * @return                          Identifier
         */
        public List<Long> getTransactionIds() {
            return transactionIds;
        }

        /**
         * Get the timestamps
         *
         * @return                          Timestamp
         */
        public List<Integer> getTimestamps() {
            return timestamps;
        }
    }

    /**
     * The Error message is returned when a error is detected while processing a
     * request.  No error is returned for messages that do not have a response.
     * The message identifier is obtained from the request message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Error severity (boolean)
     * <li>Error name (string)
     * <li>Error message (string)
     * </ul>
     */
    public static class ErrorMessage extends NetworkMessage {

        /** Error message */
        private final byte[] errorMessageBytes;

        /** Message name */
        private final byte[] errorNameBytes;

        /** Error severity */
        private final boolean severeError;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new ErrorMessage(bytes);
        }

        /**
         * Construct am Error message
         */
        private ErrorMessage() {
            super("Error");
            messageId = 0;
            severeError = false;
            errorNameBytes = null;
            errorMessageBytes = null;
        }

        /**
         * Construct an Error message
         *
         * @param   messageId               Message identifier
         * @param   severeError             TRUE if this is a severe error
         * @param   errorName               Error name
         * @param   errorMessage            Error message
         */
        public ErrorMessage(long messageId, boolean severeError, String errorName, String errorMessage) {
            super("Error");
            this.messageId = messageId;
            this.severeError = severeError;
            this.errorNameBytes = errorName.getBytes(UTF8);
            this.errorMessageBytes = errorMessage.getBytes(UTF8);
        }

        /**
         * Construct an Error Message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private ErrorMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("Error", bytes);
            messageId = bytes.getLong();
            severeError = (bytes.get() != (byte)0);
            errorNameBytes = decodeArray(bytes);
            errorMessageBytes = decodeArray(bytes);
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 1 +
                    getEncodedArrayLength(errorNameBytes) +
                    getEncodedArrayLength(errorMessageBytes);
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.put(severeError ? (byte)1 : (byte)0);
            encodeArray(bytes, errorNameBytes);
            encodeArray(bytes, errorMessageBytes);
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Check if this is a severe error
         *
         * @return                          TRUE if this is a severe error
         */
        public boolean isSevereError() {
            return severeError;
        }

        /**
         * Get the error name
         *
         * @return                          Error name
         */
        public String getErrorName() {
            return new String(errorNameBytes, UTF8);
        }

        /**
         * Get the error message
         *
         * @return                          Error Message
         */
        public String getErrorMessage() {
            return new String(errorMessageBytes, UTF8);
        }
    }

    /**
     * Encoded block bytes
     * <ul>
     * <li>Block
     * <li>Transaction list (missing transactions are represented by just the transaction identifier)
     * </ul>
     */
    private static class BlockBytes {

        /** Block bytes */
        private final byte[] blockBytes;

        /** Block transactions */
        private final List<TransactionBytes> blockTransactions;

        /** Total block byte length */
        private int length;

        /**
         * Construct an encoded block
         *
         * @param   block               Block
         */
        private BlockBytes(Block block) {
            blockBytes = block.getBytes();
            length = getEncodedArrayLength(blockBytes) + 2;
            List<? extends Transaction> transactions = block.getTransactions();
            blockTransactions = new ArrayList<>(transactions.size());
            transactions.forEach((transaction) -> {
                TransactionBytes transactionBytes = new TransactionBytes(transaction);
                blockTransactions.add(transactionBytes);
                length += transactionBytes.getLength();
            });
        }

        /**
         * Construct an encoded block
         *
         * @param   bytes                       Message buffer
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Block is not valid
         */
        private BlockBytes(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            blockBytes = decodeArray(bytes);
            length = getEncodedArrayLength(blockBytes) + 2;
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("Array size " + count + " exceeds the maximum size");
            }
            blockTransactions = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                TransactionBytes transactionBytes = new TransactionBytes(bytes);
                blockTransactions.add(transactionBytes);
                length += transactionBytes.getLength();
            }
        }

        /**
         * Get the encoded block size
         *
         * @return                      Encoded block size
         */
        private int getLength() {
            return length;
        }

        /**
         * Get the encoded block bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Buffer is too small
         */
        private void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            encodeArray(bytes, blockBytes);
            bytes.putShort((short)blockTransactions.size());
            blockTransactions.forEach((transaction) -> transaction.getBytes(bytes));
        }

        /**
         * Get the block
         *
         * This method cannot be used if transactions were excluded in the GetBlocks message
         *
         * @return                      Block
         * @throws  NotValidException   Block is not valid
         */
        private Block getBlock() throws NotValidException {
            List<Transaction> transactions = new ArrayList<>(blockTransactions.size());
            for (TransactionBytes transaction : blockTransactions) {
                transactions.add(transaction.getTransaction());
            }
            return Nxt.newBlockBuilder(blockBytes, transactions);
        }

        /**
         * Get the block
         *
         * This method must be used if transactions were excluded in the GetBlocks message
         *
         * @param   excludedTransactions Excluded transactions
         * @throws  NotValidException   Block is not valid
         */
        private Block getBlock(List<Transaction> excludedTransactions) throws NotValidException {
            List<Transaction> transactions = new ArrayList<>(blockTransactions.size());
            for (TransactionBytes transaction : blockTransactions) {
                transactions.add(transaction.getTransaction(excludedTransactions));
            }
            return Nxt.newBlockBuilder(blockBytes, transactions);
        }
    }

    /**
     * Encoded transaction bytes
     * <p>
     * <ul>
     * <li>Transaction bytes (an excluded transaction consists of just the transaction identifier)
     * <li>Prunable attachment bytes
     * </ul>
     */
    private static class TransactionBytes {

        /** Transaction bytes */
        private final byte[] transactionBytes;

        /** Prunable attachment bytes */
        private final byte[] prunableAttachmentBytes;

        /**
         * Construct an encoded transaction
         *
         * @param   transaction         Transaction
         */
        private TransactionBytes(Transaction transaction) {
            transactionBytes = transaction.getBytes();
            transaction.getAppendages(false);
            JSONObject prunableAttachment = transaction.getPrunableAttachmentJSON();
            if (prunableAttachment != null) {
                prunableAttachmentBytes = JSON.toJSONString(prunableAttachment).getBytes(UTF8);
            } else {
                prunableAttachmentBytes = new byte[0];
            }
        }

        /**
         * Construct an encoded transaction
         *
         * @param   transactionId       Transaction identifier
         */
        private TransactionBytes(long transactionId) {
            transactionBytes = new byte[8];
            for (int i=0; i<8; i++) {
                transactionBytes[i] = (byte)(transactionId>>>(i*8));
            }
            prunableAttachmentBytes = new byte[0];
        }

        /**
         * Construct an encoded transaction
         *
         * @param   bytes                       Message buffer
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Transaction is not valid
         */
        private TransactionBytes(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            transactionBytes = decodeArray(bytes);
            prunableAttachmentBytes = decodeArray(bytes);
        }

        /**
         * Get the encoded transaction length
         *
         * @return                      Encoded transaction length
         */
        private int getLength() {
            return getEncodedArrayLength(transactionBytes) + getEncodedArrayLength(prunableAttachmentBytes);
        }

        /**
         * Get the encoded transaction bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        private void getBytes(ByteBuffer bytes) {
            encodeArray(bytes, transactionBytes);
            encodeArray(bytes, prunableAttachmentBytes);
        }

        /**
         * Get the transaction
         *
         * This method cannot be used if transactions were excluded
         *
         * @return                      Transaction
         * @throws  NotValidException   Transaction is not valid
         */
        private Transaction getTransaction() throws NotValidException {
            JSONObject prunableAttachment;
            if (transactionBytes.length == 8) {
                throw new IllegalArgumentException("No excluded transactions provided");
            }
            if (prunableAttachmentBytes.length > 0) {
                prunableAttachment = (JSONObject)JSONValue.parse(new String(prunableAttachmentBytes));
            } else {
                prunableAttachment = null;
            }
            Transaction.Builder builder = Nxt.newTransactionBuilder(transactionBytes, prunableAttachment);
            return builder.build();
        }

        /**
         * Get the transaction
         *
         * This method must be used if transactions were excluded
         *
         * @param   excludedTransactions    Excluded transactions
         * @throws  NotValidException       Transaction is not valid
         */
        private Transaction getTransaction(List<Transaction> excludedTransactions) throws NotValidException {
            if (transactionBytes.length != 8) {
                return getTransaction();
            }
            long transactionId = 0;
            for (int i=0; i<8; i++) {
                transactionId |= ((long)transactionBytes[i] & 0xff) << (i*8);
            }
            Transaction transaction = null;
            for (Transaction tx : excludedTransactions) {
                if (tx.getId() == transactionId) {
                    transaction = tx;
                    break;
                }
            }
            if (transaction == null) {
                throw new NotValidException("Excluded transaction not found");
            }
            return transaction;
        }
    }
}
