/*
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

package nxt.blockchain;

import nxt.NxtException;
import nxt.account.PublicKeyAnnouncementAppendix;
import nxt.messaging.EncryptToSelfMessageAppendix;
import nxt.messaging.EncryptedMessageAppendix;
import nxt.messaging.MessageAppendix;
import nxt.messaging.PrunableEncryptedMessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.shuffling.ShufflingProcessingAttachment;
import nxt.taggeddata.TaggedDataAttachment;
import nxt.voting.PhasingAppendix;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class AppendixParser {

    public static Collection<AppendixParser> getParsers() {
        return parsersMap.values();
    }

    public static AppendixParser getParser(int appendixType) {
        return parsersMap.get(appendixType);
    }

    public static Collection<AppendixParser> getPrunableParsers() {
        return prunableParsers;
    }

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
        list.add(TaggedDataAttachment.appendixParser);
        list.add(ChildBlockAttachment.appendixParser);
        prunableParsers = Collections.unmodifiableList(list);
    }

    public abstract Appendix.AbstractAppendix parse(ByteBuffer buffer) throws NxtException.NotValidException;

    public abstract Appendix.AbstractAppendix parse(JSONObject attachmentData) throws NxtException.NotValidException;

}
