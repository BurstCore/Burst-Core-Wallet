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

package nxt.account;

import nxt.blockchain.ChildChain;

public enum HoldingType {

    COIN((byte)0) {

        @Override
        public long getBalance(ChildChain childChain, Account account, long holdingId) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            return account.getBalance(childChain);
        }

        @Override
        public long getUnconfirmedBalance(ChildChain childChain, Account account, long holdingId) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            return account.getUnconfirmedBalance(childChain);
        }

        @Override
        public void addToBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToBalance(childChain, event, eventId, amount);
        }

        @Override
        public void addToUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToUnconfirmedBalance(childChain, event, eventId, amount);
        }

        @Override
        public void addToBalanceAndUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToBalanceAndUnconfirmedBalance(childChain, event, eventId, amount);
        }

    },

    ASSET((byte)1) {

        @Override
        public long getBalance(ChildChain childChain, Account account, long holdingId) {
            return account.getAssetBalanceQNT(holdingId);
        }

        @Override
        public long getUnconfirmedBalance(ChildChain childChain, Account account, long holdingId) {
            return account.getUnconfirmedAssetBalanceQNT(holdingId);
        }

        @Override
        public void addToBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToAssetBalanceQNT(event, eventId, holdingId, amount);
        }

        @Override
        public void addToUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToUnconfirmedAssetBalanceQNT(event, eventId, holdingId, amount);
        }

        @Override
        public void addToBalanceAndUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToAssetAndUnconfirmedAssetBalanceQNT(event, eventId, holdingId, amount);
        }

    },

    CURRENCY((byte)2) {

        @Override
        public long getBalance(ChildChain childChain, Account account, long holdingId) {
            return account.getCurrencyUnits(holdingId);
        }

        @Override
        public long getUnconfirmedBalance(ChildChain childChain, Account account, long holdingId) {
            return account.getUnconfirmedCurrencyUnits(holdingId);
        }

        @Override
        public void addToBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToCurrencyUnits(event, eventId, holdingId, amount);
        }

        @Override
        public void addToUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToUnconfirmedCurrencyUnits(event, eventId, holdingId, amount);
        }

        @Override
        public void addToBalanceAndUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToCurrencyAndUnconfirmedCurrencyUnits(event, eventId, holdingId, amount);
        }

    };

    public static HoldingType get(byte code) {
        for (HoldingType holdingType : values()) {
            if (holdingType.getCode() == code) {
                return holdingType;
            }
        }
        throw new IllegalArgumentException("Invalid holdingType code: " + code);
    }

    private final byte code;

    HoldingType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public abstract long getBalance(ChildChain childChain, Account account, long holdingId);

    public abstract long getUnconfirmedBalance(ChildChain childChain, Account account, long holdingId);

    public abstract void addToBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

    public abstract void addToUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

    public abstract void addToBalanceAndUnconfirmedBalance(ChildChain childChain, Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

}
