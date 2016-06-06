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

package nxt;

import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.List;

public class ChildBlockAttachment extends Attachment.AbstractAttachment implements Appendix.Prunable {

    //TODO: use transaction hashes, make prunable
    //private final List<ChildTransactionImpl> transactions;

    ChildBlockAttachment(ByteBuffer buffer) {
        super(buffer);
    }

    ChildBlockAttachment(JSONObject attachmentData) {
        super(attachmentData);
    }

    ChildBlockAttachment(List<ChildTransactionImpl> transactions) {
        //TODO;
    }

    @Override
    int getMySize() {
        return 0;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {

    }

    @Override
    void putMyJSON(JSONObject json) {

    }

    @Override
    public TransactionType getTransactionType() {
        return ChildBlockTransactionType.instance;
    }


    public List<ChildTransactionImpl> getChildTransactions() {
        return null;
    }

    //Prunable:

    @Override
    public byte[] getHash() {
        return new byte[0];
    }

    @Override
    public boolean hasPrunableData() {
        return false;
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {

    }

}
