package io.hyperfoil.tools.horreum.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.type.CustomType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonBinaryType implements UserType<JsonNode> {

    public static final CustomType<JsonNode> INSTANCE = new CustomType<>(new JsonBinaryType(), new TypeConfiguration());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public int getSqlType() {
        return SqlTypes.JSON;
    }

    @Override
    public Class<JsonNode> returnedClass() {
        return JsonNode.class;
    }

    @Override
    public JsonNode nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        final byte[] colBytes = rs.getBytes(position);
        if (colBytes == null) {
            return null;
        }
        try {
            return MAPPER.readTree(colBytes);
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert String to JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement ps, JsonNode value, int position, WrapperOptions options) throws SQLException {
        if (value == null) {
            ps.setNull(position, Types.OTHER);
            return;
        }
        try {
            ps.setObject(position, value.toString(), Types.OTHER);
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert JSON to String: " + ex.getMessage(), ex);
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
            return MAPPER.readTree(cached.toString());
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to convert String to JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    public JsonNode replace(JsonNode original, JsonNode target, Object owner) throws HibernateException {
        return original;
    }

}
