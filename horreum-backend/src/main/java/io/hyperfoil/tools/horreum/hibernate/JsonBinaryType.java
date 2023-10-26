package io.hyperfoil.tools.horreum.hibernate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.CustomType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

import static java.lang.String.format;

public class JsonBinaryType implements UserType<JsonNode> {

    public static final CustomType<JsonNode> INSTANCE = new CustomType<>(new JsonBinaryType(), new TypeConfiguration());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public int getSqlType() {
        return SqlTypes.JSON;
    }

    @Override
    public Class<JsonNode> returnedClass() {
        return JsonNode.class;
    }

    @Override
    public boolean equals(JsonNode x, JsonNode y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(JsonNode x) {
        return Objects.hashCode(x);
    }

    @Override
    public JsonNode nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        final String json = rs.getString(position);
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json.getBytes("UTF-8"));
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert String to JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement ps, JsonNode value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
            return;
        }
        try {
            ps.setObject(index, value.toString(), Types.OTHER);
        } catch (final Exception ex) {
            throw new RuntimeException(format("Failed to convert JSON to String: %s", ex.getMessage()), ex);
        }
    }

    @Override
    public JsonNode deepCopy(JsonNode value) throws HibernateException {
        if (value == null) {
            return null;
        }
        return value.deepCopy();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(JsonNode value) throws HibernateException {
        return (Serializable) this.deepCopy(value);
    }

    @Override
    public JsonNode assemble(Serializable cached, Object owner) throws HibernateException {
        try {
            return mapper.readTree(cached.toString());
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(format("Failed to convert String to JSON: %s", ex.getMessage()), ex);
        }
    }

    @Override
    public JsonNode replace(JsonNode original, JsonNode target, Object owner) throws HibernateException {
        return original;
    }

    public String getName() {
        return "jsonb";
    }
}