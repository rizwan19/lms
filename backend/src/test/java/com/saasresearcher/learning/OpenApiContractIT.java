package com.saasresearcher.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.common.WebConfiguration;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/** Keeps docs/api/openapi.yaml synchronized with the registered REST routes. */
class OpenApiContractIT extends PostgresIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void documentedRoutesMatchImplementedRoutes() throws IOException {
        assertThat(documentedRoutes()).isEqualTo(implementedRoutes());
    }

    private Set<String> implementedRoutes() {
        Set<String> routes = new TreeSet<>();
        mappings.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(pattern -> pattern.startsWith(WebConfiguration.API_BASE))
                .forEach(pattern -> info.getMethodsCondition().getMethods().forEach(method ->
                        routes.add(method.name() + " " + pattern.substring(WebConfiguration.API_BASE.length())))));
        return routes;
    }

    @SuppressWarnings("unchecked")
    private Set<String> documentedRoutes() throws IOException {
        Set<String> routes = new TreeSet<>();
        try (InputStream input = Files.newInputStream(Path.of("../docs/api/openapi.yaml"))) {
            Map<String, Object> document = new Yaml().load(input);
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");
            if (paths != null) {
                paths.forEach((path, operations) -> operations.keySet().stream()
                        .filter(HTTP_METHODS::contains)
                        .forEach(method -> routes.add(method.toUpperCase() + " " + path)));
            }
        }
        return routes;
    }
}
