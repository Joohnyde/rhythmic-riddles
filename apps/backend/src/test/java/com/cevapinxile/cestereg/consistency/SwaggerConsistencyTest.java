/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.consistency;

import static org.junit.jupiter.api.Assertions.fail;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.junit.jupiter.api.Test;

public class SwaggerConsistencyTest {

  static {
    ParserConfiguration config = new ParserConfiguration();
    config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // or JAVA_17 / JAVA_15+
    StaticJavaParser.setConfiguration(config);
  }

  private static final Path SRC_MAIN_JAVA = Paths.get("src/main/java");
  private static final Path EXCEPTIONS_MD = Paths.get("../../docs/developer-guide/exceptions.md");

  private static final Set<String> HTTP_MAPPING_ANNOTATIONS =
      Set.of(
          "GetMapping",
          "PostMapping",
          "PutMapping",
          "DeleteMapping",
          "PatchMapping",
          "RequestMapping");

  private static final Set<String> SERVICE_SUFFIXES = Set.of("Service", "Facade", "Manager");

  @Test
  void swaggerContractMustBeStructurallyConsistent() throws Exception {
    Map<String, Set<String>> exceptionStatuses = parseExceptionCatalog();
    Map<String, Boolean> metaParameterAnnotationCache = new HashMap<>();

    List<Path> controllerFiles = findFilesBySuffix("Controller.java");
    if (controllerFiles.isEmpty()) {
      fail("No controller files found under: " + SRC_MAIN_JAVA.toAbsolutePath());
    }

    List<String> violations = new ArrayList<>();

    for (Path controllerFile : controllerFiles) {
      CompilationUnit cu = StaticJavaParser.parse(controllerFile);

      for (ClassOrInterfaceDeclaration controllerClass :
          cu.findAll(ClassOrInterfaceDeclaration.class)) {
        if (!hasAnnotation(controllerClass, "RestController")) {
          continue;
        }

        Map<String, String> serviceFields = extractServiceFields(controllerClass);
        String basePath = extractRequestMappingPath(controllerClass).orElse("");

        for (MethodDeclaration method : controllerClass.getMethods()) {
          Optional<String> mappingAnnotationName = findHttpMappingAnnotation(method);
          if (mappingAnnotationName.isEmpty()) {
            continue;
          }

          String httpMethod = extractHttpMethod(mappingAnnotationName.get(), method).orElse("");
          String methodPath = extractMappingPath(method, mappingAnnotationName.get()).orElse("");
          String fullPath = normalizePath(basePath, methodPath);

          String endpointLabel =
              controllerClass.getNameAsString()
                  + "#"
                  + method.getNameAsString()
                  + " ["
                  + httpMethod
                  + " "
                  + fullPath
                  + "]";

          if (httpMethod.isBlank()) {
            violations.add(endpointLabel + " -> missing HTTP method.");
          }

          validateOperationAnnotation(method, endpointLabel, violations);
          validateParameterDocumentation(
              method, controllerFile, endpointLabel, metaParameterAnnotationCache, violations);
          validateResponses(
              method, endpointLabel, exceptionStatuses, serviceFields, controllerClass, violations);
        }
      }
    }

    if (!violations.isEmpty()) {
      fail("Swagger consistency check failed:\n - " + String.join("\n - ", violations));
    }
  }

  private void validateOperationAnnotation(
      MethodDeclaration method, String endpointLabel, List<String> violations) {
    Optional<AnnotationExpr> operation = findAnnotation(method, "Operation");
    if (operation.isEmpty()) {
      violations.add(endpointLabel + " -> missing @Operation.");
      return;
    }

    String summary = getNamedAnnotationValue(operation.get(), "summary").orElse("").trim();
    String description = getNamedAnnotationValue(operation.get(), "description").orElse("").trim();

    if (summary.isBlank()) {
      violations.add(endpointLabel + " -> missing @Operation.summary.");
    }
    if (description.isBlank()) {
      violations.add(endpointLabel + " -> missing @Operation.description.");
    }
  }

