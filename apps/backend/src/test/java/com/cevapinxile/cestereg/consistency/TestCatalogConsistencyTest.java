/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.consistency;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

/**
 * @author denijal
 */
public class TestCatalogConsistencyTest {

  private static final Path TEST_SOURCE_ROOT = Paths.get("src/test/java");
  private static final Path TEST_CATALOG =
      Paths.get("../../docs/developer-guide/testing/test-catalog.csv");

  private static final Set<String> SUPPORTED_TEST_ANNOTATIONS =
      Set.of("@Test", "@ParameterizedTest", "@RepeatedTest", "@TestFactory", "@TestTemplate");

  @Test
  void testCatalogMustMatchCode() throws Exception {
    Set<TestKey> discoveredTests = discoverTestsFromSource();
    CatalogParseResult catalog = parseTestCatalog();

    Set<TestKey> missingInCatalog = new TreeSet<>(discoveredTests);
    missingInCatalog.removeAll(catalog.entries());

    Set<TestKey> staleInCatalog = new TreeSet<>(catalog.entries());
    staleInCatalog.removeAll(discoveredTests);

    StringBuilder error = new StringBuilder();

    if (!catalog.duplicateEntries().isEmpty()) {
      error.append("\nDuplicate entries in test-catalog.csv:\n");
      for (TestKey duplicate : catalog.duplicateEntries()) {
        error.append(" - ").append(duplicate).append("\n");
      }
    }

    if (!missingInCatalog.isEmpty()) {
      error.append("\nTests present in code but missing in test-catalog.csv:\n");
      for (TestKey missing : missingInCatalog) {
        error.append(" - ").append(missing).append("\n");
      }
    }

    if (!staleInCatalog.isEmpty()) {
      error.append("\nEntries present in test-catalog.csv but not found in code:\n");
      for (TestKey stale : staleInCatalog) {
        error.append(" - ").append(stale).append("\n");
      }
    }

    if (!error.isEmpty()) {
      fail("Test catalog consistency check failed." + error);
    }
  }

  private Set<TestKey> discoverTestsFromSource() throws IOException {
    if (!Files.exists(TEST_SOURCE_ROOT)) {
      fail("Test source root not found at: " + TEST_SOURCE_ROOT.toAbsolutePath());
    }

    Set<TestKey> result = new TreeSet<>();

    try (Stream<Path> pathStream = Files.walk(TEST_SOURCE_ROOT)) {
      List<Path> javaFiles =
          pathStream
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .toList();

      for (Path javaFile : javaFiles) {
        result.addAll(extractTestsFromJavaFile(javaFile));
      }
    }

    return result;
  }

  private Set<TestKey> extractTestsFromJavaFile(Path javaFile) throws IOException {
    List<String> lines = Files.readAllLines(javaFile);

    Set<TestKey> discovered = new TreeSet<>();

    String fileName = javaFile.getFileName().toString();
    String currentClassName = null;

    Pattern classPattern = Pattern.compile("\\bclass\\s+([A-Za-z0-9_]+)\\b");
    Pattern methodPattern =
        Pattern.compile(
            "(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:<[^>]+>\\s*)?[A-Za-z0-9_<>\\[\\], ?]+\\s+([A-Za-z0-9_]+)\\s*\\(");

    for (int i = 0; i < lines.size(); i++) {
      String rawLine = lines.get(i);
      String line = rawLine.trim();

      Matcher classMatcher = classPattern.matcher(line);
      if (classMatcher.find()) {
        currentClassName = classMatcher.group(1);
      }

      if (!isSupportedTestAnnotation(line)) {
        continue;
      }

      String methodName = findNextMethodName(lines, i + 1, methodPattern);
      if (methodName == null) {
        continue;
      }

      if (currentClassName == null) {
        currentClassName = fileName.replace(".java", "");
      }

      discovered.add(new TestKey(fileName, currentClassName, methodName));
    }

    return discovered;
  }

