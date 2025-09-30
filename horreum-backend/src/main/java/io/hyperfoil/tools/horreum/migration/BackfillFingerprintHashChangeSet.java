package io.hyperfoil.tools.horreum.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

public class BackfillFingerprintHashChangeSet implements CustomTaskChange {
    private static final int BATCH_SIZE = 500;

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        ObjectMapper mapper = new ObjectMapper();
        int count = 0;

        String selectSql = "SELECT dataset_id, fingerprint FROM Fingerprint WHERE fp_hash IS NULL";
        String updateSql = "UPDATE fingerprint SET fp_hash = ? WHERE dataset_id = ?";

        try (Statement selectStmt = connection.createStatement();
                PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {

            ResultSet rs = selectStmt.executeQuery(selectSql);
            System.out.println("Starting fingerprint hash backfill...");

            while (rs.next()) {
                int datasetId = rs.getInt("dataset_id");
                String fingerprintJson = rs.getString("fingerprint");

                if (fingerprintJson != null) {
                    JsonNode fingerprintNode = mapper.readTree(fingerprintJson);
                    int hash = fingerprintNode.hashCode();

                    updateStmt.setLong(1, hash);
                    updateStmt.setInt(2, datasetId);
                    updateStmt.addBatch();
                    count++;
                }

                if (count % BATCH_SIZE == 0) {
                    updateStmt.executeBatch();
                    System.out.println("Updated " + count + " fingerprint hashes...");
                }
            }

            // final batch
            updateStmt.executeBatch();
            System.out.println("Fingerprint hash backfill complete. Total records updated: " + count);

        } catch (Exception e) {
            throw new CustomChangeException("Failed to backfill fingerprint hashes", e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Fingerprint hashes backfilled successfully.";
    }

    @Override
    public void setUp() throws SetupException {

    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {

    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