  private void validateParameterDocumentation(
      MethodDeclaration method,
      Path controllerFile,
      String endpointLabel,
      Map<String, Boolean> metaParameterAnnotationCache,
      List<String> violations) {
    for (com.github.javaparser.ast.body.Parameter parameter : method.getParameters()) {
      boolean isPathVariable = hasAnnotation(parameter, "PathVariable");
      boolean isRequestParam = hasAnnotation(parameter, "RequestParam");
      boolean hasRequestBody = hasAnnotation(parameter, "RequestBody");

      if (hasRequestBody) {
        // Source-level check: @RequestBody is what springdoc uses to expose a request body.
        continue;
      }

      if (!isPathVariable && !isRequestParam) {
        continue;
      }

      boolean documented =
          hasAnnotation(parameter, "Parameter")
              || hasMetaParameterAnnotation(parameter, metaParameterAnnotationCache);

      if (!documented) {
        String kind = isPathVariable ? "path" : "query";
        violations.add(
            endpointLabel
                + " -> undocumented "
                + kind
                + " parameter: "
                + parameter.getNameAsString());
      }
    }
  }

  private void validateResponses(
      MethodDeclaration controllerMethod,
      String endpointLabel,
      Map<String, Set<String>> exceptionStatuses,
      Map<String, String> serviceFields,
      ClassOrInterfaceDeclaration controllerClass,
      List<String> violations)
      throws IOException {
    Set<String> documentedResponseCodes = extractDocumentedResponseCodes(controllerMethod);

    if (documentedResponseCodes.stream().noneMatch(code -> code.startsWith("2"))) {
      violations.add(endpointLabel + " -> missing at least one documented success (2xx) response.");
    }

    if (!documentedResponseCodes.contains("500")) {
      violations.add(endpointLabel + " -> missing documented 500 response.");
    }

    Set<String> inferredStatuses =
        inferExpectedExceptionStatuses(
            controllerMethod, controllerClass, serviceFields, exceptionStatuses);

    for (String status : inferredStatuses) {
      if (!documentedResponseCodes.contains(status)) {
        violations.add(
            endpointLabel
                + " -> missing documented error response for inferred status "
                + status
                + ".");
      }
    }
  }

  private Set<String> inferExpectedExceptionStatuses(
      MethodDeclaration controllerMethod,
      ClassOrInterfaceDeclaration controllerClass,
      Map<String, String> serviceFields,
      Map<String, Set<String>> exceptionStatuses)
      throws IOException {
    Set<String> result = new TreeSet<>();

    Optional<TryStmt> tryStmtOpt = controllerMethod.findFirst(TryStmt.class);
    if (tryStmtOpt.isEmpty()) {
      return result;
    }

    TryStmt tryStmt = tryStmtOpt.get();

    List<MethodCallExpr> serviceCalls =
        tryStmt
            .getTryBlock()
            .findAll(
                MethodCallExpr.class,
                call -> {
                  if (call.getScope().isEmpty()) {
                    return false;
                  }
                  Expression scope = call.getScope().get();
                  return scope.isNameExpr()
                      && serviceFields.containsKey(scope.asNameExpr().getNameAsString());
                });

    for (MethodCallExpr serviceCall : serviceCalls) {
      String serviceFieldName = serviceCall.getScope().get().asNameExpr().getNameAsString();
      String serviceType = serviceFields.get(serviceFieldName);
      if (serviceType == null || serviceType.isBlank()) {
        continue;
      }

      Optional<Path> implFileOpt = findServiceImplementationFile(serviceType);
      if (implFileOpt.isEmpty()) {
        continue;
      }

      CompilationUnit serviceCu = StaticJavaParser.parse(implFileOpt.get());
      Optional<ClassOrInterfaceDeclaration> serviceClassOpt =
          serviceCu.findFirst(
              ClassOrInterfaceDeclaration.class,
              c -> c.getNameAsString().equals(serviceType + "Impl"));

      if (serviceClassOpt.isEmpty()) {
        continue;
      }

      ClassOrInterfaceDeclaration serviceClass = serviceClassOpt.get();
      Optional<MethodDeclaration> serviceMethodOpt =
          findMethodByNameAndArity(
              serviceClass, serviceCall.getNameAsString(), serviceCall.getArguments().size());

      if (serviceMethodOpt.isEmpty()) {
        continue;
      }

      MethodDeclaration serviceMethod = serviceMethodOpt.get();

      Set<String> inferredExceptionNames =
          inferExceptionTypesFromServiceMethod(serviceMethod, serviceClass);
      for (String exceptionName : inferredExceptionNames) {
        Set<String> statuses = exceptionStatuses.get(exceptionName);
        if (statuses != null) {
          result.addAll(statuses);
        }
      }
    }

    return result;
  }

