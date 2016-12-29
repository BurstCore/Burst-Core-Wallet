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

package nxt.messaging;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Appendix;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Fee;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

abstract class AbstractEncryptedMessageAppendix extends Appendix.AbstractAppendix {

    private static final Fee ENCRYPTED_MESSAGE_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, Constants.ONE_NXT, 32) {
        @Override
        public int getSize(TransactionImpl transaction, Appendix appendage) {
            return ((AbstractEncryptedMessageAppendix)appendage).getEncryptedDataLength() - 16;
        }
    };

    private EncryptedData encryptedData;
    private final boolean isText;
    private final boolean isCompressed;

    AbstractEncryptedMessageAppendix(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        int length = buffer.getInt();
        this.isText = length < 0;
        if (length < 0) {
            length &= Integer.MAX_VALUE;
        }
        this.encryptedData = EncryptedData.readEncryptedData(buffer, length, 1000);
        this.isCompressed = getVersion() != 2;
    }

    AbstractEncryptedMessageAppendix(JSONObject attachmentJSON, JSONObject encryptedMessageJSON) {
        super(attachmentJSON);
        byte[] data = Convert.parseHexString((String)encryptedMessageJSON.get("data"));
        byte[] nonce = Convert.parseHexString((String) encryptedMessageJSON.get("nonce"));
        this.encryptedData = new EncryptedData(data, nonce);
        this.isText = Boolean.TRUE.equals(encryptedMessageJSON.get("isText"));
        Object isCompressed = encryptedMessageJSON.get("isCompressed");
        this.isCompressed = isCompressed == null || Boolean.TRUE.equals(isCompressed);
    }

    AbstractEncryptedMessageAppendix(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
        super(isCompressed ? 1 : 2);
        this.encryptedData = encryptedData;
        this.isText = isText;
        this.isCompressed = isCompressed;
    }

    @Override
    protected int getMySize() {
        return 4 + encryptedData.getSize();
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putInt(isText ? (encryptedData.getData().length | Integer.MIN_VALUE) : encryptedData.getData().length);
        buffer.put(encryptedData.getData());
        buffer.put(encryptedData.getNonce());
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("data", Convert.toHexString(encryptedData.getData()));
        json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
        json.put("isText", isText);
        json.put("isCompressed", isCompressed);
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return ENCRYPTED_MESSAGE_FEE;
    }

    @Override
    public void validate(Transaction transaction) throws NxtException.ValidationException {
        if (getEncryptedDataLength() > Constants.MAX_ENCRYPTED_MESSAGE_LENGTH) {
            throw new NxtException.NotValidException("Max encrypted message length exceeded");
        }
        if (encryptedData != null) {
            if ((encryptedData.getNonce().length != 32 && encryptedData.getData().length > 0)
                    || (encryptedData.getNonce().length != 0 && encryptedData.getData().length == 0)) {
                throw new NxtException.NotValidException("Invalid nonce length " + encryptedData.getNonce().length);
            }
        }
        if ((getVersion() != 2 && !isCompressed) || (getVersion() == 2 && isCompressed)) {
            throw new NxtException.NotValidException("Version mismatch - version " + getVersion() + ", isCompressed " + isCompressed);
        }
    }

    @Override
    public boolean verifyVersion() {
        return getVersion() == 1 || getVersion() == 2;
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {}

    public final EncryptedData getEncryptedData() {
        return encryptedData;
    }

    final void setEncryptedData(EncryptedData encryptedData) {
        this.encryptedData = encryptedData;
    }

    int getEncryptedDataLength() {
        return encryptedData.getData().length;
    }

    public final boolean isText() {
        return isText;
    }

    public final boolean isCompressed() {
        return isCompressed;
    }

    @Override
    public final boolean isPhasable() {
        return false;
    }

    @Override
    public boolean isAllowed(Chain chain) {
        return chain instanceof ChildChain;
    }

}
