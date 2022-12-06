package io.hyperfoil.tools.horreum.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.objectweb.asm.AnnotationVisitor;

public class BufferingAnnotationVisitor extends AnnotationVisitor {
   private final List<Runnable> buffer = new ArrayList<>();
   private final Supplier<AnnotationVisitor> supplier;

   protected BufferingAnnotationVisitor(int api, Supplier<AnnotationVisitor> annotationVisitor) {
      super(api);
      supplier = annotationVisitor;
   }

   @Override
   public void visit(String name, Object value) {
      buffer.add(() -> super.visit(name, value));
   }

   @Override
   public void visitEnum(String name, String descriptor, String value) {
      buffer.add(() -> super.visitEnum(name, descriptor, value));
   }

   private BufferingAnnotationVisitor addAnnotationVisitor(Supplier<AnnotationVisitor> supplier) {
      BufferingAnnotationVisitor visitor = new BufferingAnnotationVisitor(api, supplier);
      buffer.add(visitor::commit);
      return visitor;
   }

   @Override
   public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return addAnnotationVisitor(() -> super.visitAnnotation(name, descriptor));
   }

   @Override
   public AnnotationVisitor visitArray(String name) {
      return addAnnotationVisitor(() -> super.visitArray(name));
   }

   @Override
   public void visitEnd() {
      buffer.add(super::visitEnd);
   }

   public void commit() {
      av = supplier.get();
      buffer.forEach(Runnable::run);
   }
}
