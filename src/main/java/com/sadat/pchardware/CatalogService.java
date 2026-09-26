package com.sadat.pchardware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the app-owned component catalog asynchronously from packaged JSON resources. */
public class CatalogService implements AutoCloseable {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public CompletableFuture<List<Part>> loadLocalParts() {
        return CompletableFuture.supplyAsync(() -> {
            List<Part> result = new ArrayList<>();
            readFile("/catalog/processors.json", result);
            readFile("/catalog/motherboards.json", result);
            readFile("/catalog/desktop-ram.json", result);
            readFile("/catalog/ssds.json", result);
            readFile("/catalog/hdds.json", result);
            readFile("/catalog/gpus.json", result);
            readFile("/catalog/psus.json", result);
            return result;
        }, pool);
    }

    private void readFile(String resource, List<Part> result) {
        try (InputStream stream = CatalogService.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing project catalog: " + resource);
            JsonNode components = mapper.readTree(stream).path("components");
            for (JsonNode item : components) {
                String category = item.path("type").asText("");
                String name = item.path("name").asText("");
                String specs = item.path("specs").asText("");
                double price = item.path("priceBdt").asDouble(-1);
                Map<String, String> attributes = new LinkedHashMap<>();
                item.fields().forEachRemaining(field -> {
                    JsonNode value = field.getValue();
                    if (value.isValueNode()) attributes.put(field.getKey(), value.asText());
                });
                if (!category.isBlank() && !name.isBlank() && price >= 0) {
                    result.add(new Part(category, name, specs, price, attributes));
                }
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