  private boolean isSupportedTestAnnotation(String line) {
    for (String annotation : SUPPORTED_TEST_ANNOTATIONS) {
      if (line.startsWith(annotation) || line.contains(annotation + "(")) {
        return true;
      }
    }
    return false;
  }

  private String findNextMethodName(List<String> lines, int startIndex, Pattern methodPattern) {
    for (int i = startIndex; i < lines.size(); i++) {
      String line = lines.get(i).trim();

      if (line.isBlank()) {
        continue;
      }

      if (line.startsWith("@")) {
        continue;
      }

      if (line.startsWith("//")) {
        continue;
      }

      Matcher methodMatcher = methodPattern.matcher(line);
      if (methodMatcher.find()) {
        return methodMatcher.group(1);
      }

      if (line.contains(";")) {
        return null;
      }
    }

    return null;
  }

  private CatalogParseResult parseTestCatalog() throws IOException {
    if (!Files.exists(TEST_CATALOG)) {
      fail("Test catalog not found at: " + TEST_CATALOG.toAbsolutePath());
    }

    Set<TestKey> entries = new TreeSet<>();
    Set<TestKey> duplicates = new TreeSet<>();

    try (var reader = Files.newBufferedReader(TEST_CATALOG);
        var parser =
            org.apache.commons.csv.CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(reader)) {

      Map<String, Integer> headerMap = parser.getHeaderMap();
      if (headerMap == null) {
        fail("Could not read CSV header from: " + TEST_CATALOG.toAbsolutePath());
      }

      requireCsvHeader(headerMap, "file");
      requireCsvHeader(headerMap, "suite");
      requireCsvHeader(headerMap, "test_name");

      for (org.apache.commons.csv.CSVRecord record : parser) {
        String file = getCsvValue(record, "file");
        if (file.contains("/")) file = file.substring(file.lastIndexOf('/') + 1);
        String suite = getCsvValue(record, "suite");
        if (StringUtils.isBlank(suite)) suite = file;
        if (suite.contains(".")) suite = suite.substring(0, suite.lastIndexOf('.'));
        String testName = getCsvValue(record, "test_name");

        if (file.isBlank() || testName.isBlank()) {
          fail(
              "Malformed row in test-catalog.csv at CSV record "
                  + record.getRecordNumber()
                  + ". Required columns: file, test_name");
        }

        TestKey key = new TestKey(file, suite == null ? "" : suite.trim(), testName);

        if (!entries.add(key)) {
          duplicates.add(key);
        }
      }
    }

    return new CatalogParseResult(entries, duplicates);
  }

  private void requireCsvHeader(Map<String, Integer> headerMap, String required) {
    boolean exists =
        headerMap.keySet().stream()
            .anyMatch(h -> normalizeHeader(h).equals(normalizeHeader(required)));

    if (!exists) {
      fail("Missing required header in test-catalog.csv: " + required);
    }
  }

  private String getCsvValue(CSVRecord record, String headerName) {
    for (String header : record.getParser().getHeaderMap().keySet()) {
      if (normalizeHeader(header).equals(normalizeHeader(headerName))) {
        String value = record.get(header);
        return value == null ? "" : value.trim();
      }
    }
    return "";
  }

  private String normalizeHeader(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private record CatalogParseResult(Set<TestKey> entries, Set<TestKey> duplicateEntries) {}

  private record TestKey(String file, String suite, String testName)
      implements Comparable<TestKey> {
    @Override
    public int compareTo(TestKey other) {
      int byFile = this.file.compareTo(other.file);
      if (byFile != 0) return byFile;

      int bySuite = this.suite.compareTo(other.suite);
      if (bySuite != 0) return bySuite;

      return this.testName.compareTo(other.testName);
    }

    @Override
    public String toString() {
      return file + " | " + suite + " | " + testName;
    }
  }
}
