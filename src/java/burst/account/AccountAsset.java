package burst.account;


import nxt.Nxt;
import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AccountAsset {

    private final long accountId;
    private final long assetId;
    private final DbKey dbKey;
    private long quantityQNT;
    private long unconfirmedQuantityQNT;

    public AccountAsset(long accountId, long assetId, long quantityQNT, long unconfirmedQuantityQNT, DbKey.LinkKeyFactory<AccountAsset> accountAssetDbKeyFactory) {
        this.accountId = accountId;
        this.assetId = assetId;
        this.dbKey = accountAssetDbKeyFactory.newKey(this.accountId, this.assetId);
        this.quantityQNT = quantityQNT;
        this.unconfirmedQuantityQNT = unconfirmedQuantityQNT;
    }

    public AccountAsset(ResultSet rs, DbKey dbKey) throws SQLException {
        this.accountId = rs.getLong("account_id");
        this.assetId = rs.getLong("asset_id");
        this.dbKey = dbKey;
        this.quantityQNT = rs.getLong("quantity");
        this.unconfirmedQuantityQNT = rs.getLong("unconfirmed_quantity");
    }

    public void save(VersionedEntityDbTable<AccountAsset> accountAssetTable) {
        nxt.Account.checkBalance(this.accountId, this.quantityQNT, this.unconfirmedQuantityQNT);
        if (this.quantityQNT > 0 || this.unconfirmedQuantityQNT > 0) {
            accountAssetTable.insert(this);
        } else {
            accountAssetTable.delete(this);
        }
    }
    public void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_asset "
                + "(account_id, asset_id, quantity, unconfirmed_quantity, height, latest) "
                + "KEY (account_id, asset_id, height) VALUES (?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.quantityQNT);
            pstmt.setLong(++i, this.unconfirmedQuantityQNT);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public DbKey getDbKey() {
        return this.dbKey;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getAssetId() {
        return assetId;
    }

    public long getQuantityQNT() {
        return quantityQNT;
    }

    public void setQuantityQNT(long qty) {
        this.quantityQNT = qty;
    }

    public long getUnconfirmedQuantityQNT() {
        return unconfirmedQuantityQNT;
    }

    public void setUnconfirmedQuantityQNT(long qty) {
        this.unconfirmedQuantityQNT = qty;
    }

    @Override
    public String toString() {
        return "AccountAsset account_id: " + Long.toUnsignedString(accountId) + " asset_id: " + Long.toUnsignedString(assetId)
                + " quantity: " + quantityQNT + " unconfirmedQuantity: " + unconfirmedQuantityQNT;
    }

}