package io.hyperfoil.tools.horreum.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

public class BufferingMethodVisitor extends MethodVisitor {
   private final List<Runnable> buffer = new ArrayList<>();
   private final Supplier<MethodVisitor> supplier;

   protected BufferingMethodVisitor(int api, Supplier<MethodVisitor> supplier) {
      super(api);
      this.supplier = supplier;
   }

   @Override
   public void visitParameter(String name, int access) {
      buffer.add(() -> super.visitParameter(name, access));
   }

   private BufferingAnnotationVisitor addAnnotationVisitor(Supplier<AnnotationVisitor> supplier) {
      BufferingAnnotationVisitor visitor = new BufferingAnnotationVisitor(api, supplier);
      buffer.add(visitor::commit);
      return visitor;
   }

   @Override
   public AnnotationVisitor visitAnnotationDefault() {
      return addAnnotationVisitor(super::visitAnnotationDefault);
   }

   @Override
   public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return addAnnotationVisitor(() -> super.visitAnnotation(descriptor, visible));
   }

   @Override
   public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
      return addAnnotationVisitor(() -> super.visitTypeAnnotation(typeRef, typePath, descriptor, visible));
   }

   @Override
   public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
      buffer.add(() -> super.visitAnnotableParameterCount(parameterCount, visible));
   }

   @Override
   public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
      return addAnnotationVisitor(() -> super.visitParameterAnnotation(parameter, descriptor, visible));
   }

   @Override
   public void visitAttribute(Attribute attribute) {
      buffer.add(() -> super.visitAttribute(attribute));
   }

   @Override
   public void visitCode() {
      buffer.add(super::visitCode);
   }

   @Override
   public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
      buffer.add(() -> super.visitFrame(type, numLocal, local, numStack, stack));
   }

   @Override
   public void visitInsn(int opcode) {
      buffer.add(() -> super.visitInsn(opcode));
   }

   @Override
   public void visitIntInsn(int opcode, int operand) {
      buffer.add(() -> super.visitIntInsn(opcode, operand));
   }

   @Override
   public void visitVarInsn(int opcode, int varIndex) {
      buffer.add(() -> super.visitVarInsn(opcode, varIndex));
   }

   @Override
   public void visitTypeInsn(int opcode, String type) {
      buffer.add(() -> super.visitTypeInsn(opcode, type));
   }

   @Override
   public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      buffer.add(() -> super.visitFieldInsn(opcode, owner, name, descriptor));
   }

   @Override
   public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
      buffer.add(() -> super.visitMethodInsn(opcode, owner, name, descriptor));
   }

   @Override
   public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
      buffer.add(() -> super.visitMethodInsn(opcode, owner, name, descriptor, isInterface));
   }

   @Override
   public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
      buffer.add(() -> super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
   }

   @Override
   public void visitJumpInsn(int opcode, Label label) {
      buffer.add(() -> super.visitJumpInsn(opcode, label));
   }

   @Override
   public void visitLabel(Label label) {
      buffer.add(() -> super.visitLabel(label));
   }

   @Override
   public void visitLdcInsn(Object value) {
      buffer.add(() -> super.visitLdcInsn(value));
   }

   @Override
   public void visitIincInsn(int varIndex, int increment) {
      buffer.add(() -> super.visitIincInsn(varIndex, increment));
   }

   @Override
   public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      buffer.add(() -> super.visitTableSwitchInsn(min, max, dflt, labels));
   }

   @Override
   public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      buffer.add(() -> super.visitLookupSwitchInsn(dflt, keys, labels));
   }

   @Override
   public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      buffer.add(() -> super.visitMultiANewArrayInsn(descriptor, numDimensions));
   }

   @Override
   public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
      return addAnnotationVisitor(() -> super.visitInsnAnnotation(typeRef, typePath, descriptor, visible));
   }

   @Override
   public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      buffer.add(() -> super.visitTryCatchBlock(start, end, handler, type));
   }

   @Override
   public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
      return addAnnotationVisitor(() -> super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible));
   }

   @Override
   public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
      buffer.add(() -> super.visitLocalVariable(name, descriptor, signature, start, end, index));
   }

   @Override
   public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
      return addAnnotationVisitor(() -> super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible));
   }

   @Override
   public void visitLineNumber(int line, Label start) {
      buffer.add(() -> super.visitLineNumber(line, start));
   }

   @Override
   public void visitMaxs(int maxStack, int maxLocals) {
      buffer.add(() -> super.visitMaxs(maxStack, maxLocals));
   }

   @Override
   public void visitEnd() {
      buffer.add(super::visitEnd);
   }

   public void commit() {
      mv = supplier.get();
      buffer.forEach(Runnable::run);
   }
}
