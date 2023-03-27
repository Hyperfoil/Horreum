package io.hyperfoil.tools.horreum.entity.converter;

import io.hyperfoil.tools.horreum.api.ApiUtil;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

@RegisterForReflection
public class JsonUserType implements UserType {
   @Override
   public int[] sqlTypes() {
      return new int[]{Types.JAVA_OBJECT};
   }

   @Override
   public Class<JsonNode> returnedClass() {
      return JsonNode.class;
   }

   @Override
   public boolean equals(Object o1, Object o2) throws HibernateException {
      if (o1 == o2) {
         return true;
      } else if (o1 == null || o2 == null){
         return false;
      }
      return o1.equals(o2);
   }

   @Override
   public int hashCode(Object o) throws HibernateException {
      if(o == null){
         return 0;
      }
      return o.hashCode();
   }

   @Override
   public Object nullSafeGet(ResultSet resultSet, String[] strings, SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws HibernateException, SQLException {
      String content = resultSet.getString(strings[0]);
      if (content == null) {
         return null;
      }
      try {
         return ApiUtil.OBJECT_MAPPER.readTree(content);
      } catch (JsonProcessingException e) {
         throw new HibernateException(e);
      }
   }

   @Override
   public void nullSafeSet(PreparedStatement preparedStatement, Object o, int i, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException, SQLException {
      if (o == null) {
         preparedStatement.setNull(i, Types.OTHER);
         return;
      }
      try {
         preparedStatement.setObject(i, o.toString().replaceAll("\n", ""), Types.OTHER);
      } catch (Exception e) {
         throw new HibernateException(e);
      }
   }

   @Override
   public Object deepCopy(Object o) throws HibernateException {
      if (o instanceof JsonNode) {
         return ((JsonNode) o).deepCopy();
      }
      return null;
   }

   @Override
   public boolean isMutable() {
      return false;
   }

   @Override
   public Serializable disassemble(Object o) throws HibernateException {
      if(o == null){
         return null;
      }
      return o.toString().replaceAll("\n","");
   }

   @Override
   public Object assemble(Serializable cached, Object owner) throws HibernateException {
      if (cached instanceof String) {
         try {
            return ApiUtil.OBJECT_MAPPER.readTree((String) cached);
         } catch (JsonProcessingException e) {
            throw new HibernateException(e);
         }
      }
      return null;
   }

   @Override
   public Object replace(Object original, Object target, Object owner) throws HibernateException {
      return original;
   }
}