  private Set<String> inferExceptionTypesFromServiceMethod(
      MethodDeclaration serviceMethod, ClassOrInterfaceDeclaration serviceClass) {
    Set<String> result = new TreeSet<>();

    // 1) Direct throws in the service method body
    for (ThrowStmt throwStmt : serviceMethod.findAll(ThrowStmt.class)) {
      Optional<String> exceptionType = extractThrownExceptionType(throwStmt);
      if (exceptionType.isEmpty()) {
        continue;
      }

      String type = exceptionType.get();
      if ("DerivedException".equals(type)) {
        continue;
      }

      if (!isCaughtByAncestorTry(throwStmt, type)) {
        result.add(type);
      }
    }

    // 2) Helper methods called from inside the service method:
    //    take only what they DECLARE in their throws clause.
    List<MethodCallExpr> helperCalls =
        serviceMethod.findAll(MethodCallExpr.class, call -> isSameClassHelperCall(call));

    for (MethodCallExpr helperCall : helperCalls) {
      Optional<MethodDeclaration> helperMethod =
          findMethodByNameAndArity(
              serviceClass, helperCall.getNameAsString(), helperCall.getArguments().size());

      if (helperMethod.isEmpty()) {
        continue;
      }

      for (ReferenceTypeName thrown : extractThrownTypes(helperMethod.get())) {
        String thrownType = thrown.simpleName();
        if ("DerivedException".equals(thrownType)) {
          continue;
        }
        if (!isCaughtByAncestorTry(helperCall, thrownType)) {
          result.add(thrownType);
        }
      }
    }

    return result;
  }

  private boolean isSameClassHelperCall(MethodCallExpr call) {
    if (call.getScope().isEmpty()) {
      return true;
    }
    Expression scope = call.getScope().get();
    return scope.isThisExpr();
  }

  private Optional<String> extractThrownExceptionType(ThrowStmt throwStmt) {
    Expression expression = throwStmt.getExpression();

    if (expression.isObjectCreationExpr()) {
      ObjectCreationExpr creation = expression.asObjectCreationExpr();
      return Optional.of(creation.getType().getNameAsString());
    }

    return Optional.empty();
  }

  private boolean isCaughtByAncestorTry(Node node, String thrownTypeSimpleName) {
    Node current = node;

    while (current.getParentNode().isPresent()) {
      Node parent = current.getParentNode().get();

      if (parent instanceof TryStmt tryStmt) {
        if (tryStmt.getTryBlock().isAncestorOf(node) || tryStmt.getTryBlock() == current) {
          for (CatchClause catchClause : tryStmt.getCatchClauses()) {
            if (catchMatches(catchClause, thrownTypeSimpleName)) {
              return true;
            }
          }
        }
      }

      current = parent;
    }

    return false;
  }

  private boolean catchMatches(CatchClause catchClause, String thrownTypeSimpleName) {
    Type catchType = catchClause.getParameter().getType();

    if (catchType.isUnionType()) {
      return catchType.asUnionType().getElements().stream()
          .anyMatch(type -> catchTypeMatchesName(type.asString(), thrownTypeSimpleName));
    }

    return catchTypeMatchesName(catchType.asString(), thrownTypeSimpleName);
  }

  private boolean catchTypeMatchesName(String catchTypeName, String thrownTypeSimpleName) {
    String simpleCatch = simplifyTypeName(catchTypeName);

    return simpleCatch.equals(thrownTypeSimpleName)
        || "DerivedException".equals(simpleCatch)
        || "Exception".equals(simpleCatch)
        || "Throwable".equals(simpleCatch);
  }

  private List<ReferenceTypeName> extractThrownTypes(MethodDeclaration method) {
    List<ReferenceTypeName> result = new ArrayList<>();
    for (ReferenceTypeName type :
        method.getThrownExceptions().stream()
            .map(t -> new ReferenceTypeName(simplifyTypeName(t.asString())))
            .toList()) {
      result.add(type);
    }
    return result;
  }

  private Set<String> extractDocumentedResponseCodes(MethodDeclaration method) {
    Set<String> result = new TreeSet<>();

    for (AnnotationExpr annotation : method.getAnnotations()) {
      String name = annotation.getNameAsString();

      if ("ApiResponse".equals(name)) {
        getNamedAnnotationValue(annotation, "responseCode").ifPresent(result::add);
      } else if ("ApiResponses".equals(name)) {
        extractApiResponsesFromContainer(annotation, result);
      }
    }

    return result;
  }

