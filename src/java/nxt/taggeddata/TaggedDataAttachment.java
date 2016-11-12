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

package nxt.taggeddata;

import nxt.blockchain.Appendix;
import nxt.blockchain.Attachment;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public abstract class TaggedDataAttachment extends Attachment.AbstractAttachment implements Appendix.Prunable {

    private final String name;
    private final String description;
    private final String tags;
    private final String type;
    private final String channel;
    private final boolean isText;
    private final String filename;
    private final byte[] data;
    private volatile TaggedDataHome.TaggedData taggedData;

    TaggedDataAttachment(ByteBuffer buffer) {
        super(buffer);
        this.name = null;
        this.description = null;
        this.tags = null;
        this.type = null;
        this.channel = null;
        this.isText = false;
        this.filename = null;
        this.data = null;
    }

    TaggedDataAttachment(JSONObject attachmentData) {
        super(attachmentData);
        String dataJSON = (String) attachmentData.get("data");
        if (dataJSON != null) {
            this.name = (String) attachmentData.get("name");
            this.description = (String) attachmentData.get("description");
            this.tags = (String) attachmentData.get("tags");
            this.type = (String) attachmentData.get("type");
            this.channel = Convert.nullToEmpty((String) attachmentData.get("channel"));
            this.isText = Boolean.TRUE.equals(attachmentData.get("isText"));
            this.data = isText ? Convert.toBytes(dataJSON) : Convert.parseHexString(dataJSON);
            this.filename = (String) attachmentData.get("filename");
        } else {
            this.name = null;
            this.description = null;
            this.tags = null;
            this.type = null;
            this.channel = null;
            this.isText = false;
            this.filename = null;
            this.data = null;
        }

    }

    TaggedDataAttachment(String name, String description, String tags, String type, String channel, boolean isText, String filename, byte[] data) {
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.type = type;
        this.channel = channel;
        this.isText = isText;
        this.data = data;
        this.filename = filename;
    }

    @Override
    protected final int getMyFullSize() {
        if (getData() == null) {
            return 0;
        }
        return Convert.toBytes(getName()).length + Convert.toBytes(getDescription()).length + Convert.toBytes(getType()).length
                + Convert.toBytes(getChannel()).length + Convert.toBytes(getTags()).length + Convert.toBytes(getFilename()).length + getData().length;
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        if (taggedData != null) {
            attachment.put("name", taggedData.getName());
            attachment.put("description", taggedData.getDescription());
            attachment.put("tags", taggedData.getTags());
            attachment.put("type", taggedData.getType());
            attachment.put("channel", taggedData.getChannel());
            attachment.put("isText", taggedData.isText());
            attachment.put("filename", taggedData.getFilename());
            attachment.put("data", taggedData.isText() ? Convert.toString(taggedData.getData()) : Convert.toHexString(taggedData.getData()));
        } else if (data != null) {
            attachment.put("name", name);
            attachment.put("description", description);
            attachment.put("tags", tags);
            attachment.put("type", type);
            attachment.put("channel", channel);
            attachment.put("isText", isText);
            attachment.put("filename", filename);
            attachment.put("data", isText ? Convert.toString(data) : Convert.toHexString(data));
        }
    }

    @Override
    public byte[] getHash() {
        if (data == null) {
            return null;
        }
        MessageDigest digest = Crypto.sha256();
        digest.update(Convert.toBytes(name));
        digest.update(Convert.toBytes(description));
        digest.update(Convert.toBytes(tags));
        digest.update(Convert.toBytes(type));
        digest.update(Convert.toBytes(channel));
        digest.update((byte)(isText ? 1 : 0));
        digest.update(Convert.toBytes(filename));
        digest.update(data);
        return digest.digest();
    }

    public final String getName() {
        if (taggedData != null) {
            return taggedData.getName();
        }
        return name;
    }

    public final String getDescription() {
        if (taggedData != null) {
            return taggedData.getDescription();
        }
        return description;
    }

    public final String getTags() {
        if (taggedData != null) {
            return taggedData.getTags();
        }
        return tags;
    }

    public final String getType() {
        if (taggedData != null) {
            return taggedData.getType();
        }
        return type;
    }

    public final String getChannel() {
        if (taggedData != null) {
            return taggedData.getChannel();
        }
        return channel;
    }

    public final boolean isText() {
        if (taggedData != null) {
            return taggedData.isText();
        }
        return isText;
    }

    public final String getFilename() {
        if (taggedData != null) {
            return taggedData.getFilename();
        }
        return filename;
    }

    public final byte[] getData() {
        if (taggedData != null) {
            return taggedData.getData();
        }
        return data;
    }

    @Override
    public void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
        if (data == null && taggedData == null && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
            taggedData = ((ChildChain) transaction.getChain()).getTaggedDataHome().getData(getTaggedDataId(transaction));
        }
    }

    @Override
    public boolean hasPrunableData() {
        return (taggedData != null || data != null);
    }

    abstract long getTaggedDataId(Transaction transaction);

}
