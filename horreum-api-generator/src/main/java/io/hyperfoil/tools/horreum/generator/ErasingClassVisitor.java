package io.hyperfoil.tools.horreum.generator;

import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ErasingClassVisitor extends ClassVisitor {
   private final String ignoreAnnotation;
   private final List<String> erasedPackages;

   public ErasingClassVisitor(List<String> erasedPackages, ClassVisitor delegated, String ignoreAnnotation) {
      super(Opcodes.ASM7, delegated);
      this.erasedPackages = erasedPackages;
      this.ignoreAnnotation = ignoreAnnotation;
   }

   private boolean isDescriptorErased(String descriptor) {
      String className = descriptor.substring(1).replaceAll("/", ".");
      return erasedPackages.stream().anyMatch(className::startsWith);
   }

   private boolean isNameErased(String name) {
      String className = name.replaceAll("/", ".");
      return erasedPackages.stream().anyMatch(className::startsWith);
   }

   @Override
   public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      if (isNameErased(superName)) {
         superName = "java/lang/Object";
      }
      interfaces = Stream.of(interfaces).filter(iface -> !isNameErased(iface)).toArray(String[]::new);
      super.visit(version, access, name, signature, superName, interfaces);
   }

   @Override
   public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return isDescriptorErased(descriptor) ? null : super.visitAnnotation(descriptor, visible);
   }

   @Override
   public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
      boolean isConstructor = name.equals("<init>");
      return new MethodVisitor(Opcodes.ASM7, visitor) {
         private boolean ignored = false;

         @Override
         public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (ignoreAnnotation.equals(descriptor.substring(1).replaceAll("/", "."))) {
               ignored = true;
               return null;
            }
            return isDescriptorErased(descriptor) ? null : super.visitAnnotation(descriptor, visible);
         }

         @Override
         public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL && isConstructor && name.equals("<init>") && isNameErased(owner)) {
               owner = "java/lang/Object";
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
         }

         @Override
         public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            if (!ignored) {
               super.visitFrame(type, numLocal, local, numStack, stack);
            }
         }
      };
   }

   @Override
   public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
      final FieldVisitor visitor = super.visitField(access, name, descriptor, signature, value);
      return new FieldVisitor(Opcodes.ASM7, visitor) {
         @Override
         public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return isDescriptorErased(descriptor) ? null : super.visitAnnotation(descriptor, visible);
         }
      };
   }
}
