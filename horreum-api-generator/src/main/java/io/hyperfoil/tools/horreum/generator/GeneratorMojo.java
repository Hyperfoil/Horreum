package io.hyperfoil.tools.horreum.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@Mojo(name = "apigen", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratorMojo extends AbstractMojo {
   @Parameter(readonly = true, defaultValue = "${project}")
   private MavenProject project;

   @Parameter
   private String sources;

   @Parameter
   private List<String> transformedPackages;

   @Parameter
   private List<String> erasedPackages;

   @Parameter(defaultValue = "${project.build.outputDirectory}")
   private String target;

   private boolean isDescriptorErased(String descriptor) {
      String className = descriptor.substring(1).replaceAll("/", ".");
      return erasedPackages.stream().anyMatch(className::startsWith);
   }

   private boolean isNameErased(String name) {
      String className = name.replaceAll("/", ".");
      return erasedPackages.stream().anyMatch(className::startsWith);
   }

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      try {
         Path sourcesPath = Path.of(sources);
         List<Path> candidates = Files.walk(sourcesPath).filter(path -> path.toFile().getName().endsWith(".class")).collect(Collectors.toList());
         for (Path path : candidates) {
            String relative = sourcesPath.relativize(path).toString();
            String className = relative.substring(0, relative.length() - 6).replaceAll("/", ".");
            if (transformedPackages.stream().anyMatch(className::startsWith)) {
               try (InputStream stream = Files.newInputStream(path)) {
                  ClassReader reader = new ClassReader(stream);
                  ClassWriter writer = new ClassWriter(reader, 0);
                  reader.accept(new ErasingClassVisitor(writer), 0);
                  Path targetPath = Path.of(target, relative);
                  if (!targetPath.getParent().toFile().mkdirs() && !targetPath.getParent().toFile().exists()) {
                     throw new MojoExecutionException("Cannot create parent directory for " + targetPath);
                  }
                  Files.write(targetPath, writer.toByteArray());
               }
            }
         }
      } catch (IOException e) {
         throw new MojoExecutionException(e);
      }
   }

   private class ErasingClassVisitor extends ClassVisitor {
      public ErasingClassVisitor(ClassWriter writer) {
         super(Opcodes.ASM7, writer);
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
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
               return isDescriptorErased(descriptor) ? null : super.visitAnnotation(descriptor, visible);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
               if (opcode == Opcodes.INVOKESPECIAL && isConstructor && name.equals("<init>") && isNameErased(owner)) {
                  owner = "java/lang/Object";
               }
               super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
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
}
