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

import nxt.NxtException;
import nxt.blockchain.Appendix;
import nxt.blockchain.Transaction;
import nxt.crypto.EncryptedData;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public class EncryptedMessageAppendix extends AbstractEncryptedMessageAppendix {

    private static final String appendixName = "EncryptedMessage";

    public static EncryptedMessageAppendix parse(JSONObject attachmentData) {
        if (!Appendix.hasAppendix(appendixName, attachmentData)) {
            return null;
        }
        if (((JSONObject)attachmentData.get("encryptedMessage")).get("data") == null) {
            return new UnencryptedEncryptedMessageAppendix(attachmentData);
        }
        return new EncryptedMessageAppendix(attachmentData);
    }

    public EncryptedMessageAppendix(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
    }

    EncryptedMessageAppendix(JSONObject attachmentData) {
        super(attachmentData, (JSONObject)attachmentData.get("encryptedMessage"));
    }

    public EncryptedMessageAppendix(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
        super(encryptedData, isText, isCompressed);
    }

    @Override
    public final String getAppendixName() {
        return appendixName;
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        JSONObject encryptedMessageJSON = new JSONObject();
        super.putMyJSON(encryptedMessageJSON);
        json.put("encryptedMessage", encryptedMessageJSON);
    }

    @Override
    public void validate(Transaction transaction) throws NxtException.ValidationException {
        super.validate(transaction);
        if (transaction.getRecipientId() == 0) {
            throw new NxtException.NotValidException("Encrypted messages cannot be attached to transactions with no recipient");
        }
    }

}
