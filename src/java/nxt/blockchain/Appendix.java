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

package nxt.blockchain;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.PublicKeyAnnouncementAppendix;
import nxt.messaging.EncryptToSelfMessageAppendix;
import nxt.messaging.EncryptedMessageAppendix;
import nxt.messaging.MessageAppendix;
import nxt.messaging.PrunableEncryptedMessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.shuffling.ShufflingProcessingAttachment;
import nxt.taggeddata.TaggedDataExtendAttachment;
import nxt.taggeddata.TaggedDataUploadAttachment;
import nxt.voting.PhasingAppendix;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public interface Appendix {

    int getAppendixType();
    int getSize();
    int getFullSize();
    void putBytes(ByteBuffer buffer);
    JSONObject getJSONObject();
    byte getVersion();
    int getBaselineFeeHeight();
    Fee getBaselineFee(Transaction transaction);
    int getNextFeeHeight();
    Fee getNextFee(Transaction transaction);
    Fee getFee(Transaction transaction, int height);
    boolean isPhased(Transaction transaction);
    boolean isAllowed(Chain chain);

    interface Prunable {
        byte[] getHash();
        boolean hasPrunableData();
        void restorePrunableData(Transaction transaction, int blockTimestamp, int height);
        default boolean shouldLoadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
            return Nxt.getEpochTime() - transaction.getTimestamp() <
                    (includeExpiredPrunable && Constants.INCLUDE_EXPIRED_PRUNABLE ?
                            Constants.MAX_PRUNABLE_LIFETIME : Constants.MIN_PRUNABLE_LIFETIME);
        }
        void putPrunableBytes(ByteBuffer buffer);
    }

    interface Encryptable {
        void encrypt(String secretPhrase);
    }

    interface AppendixParser {
        AbstractAppendix parse(ByteBuffer buffer) throws NxtException.NotValidException;
        AbstractAppendix parse(JSONObject attachmentData) throws NxtException.NotValidException;
    }

    interface PrunableAppendixParser extends AppendixParser {
        AbstractAppendix parsePrunable(ByteBuffer buffer) throws NxtException.NotValidException;
    }

    static Collection<AppendixParser> getParsers() {
        return AbstractAppendix.parsersMap.values();
    }

    static AppendixParser getParser(int appendixType) {
        return AbstractAppendix.parsersMap.get(appendixType);
    }

    static Collection<AppendixParser> getPrunableParsers() {
        return AbstractAppendix.prunableParsers;
    }

    abstract class AbstractAppendix implements Appendix {

        private static final SortedMap<Integer,AppendixParser> parsersMap;
        static {
            SortedMap<Integer,AppendixParser> map = new TreeMap<>();
            map.put(MessageAppendix.appendixType, MessageAppendix.appendixParser);
            map.put(EncryptedMessageAppendix.appendixType, EncryptedMessageAppendix.appendixParser);
            map.put(EncryptToSelfMessageAppendix.appendixType, EncryptToSelfMessageAppendix.appendixParser);
            map.put(PrunablePlainMessageAppendix.appendixType, PrunablePlainMessageAppendix.appendixParser);
            map.put(PrunableEncryptedMessageAppendix.appendixType, PrunableEncryptedMessageAppendix.appendixParser);
            map.put(PublicKeyAnnouncementAppendix.appendixType, PublicKeyAnnouncementAppendix.appendixParser);
            map.put(PhasingAppendix.appendixType, PhasingAppendix.appendixParser);
            parsersMap = Collections.unmodifiableSortedMap(map);
        }

        private static final List<AppendixParser> prunableParsers;
        static {
            List<AppendixParser> list = new ArrayList<>();
            list.add(PrunablePlainMessageAppendix.appendixParser);
            list.add(PrunableEncryptedMessageAppendix.appendixParser);
            list.add(ShufflingProcessingAttachment.appendixParser);
            list.add(TaggedDataUploadAttachment.appendixParser);
            list.add(TaggedDataExtendAttachment.appendixParser);
            list.add(ChildBlockAttachment.appendixParser);
            prunableParsers = Collections.unmodifiableList(list);
        }

        private final byte version;

        protected AbstractAppendix(JSONObject attachmentData) {
            version = ((Long) attachmentData.get("version." + getAppendixName())).byteValue();
        }

        protected AbstractAppendix(ByteBuffer buffer) {
            version = buffer.get();
        }

        protected AbstractAppendix(int version) {
            this.version = (byte) version;
        }

        protected AbstractAppendix() {
            this.version = 1;
        }

        public abstract String getAppendixName();

        @Override
        public final int getSize() {
            return getMySize() + 1;
        }

        @Override
        public final int getFullSize() {
            return getMyFullSize() + 1;
        }

        protected abstract int getMySize();

        protected int getMyFullSize() {
            return getMySize();
        }

        @Override
        public final void putBytes(ByteBuffer buffer) {
            buffer.put(version);
            putMyBytes(buffer);
        }

        protected abstract void putMyBytes(ByteBuffer buffer);

        public final void putPrunableBytes(ByteBuffer buffer) {
            buffer.put(version);
            putMyPrunableBytes(buffer);
        }

        protected void putMyPrunableBytes(ByteBuffer buffer) {
        }

        @Override
        public final JSONObject getJSONObject() {
            JSONObject json = new JSONObject();
            json.put("version." + getAppendixName(), version);
            putMyJSON(json);
            return json;
        }

        protected abstract void putMyJSON(JSONObject json);

        @Override
        public final byte getVersion() {
            return version;
        }

        public boolean verifyVersion() {
            return version == 1;
        }

        @Override
        public int getBaselineFeeHeight() {
            return 0;
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return Fee.NONE;
        }

        @Override
        public int getNextFeeHeight() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Fee getNextFee(Transaction transaction) {
            return getBaselineFee(transaction);
        }

        @Override
        public final Fee getFee(Transaction transaction, int height) {
            return height >= getNextFeeHeight() ? getNextFee(transaction) : getBaselineFee(transaction);
        }

        public abstract void validate(Transaction transaction) throws NxtException.ValidationException;

        public void validateId(Transaction transaction) throws NxtException.ValidationException {}

        public void validateAtFinish(Transaction transaction) throws NxtException.ValidationException {
            if (!isPhased(transaction)) {
                return;
            }
            validate(transaction);
        }

        public abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);

        public final void loadPrunable(Transaction transaction) {
            loadPrunable(transaction, false);
        }

        public void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {}

        public abstract boolean isPhasable();

        @Override
        public final boolean isPhased(Transaction transaction) {
            return isPhasable() && transaction instanceof ChildTransaction && ((ChildTransaction)transaction).getPhasing() != null;
        }

    }

    static boolean hasAppendix(String appendixName, JSONObject attachmentData) {
        return attachmentData.get("version." + appendixName) != null;
    }

}