  private void extractApiResponsesFromContainer(AnnotationExpr annotation, Set<String> result) {
    if (annotation.isSingleMemberAnnotationExpr()) {
      Expression member = annotation.asSingleMemberAnnotationExpr().getMemberValue();
      if (member.isArrayInitializerExpr()) {
        ArrayInitializerExpr array = member.asArrayInitializerExpr();
        for (Expression value : array.getValues()) {
          if (value.isNormalAnnotationExpr()) {
            getNamedAnnotationValue(value.asNormalAnnotationExpr(), "responseCode")
                .ifPresent(result::add);
          }
        }
      }
    } else if (annotation.isNormalAnnotationExpr()) {
      getNamedAnnotationValue(annotation, "value")
          .ifPresent(
              ignored -> {
                // not used in your codebase at the moment
              });
    }
  }

  private Optional<String> findHttpMappingAnnotation(MethodDeclaration method) {
    return method.getAnnotations().stream()
        .map(AnnotationExpr::getNameAsString)
        .filter(HTTP_MAPPING_ANNOTATIONS::contains)
        .findFirst();
  }

  private Optional<String> extractHttpMethod(String annotationName, MethodDeclaration method) {
    return switch (annotationName) {
      case "GetMapping" -> Optional.of("GET");
      case "PostMapping" -> Optional.of("POST");
      case "PutMapping" -> Optional.of("PUT");
      case "DeleteMapping" -> Optional.of("DELETE");
      case "PatchMapping" -> Optional.of("PATCH");
      case "RequestMapping" ->
          getNamedAnnotationValue(findAnnotation(method, "RequestMapping").orElseThrow(), "method")
              .map(this::normalizeRequestMethodEnum);
      default -> Optional.empty();
    };
  }

  private String normalizeRequestMethodEnum(String raw) {
    String value = raw.trim();
    int dot = value.lastIndexOf('.');
    return dot >= 0 ? value.substring(dot + 1) : value;
  }

  private Optional<String> extractRequestMappingPath(NodeWithAnnotations<?> node) {
    Optional<AnnotationExpr> requestMapping = findAnnotation(node, "RequestMapping");
    if (requestMapping.isPresent()) {
      Optional<String> path = extractPathFromMappingAnnotation(requestMapping.get());
      if (path.isPresent()) {
        return path;
      }
    }
    return Optional.empty();
  }

  private Optional<String> extractMappingPath(
      MethodDeclaration method, String mappingAnnotationName) {
    Optional<AnnotationExpr> annotation = findAnnotation(method, mappingAnnotationName);
    return annotation.flatMap(this::extractPathFromMappingAnnotation);
  }

  private Optional<String> extractPathFromMappingAnnotation(AnnotationExpr annotation) {
    if (annotation.isSingleMemberAnnotationExpr()) {
      return Optional.of(
          stripQuotes(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString()));
    }

    if (annotation.isNormalAnnotationExpr()) {
      NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
      for (var pair : normal.getPairs()) {
        if ("value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString())) {
          return Optional.of(stripQuotes(pair.getValue().toString()));
        }
      }
    }

    return Optional.of("");
  }

  private Map<String, String> extractServiceFields(ClassOrInterfaceDeclaration controllerClass) {
    Map<String, String> result = new HashMap<>();

    for (FieldDeclaration field : controllerClass.getFields()) {
      String type = field.getElementType().asString();
      if (!looksLikeServiceType(type)) {
        continue;
      }

      field
          .getVariables()
          .forEach(var -> result.put(var.getNameAsString(), simplifyTypeName(type)));
    }

    return result;
  }

