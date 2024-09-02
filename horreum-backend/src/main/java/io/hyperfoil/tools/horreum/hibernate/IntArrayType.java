package io.hyperfoil.tools.horreum.hibernate;

import static java.lang.String.format;

import java.io.Serializable;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.CustomType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

public class IntArrayType implements UserType<int[]> {

    public static final CustomType INSTANCE = new CustomType<>(new IntArrayType(), new TypeConfiguration());

    @Override
    public int getSqlType() {
        return SqlTypes.ARRAY;
    }

    @Override
    public Class<int[]> returnedClass() {
        return int[].class;
    }

    @Override
    public boolean equals(int[] x, int[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(int[] x) {
        return Objects.hashCode(x);
    }

    @Override
    public int[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        if (rs.wasNull())
            return null;
        Array array = rs.getArray(position);
        if (array == null) {
            return null;
        }
        try {
            return (int[]) array.getArray();
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert ResultSet to int[]: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement ps, int[] value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.ARRAY);
            return;
        }
        try {
            Integer[] castArray = IntStream.of(value).boxed().toArray(Integer[]::new);
            Array array = ps.getConnection().createArrayOf("integer", castArray);
            ps.setArray(index, array);
        } catch (final Exception ex) {
            throw new RuntimeException(format("Failed to convert JSON to String: %s", ex.getMessage()), ex);
        }
    }

    @Override
    public int[] deepCopy(int[] value) throws HibernateException {
        if (value == null) {
            return null;
        }
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(int[] value) throws HibernateException {
        return (Serializable) this.deepCopy(value);
    }

    @Override
    public int[] assemble(Serializable cached, Object owner) throws HibernateException {
        String stringArray = cached.toString().replaceAll("[\\[\\]]", "");
        String[] tokens = stringArray.split(",");

        int length = tokens.length;
        int[] array = new int[length];
        for (int i = 0; i < tokens.length; i++) {
            array[i] = Integer.valueOf(tokens[i]);
        }
        return array;
    }

    @Override
    public int[] replace(int[] original, int[] target, Object owner) throws HibernateException {
        return original;
    }

    public String getName() {
        return "int-array";
    }
}
