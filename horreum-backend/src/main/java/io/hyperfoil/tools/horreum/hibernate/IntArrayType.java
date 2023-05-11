package io.hyperfoil.tools.horreum.hibernate;


import org.hibernate.type.BasicTypeReference;

public class IntArrayType {

    public static final BasicTypeReference<int[]> INT_ARRAY = new BasicTypeReference("int-array", int[].class, 666);

}