  private boolean looksLikeServiceType(String typeName) {
    String simple = simplifyTypeName(typeName);
    for (String suffix : SERVICE_SUFFIXES) {
      if (simple.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private Optional<Path> findServiceImplementationFile(String serviceType) throws IOException {
    String fileName = serviceType + "Impl.java";
    try (var stream = Files.walk(SRC_MAIN_JAVA)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst();
    }
  }

  private Optional<MethodDeclaration> findMethodByNameAndArity(
      ClassOrInterfaceDeclaration type, String methodName, int arity) {
    return type.getMethodsByName(methodName).stream()
        .filter(m -> m.getParameters().size() == arity)
        .findFirst();
  }

  private Map<String, Set<String>> parseExceptionCatalog() throws IOException {
    if (!Files.exists(EXCEPTIONS_MD)) {
      fail("Exception catalog not found at: " + EXCEPTIONS_MD.toAbsolutePath());
    }

    Map<String, Set<String>> result = new HashMap<>();
    List<String> lines = Files.readAllLines(EXCEPTIONS_MD);

    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (!line.startsWith("|")) {
        continue;
      }
      if (line.matches("^\\|[\\-:| ]+\\|$")) {
        continue;
      }

      String[] parts = line.split("\\|", -1);
      if (parts.length < 5) {
        continue;
      }

      String code = parts[1].trim();
      String http = parts[2].trim();
      String title = parts[3].trim();
      String exceptionCell = parts[4].trim();

      if ("Code".equalsIgnoreCase(code) || "Exception class".equalsIgnoreCase(exceptionCell)) {
        continue;
      }

      String exceptionName = exceptionCell.replace("`", "");
      int paren = exceptionName.indexOf('(');
      if (paren >= 0) {
        exceptionName = exceptionName.substring(0, paren).trim();
      }

      if (exceptionName.endsWith("Exception") && !http.isBlank() && !title.isBlank()) {
        result.computeIfAbsent(exceptionName, k -> new TreeSet<>()).add(http);
      }
    }

    return result;
  }

  private boolean hasMetaParameterAnnotation(
      com.github.javaparser.ast.body.Parameter parameter, Map<String, Boolean> cache) {
    for (AnnotationExpr annotation : parameter.getAnnotations()) {
      String annotationName = annotation.getNameAsString();
      if ("Parameter".equals(annotationName)) {
        return true;
      }

      boolean isMetaParameter =
          cache.computeIfAbsent(annotationName, this::annotationDeclaresParameter);
      if (isMetaParameter) {
        return true;
      }
    }
    return false;
  }

  private boolean annotationDeclaresParameter(String annotationSimpleName) {
    try {
      Optional<Path> annotationFile = findSourceFileByName(annotationSimpleName + ".java");
      if (annotationFile.isEmpty()) {
        return false;
      }

      CompilationUnit cu = StaticJavaParser.parse(annotationFile.get());
      Optional<AnnotationDeclaration> declaration =
          cu.findFirst(
              AnnotationDeclaration.class, a -> a.getNameAsString().equals(annotationSimpleName));

      return declaration.isPresent() && hasAnnotation(declaration.get(), "Parameter");
    } catch (Exception ignored) {
      return false;
    }
  }

  private Optional<Path> findSourceFileByName(String fileName) throws IOException {
    try (var stream = Files.walk(SRC_MAIN_JAVA)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst();
    }
  }

  private boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationName) {
    return findAnnotation(node, annotationName).isPresent();
  }

  private Optional<AnnotationExpr> findAnnotation(
      NodeWithAnnotations<?> node, String annotationName) {
    return node.getAnnotations().stream()
        .filter(a -> a.getNameAsString().equals(annotationName))
        .findFirst();
  }

  private Optional<String> getNamedAnnotationValue(AnnotationExpr annotation, String fieldName) {
    if (annotation.isNormalAnnotationExpr()) {
      return annotation.asNormalAnnotationExpr().getPairs().stream()
          .filter(p -> p.getNameAsString().equals(fieldName))
          .map(p -> stripQuotes(p.getValue().toString()))
          .findFirst();
    }

    if (annotation.isSingleMemberAnnotationExpr() && "value".equals(fieldName)) {
      return Optional.of(
          stripQuotes(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString()));
    }

    return Optional.empty();
  }

  private String normalizePath(String basePath, String methodPath) {
    String base = basePath == null ? "" : basePath.trim();
    String method = methodPath == null ? "" : methodPath.trim();

    String combined = (base + "/" + method).replaceAll("/+", "/");
    if (!combined.startsWith("/")) {
      combined = "/" + combined;
    }
    if (combined.length() > 1 && combined.endsWith("/")) {
      combined = combined.substring(0, combined.length() - 1);
    }
    return combined;
  }

  private List<Path> findFilesBySuffix(String suffix) throws IOException {
    try (var stream = Files.walk(SRC_MAIN_JAVA)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(suffix))
          .toList();
    }
  }

  private String stripQuotes(String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.startsWith("\"\"\"") && trimmed.endsWith("\"\"\"") && trimmed.length() >= 6) {
      return trimmed.substring(3, trimmed.length() - 3);
    }
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private String simplifyTypeName(String rawType) {
    String trimmed = rawType.trim();
    int genericStart = trimmed.indexOf('<');
    if (genericStart >= 0) {
      trimmed = trimmed.substring(0, genericStart);
    }
    int dot = trimmed.lastIndexOf('.');
    if (dot >= 0) {
      trimmed = trimmed.substring(dot + 1);
    }
    return trimmed;
  }

  private record ReferenceTypeName(String simpleName) {}
}
