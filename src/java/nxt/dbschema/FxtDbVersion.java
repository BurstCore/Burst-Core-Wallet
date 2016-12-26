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

package nxt.dbschema;

import nxt.db.BasicDb;
import nxt.db.DbVersion;

public class FxtDbVersion extends DbVersion {

    public FxtDbVersion(BasicDb db) {
        super(db, "PUBLIC");
    }

    protected void update(int nextUpdate) {
        switch (nextUpdate) {
            case 1:
                apply("CREATE TABLE IF NOT EXISTS block (db_id IDENTITY, id BIGINT NOT NULL, version INT NOT NULL, "
                        + "timestamp INT NOT NULL, previous_block_id BIGINT, "
                        + "total_amount BIGINT NOT NULL, "
                        + "total_fee BIGINT NOT NULL, payload_length INT NOT NULL, "
                        + "previous_block_hash BINARY(32), cumulative_difficulty VARBINARY NOT NULL, base_target BIGINT NOT NULL, "
                        + "next_block_id BIGINT, "
                        + "height INT NOT NULL, generation_signature BINARY(64) NOT NULL, "
                        + "block_signature BINARY(64) NOT NULL, payload_hash BINARY(32) NOT NULL, generator_id BIGINT NOT NULL)");
            case 2:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_id_idx ON block (id)");
            case 3:
                apply("CREATE TABLE IF NOT EXISTS transaction_fxt (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "deadline SMALLINT NOT NULL, recipient_id BIGINT, transaction_index SMALLINT NOT NULL, "
                        + "amount BIGINT NOT NULL, fee BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, "
                        + "height INT NOT NULL, block_id BIGINT NOT NULL, FOREIGN KEY (block_id) REFERENCES block (id) ON DELETE CASCADE, "
                        + "signature BINARY(64) NOT NULL, timestamp INT NOT NULL, type TINYINT NOT NULL, subtype TINYINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, block_timestamp INT NOT NULL, has_prunable_attachment BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "ec_block_height INT DEFAULT NULL, ec_block_id BIGINT DEFAULT NULL, attachment_bytes VARBINARY, version TINYINT NOT NULL)");
            case 4:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS transaction_fxt_id_idx ON transaction_fxt (id)");
            case 5:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_height_idx ON block (height)");
            case 6:
                apply("CREATE INDEX IF NOT EXISTS block_generator_id_idx ON block (generator_id)");
            case 7:
                apply("CREATE INDEX IF NOT EXISTS transaction_fxt_sender_id_idx ON transaction_fxt (sender_id)");
            case 8:
                apply("CREATE INDEX IF NOT EXISTS transaction_fxt_recipient_id_idx ON transaction_fxt (recipient_id)");
            case 9:
                apply("CREATE TABLE IF NOT EXISTS peer (address VARCHAR PRIMARY KEY, last_updated INT, services BIGINT)");
            case 10:
                apply("CREATE INDEX IF NOT EXISTS transaction_fxt_block_timestamp_idx ON transaction_fxt (block_timestamp DESC)");
            case 11:
                apply("CREATE TABLE IF NOT EXISTS account (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "has_control_phasing BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "forged_balance BIGINT NOT NULL, active_lessee_id BIGINT, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 12:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_id_height_idx ON account (id, height DESC)");
            case 13:
                apply("CREATE TABLE IF NOT EXISTS account_asset (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "asset_id BIGINT NOT NULL, quantity BIGINT NOT NULL, unconfirmed_quantity BIGINT NOT NULL, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 14:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_asset_id_height_idx ON account_asset (account_id, asset_id, height DESC)");
            case 15:
                apply("CREATE TABLE IF NOT EXISTS account_guaranteed_balance (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "additions BIGINT NOT NULL, height INT NOT NULL)");
            case 16:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_guaranteed_balance_id_height_idx ON account_guaranteed_balance "
                        + "(account_id, height DESC)");
            case 17:
                apply("CREATE TABLE IF NOT EXISTS unconfirmed_transaction (db_id IDENTITY, id BIGINT NOT NULL, expiration INT NOT NULL, "
                        + "transaction_height INT NOT NULL, fee_per_byte BIGINT NOT NULL, arrival_timestamp BIGINT NOT NULL, "
                        + "transaction_bytes VARBINARY NOT NULL, prunable_json VARCHAR, chain_id INT NOT NULL, height INT NOT NULL)");
            case 18:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS unconfirmed_transaction_id_idx ON unconfirmed_transaction (id)");
            case 19:
                apply("CREATE INDEX IF NOT EXISTS account_asset_quantity_idx ON account_asset (quantity DESC)");
            case 20:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_timestamp_idx ON block (timestamp DESC)");
            case 21:
                apply("CREATE TABLE IF NOT EXISTS account_currency (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "currency_id BIGINT NOT NULL, units BIGINT NOT NULL, unconfirmed_units BIGINT NOT NULL, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 22:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_currency_id_height_idx ON account_currency (account_id, currency_id, height DESC)");
            case 23:
                apply("CREATE INDEX IF NOT EXISTS account_currency_units_idx ON account_currency (units DESC)");
            case 24:
                apply("CREATE INDEX IF NOT EXISTS unconfirmed_transaction_height_fee_timestamp_idx ON unconfirmed_transaction "
                        + "(transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC)");
            case 25:
                apply("CREATE TABLE IF NOT EXISTS scan (rescan BOOLEAN NOT NULL DEFAULT FALSE, height INT NOT NULL DEFAULT 0, "
                        + "validate BOOLEAN NOT NULL DEFAULT FALSE)");
            case 26:
                apply("INSERT INTO scan (rescan, height, validate) VALUES (false, 0, false)");
            case 27:
                apply("CREATE TABLE IF NOT EXISTS public_key (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "public_key BINARY(32), height INT NOT NULL, FOREIGN KEY (height) REFERENCES block (height) ON DELETE CASCADE, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 28:
                apply("CREATE INDEX IF NOT EXISTS account_guaranteed_balance_height_idx ON account_guaranteed_balance(height)");
            case 29:
                apply("CREATE TABLE IF NOT EXISTS account_info (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "name VARCHAR, description VARCHAR, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 30:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_info_id_height_idx ON account_info (account_id, height DESC)");
            case 31:
                apply("CREATE INDEX IF NOT EXISTS account_active_lessee_id_idx ON account (active_lessee_id)");
            case 32:
                apply("CREATE TABLE IF NOT EXISTS account_lease (db_id IDENTITY, lessor_id BIGINT NOT NULL, "
                        + "current_leasing_height_from INT, current_leasing_height_to INT, current_lessee_id BIGINT, "
                        + "next_leasing_height_from INT, next_leasing_height_to INT, next_lessee_id BIGINT, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 33:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_lease_lessor_id_height_idx ON account_lease (lessor_id, height DESC)");
            case 34:
                apply("CREATE INDEX IF NOT EXISTS account_lease_current_leasing_height_from_idx ON account_lease (current_leasing_height_from)");
            case 35:
                apply("CREATE INDEX IF NOT EXISTS account_lease_current_leasing_height_to_idx ON account_lease (current_leasing_height_to)");
            case 36:
                apply("CREATE INDEX IF NOT EXISTS account_lease_height_id_idx ON account_lease (height, lessor_id)");
            case 37:
                apply("CREATE INDEX IF NOT EXISTS account_asset_asset_id_idx ON account_asset (asset_id)");
            case 38:
                apply("CREATE INDEX IF NOT EXISTS account_currency_currency_id_idx ON account_currency (currency_id)");
            case 39:
                apply("CREATE INDEX IF NOT EXISTS unconfirmed_transaction_expiration_idx ON unconfirmed_transaction (expiration DESC)");
            case 40:
                apply("CREATE INDEX IF NOT EXISTS account_height_id_idx ON account (height, id)");
            case 41:
                apply("CREATE INDEX IF NOT EXISTS account_asset_height_id_idx ON account_asset (height, account_id, asset_id)");
            case 42:
                apply("CREATE INDEX IF NOT EXISTS account_currency_height_id_idx ON account_currency (height, account_id, currency_id)");
            case 43:
                apply("CREATE INDEX IF NOT EXISTS account_info_height_id_idx ON account_info (height, account_id)");
            case 44:
                apply("CREATE TABLE IF NOT EXISTS account_ledger (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "event_type TINYINT NOT NULL, event_id BIGINT NOT NULL, event_hash BINARY(32), chain_id INT NOT NULL, "
                        + "holding_type TINYINT NOT NULL, holding_id BIGINT, change BIGINT NOT NULL, balance BIGINT NOT NULL, "
                        + "block_id BIGINT NOT NULL, height INT NOT NULL, timestamp INT NOT NULL)");
            case 45:
                apply("CREATE INDEX IF NOT EXISTS account_ledger_id_idx ON account_ledger(account_id, db_id)");
            case 46:
                apply("CREATE INDEX IF NOT EXISTS account_ledger_height_idx ON account_ledger(height)");
            case 47:
                nxt.db.FullTextTrigger.init(db);
                apply(null);
            case 48:
                apply("CREATE TABLE IF NOT EXISTS account_control_phasing (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "whitelist ARRAY, voting_model TINYINT NOT NULL, quorum BIGINT, min_balance BIGINT, "
                        + "holding_id BIGINT, min_balance_model TINYINT, max_fees BIGINT, min_duration SMALLINT, max_duration SMALLINT, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 49:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_control_phasing_id_height_idx ON account_control_phasing (account_id, height DESC)");
            case 50:
                apply("CREATE INDEX IF NOT EXISTS account_control_phasing_height_id_idx ON account_control_phasing (height, account_id)");
            case 51:
                apply("CREATE TABLE IF NOT EXISTS account_property (db_id IDENTITY, id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, setter_id BIGINT, "
                        + "property VARCHAR NOT NULL, value VARCHAR, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 52:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_property_id_height_idx ON account_property (id, height DESC)");
            case 53:
                apply("CREATE INDEX IF NOT EXISTS account_property_height_id_idx ON account_property (height, id)");
            case 54:
                apply("CREATE INDEX IF NOT EXISTS account_property_recipient_height_idx ON account_property (recipient_id, height DESC)");
            case 55:
                apply("CREATE INDEX IF NOT EXISTS account_property_setter_recipient_idx ON account_property (setter_id, recipient_id)");
            case 56:
                apply("CREATE TABLE IF NOT EXISTS asset (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "name VARCHAR NOT NULL, description VARCHAR, quantity BIGINT NOT NULL, decimals TINYINT NOT NULL, "
                        + "initial_quantity BIGINT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 57:
                apply("CREATE INDEX IF NOT EXISTS asset_account_id_idx ON asset (account_id)");
            case 58:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS asset_id_height_idx ON asset (id, height DESC)");
            case 59:
                apply("CREATE INDEX IF NOT EXISTS asset_height_id_idx ON asset (height, id)");
            case 60:
                apply("CREATE TABLE IF NOT EXISTS asset_delete (db_id IDENTITY, id BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, "
                        + "asset_id BIGINT NOT NULL, "
                        + "account_id BIGINT NOT NULL, quantity BIGINT NOT NULL, timestamp INT NOT NULL, height INT NOT NULL)");
            case 61:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_id_idx ON asset_delete (id)");
            case 62:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_asset_id_idx ON asset_delete (asset_id, height DESC)");
            case 63:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_account_id_idx ON asset_delete (account_id, height DESC)");
            case 64:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_height_idx ON asset_delete (height)");
            case 65:
                apply("CREATE TABLE IF NOT EXISTS asset_transfer (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "full_hash BINARY(32) NOT NULL, asset_id BIGINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, quantity BIGINT NOT NULL, timestamp INT NOT NULL, "
                        + "height INT NOT NULL)");
            case 66:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_id_idx ON asset_transfer (id)");
            case 67:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_asset_id_idx ON asset_transfer (asset_id, height DESC)");
            case 68:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_sender_id_idx ON asset_transfer (sender_id, height DESC)");
            case 69:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_recipient_id_idx ON asset_transfer (recipient_id, height DESC)");
            case 70:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_height_idx ON asset_transfer (height)");
            case 71:
                apply("CREATE TABLE IF NOT EXISTS currency (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "name VARCHAR NOT NULL, name_lower VARCHAR AS LOWER (name) NOT NULL, code VARCHAR NOT NULL, "
                        + "description VARCHAR, type INT NOT NULL, chain INT NOT NULL, initial_supply BIGINT NOT NULL DEFAULT 0, "
                        + "reserve_supply BIGINT NOT NULL, max_supply BIGINT NOT NULL, creation_height INT NOT NULL, issuance_height INT NOT NULL, "
                        + "min_reserve_per_unit_nqt BIGINT NOT NULL, min_difficulty TINYINT NOT NULL, "
                        + "max_difficulty TINYINT NOT NULL, ruleset TINYINT NOT NULL, algorithm TINYINT NOT NULL, "
                        + "decimals TINYINT NOT NULL DEFAULT 0, is_deleted BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 72:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS currency_id_height_idx ON currency (id, height DESC)");
            case 73:
                apply("CREATE INDEX IF NOT EXISTS currency_account_id_idx ON currency (account_id)");
            case 74:
                apply("CREATE INDEX IF NOT EXISTS currency_name_idx ON currency (name_lower, chain, height DESC)");
            case 75:
                apply("CREATE INDEX IF NOT EXISTS currency_code_idx ON currency (code, chain, height DESC)");
            case 76:
                apply("CREATE INDEX IF NOT EXISTS currency_creation_height_idx ON currency (creation_height DESC)");
            case 77:
                apply("CREATE INDEX IF NOT EXISTS currency_issuance_height_idx ON currency (issuance_height)");
            case 78:
                apply("CREATE INDEX IF NOT EXISTS currency_height_id_idx ON currency (height, id)");
            case 79:
                apply("CREATE TABLE IF NOT EXISTS currency_supply (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "current_supply BIGINT NOT NULL, current_reserve_per_unit_nqt BIGINT NOT NULL, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 80:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS currency_supply_id_height_idx ON currency_supply (id, height DESC)");
            case 81:
                apply("CREATE INDEX IF NOT EXISTS currency_supply_height_id_idx ON currency_supply (height, id)");
            case 82:
                apply("CREATE TABLE IF NOT EXISTS phasing_poll_finish (db_id IDENTITY, transaction_id BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, "
                        + "chain_id INT NOT NULL, finish_height INT NOT NULL, height INT NOT NULL)");
            case 83:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_finish_finish_height_idx ON phasing_poll_finish (finish_height)");
            case 84:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_finish_height_idx ON phasing_poll_finish (height)");
            case 85:
                apply("CREATE TABLE IF NOT EXISTS currency_mint (db_id IDENTITY, currency_id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "counter BIGINT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 86:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS currency_mint_currency_id_account_id_idx ON currency_mint (currency_id, account_id, height DESC)");
            case 87:
                apply("CREATE INDEX IF NOT EXISTS currency_mint_height_id_idx ON currency_mint (height, currency_id, account_id)");
            case 88:
                apply("CREATE TABLE IF NOT EXISTS currency_transfer (db_id IDENTITY, id BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, currency_id BIGINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, units BIGINT NOT NULL, timestamp INT NOT NULL, "
                        + "height INT NOT NULL)");
            case 89:
                apply("CREATE INDEX IF NOT EXISTS currency_transfer_id_idx ON currency_transfer (id)");
            case 90:
                apply("CREATE INDEX IF NOT EXISTS currency_transfer_currency_id_idx ON currency_transfer (currency_id, height DESC)");
            case 91:
                apply("CREATE INDEX IF NOT EXISTS currency_transfer_sender_id_idx ON currency_transfer (sender_id, height DESC)");
            case 92:
                apply("CREATE INDEX IF NOT EXISTS currency_transfer_recipient_id_idx ON currency_transfer (recipient_id, height DESC)");
            case 93:
                apply("CREATE INDEX IF NOT EXISTS currency_transfer_height_idx ON currency_transfer(height)");
            case 94:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_finish_id_idx ON phasing_poll_finish (transaction_id)");
            case 95:
                apply("CREATE TABLE IF NOT EXISTS phasing_poll_linked_transaction (db_id IDENTITY, chain_id INT NOT NULL, "
                        + "transaction_id BIGINT NOT NULL, transaction_full_hash BINARY(32) NOT NULL, linked_chain_id INT NOT NULL, linked_full_hash BINARY(32) NOT NULL, "
                        + "linked_transaction_id BIGINT NOT NULL, height INT NOT NULL)");
            case 96:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_linked_transaction_id_link_idx "
                        + "ON phasing_poll_linked_transaction (transaction_id, linked_transaction_id)");
            case 97:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_linked_transaction_height_idx ON phasing_poll_linked_transaction (height)");
            case 98:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_linked_transaction_link_id_idx "
                        + "ON phasing_poll_linked_transaction (linked_transaction_id, transaction_id)");
            case 99:
                apply("CREATE TABLE IF NOT EXISTS balance_fxt (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "balance BIGINT NOT NULL, unconfirmed_balance BIGINT NOT NULL, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 100:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS balance_fxt_id_height_idx ON balance_fxt (account_id, height DESC)");
            case 101:
                apply("CREATE INDEX IF NOT EXISTS balance_fxt_height_id_idx ON balance_fxt (height, account_id)");
            case 102:
                apply("CREATE TABLE IF NOT EXISTS coin_order_fxt (db_id IDENTITY, id BIGINT NOT NULL,"
                        + "account_id BIGINT NOT NULL, chain_id INT NOT NULL, exchange_id INT NOT NULL, "
                        + "full_hash BINARY(32) NOT NULL,"
                        + "quantity BIGINT NOT NULL, bid_price BIGINT NOT NULL, ask_price BIGINT NOT NULL,"
                        + "creation_height INT NOT NULL, height INT NOT NULL, transaction_height INT NOT NULL,"
                        + "transaction_index SMALLINT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 103:
                apply("CREATE INDEX IF NOT EXISTS coin_order_fxt_id_idx ON coin_order_fxt (id, height DESC)");
            case 104:
                apply("CREATE INDEX IF NOT EXISTS coin_order_fxt_chain_idx ON coin_order_fxt (chain_id, exchange_id)");
            case 105:
                apply("CREATE INDEX IF NOT EXISTS coin_order_fxt_exchange_idx ON coin_order_fxt (exchange_id)");
            case 106:
                apply("CREATE INDEX IF NOT EXISTS coin_order_fxt_account_idx ON coin_order_fxt (account_id)");
            case 107:
                apply("CREATE TABLE IF NOT EXISTS coin_trade_fxt (db_id IDENTITY, "
                        + "chain_id INT NOT NULL, exchange_id INT NOT NULL, account_id BIGINT NOT NULL, "
                        + "block_id BIGINT NOT NULL, height INT NOT NULL, timestamp INT NOT NULL,"
                        + "exchange_quantity BIGINT NOT NULL, exchange_price BIGINT NOT NULL,"
                        + "order_id BIGINT NOT NULL, order_full_hash BINARY(32) NOT NULL,"
                        + "match_id BIGINT NOT NULL, match_full_hash BINARY(32) NOT NULL)");
            case 108:
                apply("CREATE INDEX IF NOT EXISTS coin_trade_fxt_chain_idx ON coin_trade_fxt (chain_id)");
            case 109:
                apply("CREATE INDEX IF NOT EXISTS coin_trade_fxt_exchange_idx ON coin_trade_fxt (exchange_id)");
            case 110:
                apply("CREATE INDEX IF NOT EXISTS coin_trade_fxt_account_idx ON coin_trade_fxt (account_id)");
            case 111:
                apply("CREATE INDEX IF NOT EXISTS coin_trade_fxt_order_idx ON coin_trade_fxt (order_id)");
            case 112:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS public_key_account_id_height_idx ON public_key (account_id, height DESC)");
            case 113:
                return;
            default:
                throw new RuntimeException("Forging chain database inconsistent with code, at update " + nextUpdate
                        + ", probably trying to run older code on newer database");
        }
    }
}
