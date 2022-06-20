package io.hyperfoil.tools.horreum.generator;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * We are adding @Encoded to every parameter with @PathParam as since https://issues.redhat.com/browse/RESTEASY-1475
 * it has a weird meaning - without this annotation the slash will be retained and NOT encoded into %2F.
 * As a result such parameter will lead to unexpected paths.
 */
public class PathParamEncodingVisitor extends ClassVisitor {
   public PathParamEncodingVisitor(ClassVisitor visitor) {
      super(Opcodes.ASM7, visitor);
   }

   @Override
   public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
      return new MethodVisitor(Opcodes.ASM7, visitor) {
         @Override
         public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            if ("Ljavax/ws/rs/PathParam;".equals(descriptor)) {
               super.visitParameterAnnotation(parameter, "Ljavax/ws/rs/Encoded;", true);
            }
            return super.visitParameterAnnotation(parameter, descriptor, visible);
         }
      };
   }
}
