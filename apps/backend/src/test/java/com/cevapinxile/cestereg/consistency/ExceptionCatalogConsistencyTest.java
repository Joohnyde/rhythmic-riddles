package com.cevapinxile.cestereg.consistency;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class ExceptionCatalogConsistencyTest {

  /** Adjust these paths if needed. */
  private static final String EXCEPTION_PACKAGE = "com.cevapinxile.cestereg.common.exception";

  private static final Path EXCEPTION_CATALOG =
      Paths.get("../../docs/developer-guide/exceptions.md");

  @Test
  void exceptionCatalogMustMatchCode() throws Exception {

    Set<String> codeExceptions = findDerivedExceptionSubclasses();
    Set<String> catalogExceptions = parseCatalogExceptions();

    Set<String> missingInCatalog = new TreeSet<>(codeExceptions);
    missingInCatalog.removeAll(catalogExceptions);

    Set<String> staleInCatalog = new TreeSet<>(catalogExceptions);
    staleInCatalog.removeAll(codeExceptions);

    if (!missingInCatalog.isEmpty() || !staleInCatalog.isEmpty()) {

      StringBuilder error = new StringBuilder("\nException catalog consistency check failed.\n");

      if (!missingInCatalog.isEmpty()) {
        error.append("\nExceptions missing in exception-catalog.md:\n");
        missingInCatalog.forEach(e -> error.append(" - ").append(e).append("\n"));
      }

      if (!staleInCatalog.isEmpty()) {
        error.append("\nExceptions documented but not present in code:\n");
        staleInCatalog.forEach(e -> error.append(" - ").append(e).append("\n"));
      }

      fail(error.toString());
    }
  }

  /** Finds all subclasses of DerivedException using reflection. */
  private Set<String> findDerivedExceptionSubclasses() throws Exception {

    Class<?> baseException = Class.forName(EXCEPTION_PACKAGE + ".DerivedException");

    Set<String> result = new HashSet<>();

    String packagePath = EXCEPTION_PACKAGE.replace(".", "/");

    Enumeration<java.net.URL> resources =
        Thread.currentThread().getContextClassLoader().getResources(packagePath);

    while (resources.hasMoreElements()) {

      java.net.URL resource = resources.nextElement();

      Path directory = Paths.get(resource.toURI());

      try (Stream<Path> paths = Files.walk(directory)) {

        List<Path> classFiles =
            paths.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());

        for (Path path : classFiles) {

          String className =
              path.toString()
                  .replace(directory.toString(), "")
                  .replace(FileSystems.getDefault().getSeparator(), ".")
                  .replaceAll("^\\.", "")
                  .replace(".class", "");

          String fqcn = EXCEPTION_PACKAGE + "." + className;

          Class<?> clazz = Class.forName(fqcn);

          if (baseException.isAssignableFrom(clazz)
              && !clazz.equals(baseException)
              && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {

            result.add(clazz.getSimpleName());
          }
        }
      }
    }

    return result;
  }

  /** Extract exception names from the markdown catalog table. */
  private Set<String> parseCatalogExceptions() throws IOException {
    if (!Files.exists(EXCEPTION_CATALOG)) {
      fail("Exception catalog not found at: " + EXCEPTION_CATALOG.toAbsolutePath());
    }

    List<String> lines = Files.readAllLines(EXCEPTION_CATALOG);

    Set<String> exceptions = new HashSet<>();

    for (String rawLine : lines) {
      String line = rawLine.trim();

      if (!line.startsWith("|")) {
        continue;
      }

      // Skip separator row like |---|---:|---|
      if (line.matches("^\\|[\\-:| ]+\\|$")) {
        continue;
      }

      String[] parts = line.split("\\|", -1);

      // Markdown table rows usually look like:
      // [0]="", [1]=Code, [2]=HTTP, [3]=Title, [4]=Exception class, [5]=Typical meaning, [6]=""
      if (parts.length < 5) {
        continue;
      }

      String candidate = parts[4].trim();

      // Skip header
      if (candidate.equalsIgnoreCase("Exception class")) {
        continue;
      }

      // Handle values like `MissingArgumentException`
      candidate = candidate.replace("`", "").trim();

      // Handle values like AssetAccessException(Reason.NOT_FOUND)
      int parenIndex = candidate.indexOf('(');
      if (parenIndex >= 0) {
        candidate = candidate.substring(0, parenIndex).trim();
      }

      if (candidate.endsWith("Exception")) {
        exceptions.add(candidate);
      }
    }

    return exceptions;
  }
}
