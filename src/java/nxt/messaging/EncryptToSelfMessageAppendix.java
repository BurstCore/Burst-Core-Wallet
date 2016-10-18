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
import nxt.crypto.EncryptedData;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public class EncryptToSelfMessageAppendix extends AbstractEncryptedMessageAppendix {

    private static final String appendixName = "EncryptToSelfMessage";

    public static EncryptToSelfMessageAppendix parse(JSONObject attachmentData) {
        if (!Appendix.hasAppendix(appendixName, attachmentData)) {
            return null;
        }
        if (((JSONObject)attachmentData.get("encryptToSelfMessage")).get("data") == null) {
            return new UnencryptedEncryptToSelfMessageAppendix(attachmentData);
        }
        return new EncryptToSelfMessageAppendix(attachmentData);
    }

    public EncryptToSelfMessageAppendix(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
    }

    public EncryptToSelfMessageAppendix(JSONObject attachmentData) {
        super(attachmentData, (JSONObject)attachmentData.get("encryptToSelfMessage"));
    }

    public EncryptToSelfMessageAppendix(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
        super(encryptedData, isText, isCompressed);
    }

    @Override
    public final String getAppendixName() {
        return appendixName;
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        JSONObject encryptToSelfMessageJSON = new JSONObject();
        super.putMyJSON(encryptToSelfMessageJSON);
        json.put("encryptToSelfMessage", encryptToSelfMessageJSON);
    }

}
