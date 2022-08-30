package io.hyperfoil.tools.horreum.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

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

   @Parameter
   private String ignoreAnnotation;

   @Parameter(defaultValue = "${project.build.outputDirectory}")
   private String target;

   @Override
   public void execute() throws MojoExecutionException {
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
                  reader.accept(buildVisitorChain(writer), 0);
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

   private ErasingClassVisitor buildVisitorChain(ClassWriter writer) {
      return new ErasingClassVisitor(erasedPackages, new PathParamEncodingVisitor(writer), ignoreAnnotation);
   }

}
