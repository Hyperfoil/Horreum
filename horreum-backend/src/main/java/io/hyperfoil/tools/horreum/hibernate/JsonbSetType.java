package io.hyperfoil.tools.horreum.hibernate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.hyperfoil.tools.horreum.svc.Util;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.CustomType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;
import java.util.*;

import static java.lang.String.format;

public class JsonbSetType implements UserType<ArrayNode> {


    public static final CustomType INSTANCE = new CustomType<>(new JsonbSetType(), new TypeConfiguration());

    @Override
    public int getSqlType() {
        return SqlTypes.ARRAY;
    }

    @Override
    public Class<ArrayNode> returnedClass() {
        return ArrayNode.class;
    }

    @Override
    public boolean equals(ArrayNode x, ArrayNode y) {
        return x.equals(y);
    }

    @Override
    public int hashCode(ArrayNode x) {
        return Objects.hashCode(x);
    }

    @Override
    public ArrayNode nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        if(rs.wasNull())
            return null;
        Array array = rs.getArray(position);
        if (array == null) {
            return null;
        }
        try {
            String[] raw = (String[]) array.getArray();
            ArrayNode rtrn = JsonNodeFactory.instance.arrayNode();
            for(int i=0; i<raw.length; i++){
                rtrn.add(Util.toJsonNode(raw[i]));
            }
            return  rtrn;
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert ResultSet to json array: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement ps, ArrayNode value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.ARRAY);
            return;
        }
        try {
            Set<String> str = new HashSet<>();
            value.forEach(v->str.add(v.toString()));
            Array array = ps.getConnection().createArrayOf("jsonb", str.toArray());
            ps.setObject(index, array);
        } catch (final Exception ex) {
            throw new RuntimeException(format("Failed to convert JSON to String: %s", ex.getMessage()), ex);
        }
    }

    @Override
    public ArrayNode deepCopy(ArrayNode value) throws HibernateException {
        if (value == null) {
            return null;
        }
        try {
            return (ArrayNode)new ObjectMapper().readTree(value.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(ArrayNode value) throws HibernateException {
        return value.toString();
    }

    @Override
    public ArrayNode assemble(Serializable cached, Object owner) throws HibernateException {
        try {
            return (ArrayNode)new ObjectMapper().readTree(cached.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName(){ return "jsonb-any";}
}
