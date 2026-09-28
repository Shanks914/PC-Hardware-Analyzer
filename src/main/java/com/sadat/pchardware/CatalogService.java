package com.sadat.pchardware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches and parses the project catalog asynchronously, with packaged JSON for offline startup. */
public class CatalogService implements AutoCloseable {
    private static final String REMOTE_BASE =
            "https://raw.githubusercontent.com/Shanks914/PC-Hardware-Analyzer-Catalog/main/";
    private static final List<String> CATALOG_FILES = List.of(
            "processors.json", "motherboards.json", "desktop-ram.json", "ssds.json",
            "hdds.json", "gpus.json", "psus.json", "cpu-coolers.json", "casings.json",
            "casing-fans.json", "monitors.json", "keyboards.json", "mice.json", "ups.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Downloads catalogs one at a time to avoid bursts of simultaneous TLS handshakes. */
    public CompletableFuture<List<Part>> loadRemoteParts() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Part> parts = new ArrayList<>();
                for (String file : CATALOG_FILES) {
                    HttpResponse<String> response = fetchCatalog(file);
                    parts.addAll(parseParts(mapper.readTree(response.body()), file));
                }
                if (parts.isEmpty()) throw new IOException("GitHub catalogs contained no valid parts.");
                return List.copyOf(parts);
            } catch (Exception e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                throw new CompletionException(cause);
            }
        }, pool);
    }

    private HttpResponse<String> fetchCatalog(String file) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(REMOTE_BASE + file))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "PC-Hardware-Analyzer")
                .GET()
                .build();
        IOException lastNetworkError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("GitHub returned HTTP " + response.statusCode() + " for " + file);
                }
                return response;
            } catch (IOException e) {
                lastNetworkError = e;
                if (attempt == 2) break;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
        throw new IOException("Could not fetch " + file + " from GitHub after 2 attempts: "
                + (lastNetworkError == null ? "unknown network error" : lastNetworkError.getMessage()),
                lastNetworkError);
    }

    /** Reads bundled catalogs as a fallback when the app starts without internet access. */
    public CompletableFuture<List<Part>> loadLocalParts() {
        return CompletableFuture.supplyAsync(() -> {
            List<Part> result = new ArrayList<>();
            for (String file : CATALOG_FILES) {
                String resource = "/catalog/" + file;
                try (InputStream stream = CatalogService.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IOException("Missing project catalog: " + resource);
                    result.addAll(parseParts(mapper.readTree(stream), file));
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }
            return List.copyOf(result);
        }, pool);
    }

    private List<Part> parseParts(JsonNode root, String source) throws IOException {
        JsonNode components = root.path("components");
        if (!components.isArray()) throw new IOException("Catalog " + source + " has no components array.");
        List<Part> parts = new ArrayList<>();
        for (JsonNode item : components) {
            String category = item.path("type").asText("");
            String name = item.path("name").asText("");
            String specs = item.path("specs").asText("");
            double price = item.path("priceBdt").asDouble(-1);
            Map<String, String> attributes = new LinkedHashMap<>();
            item.fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if (value.isValueNode() && !value.isNull()) attributes.put(field.getKey(), value.asText());
            });
            if (!category.isBlank() && !name.isBlank() && price >= 0) {
                parts.add(new Part(category, name, specs, price, attributes));
            }
        }
        return parts;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
