package io.hyperfoil.tools.repo.entity.converter;

import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@RegisterForReflection
public class JsonUserType implements UserType {

   @Override
   public int[] sqlTypes() {
      return new int[]{Types.JAVA_OBJECT};
   }

   @Override
   public Class<Json> returnedClass() {
      return Json.class;
   }

   @Override
   public boolean equals(Object o, Object o1) throws HibernateException {
      if(o == null || o1 == null){
         return false;
      }
      return o.equals(o1);
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
      //return null if the object is not valid json because it could be null / missing from the result set
//      System.out.println(AsciiArt.ANSI_BLUE+"nullSafeGet:"+AsciiArt.ANSI_RESET+content);
      return Json.fromString(content,null);
   }

   @Override
   public void nullSafeSet(PreparedStatement preparedStatement, Object o, int i, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException, SQLException {
//      System.out.println("nullSafeSet "+o);
      if(o == null){
         preparedStatement.setNull(i, Types.OTHER);
         return;
      }
      try {
         preparedStatement.setObject(i, o.toString().replaceAll("\n", ""), Types.OTHER);
      }catch(Exception e){
         System.out.println("WTF nullSafeSet"+e.getMessage());
         e.printStackTrace();
      }
   }

   @Override
   public Object deepCopy(Object o) throws HibernateException {
      if(o instanceof Json){
         return ((Json)o).clone();
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
      if(cached !=null && cached instanceof String){
         return Json.fromString((String)cached);
      }
      return null;
   }

   @Override
   public Object replace(Object original, Object target, Object owner) throws HibernateException {
      return original;
   }

}
