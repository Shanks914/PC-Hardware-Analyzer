package com.sadat.pchardware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CatalogService implements AutoCloseable {
    private static final URI CATALOG_URI = URI.create(
            "https://raw.githubusercontent.com/Shanks914/pc-hardware-catalog-bd/main/catalog.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public CompletableFuture<List<Part>> fetchParts() {
        return CompletableFuture.supplyAsync(() -> {
            HttpRequest request = HttpRequest.newBuilder(CATALOG_URI)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "PC-Hardware-Analyzer")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new IOException("GitHub returned HTTP " + response.statusCode());
                }

                JsonNode components = mapper.readTree(response.body()).path("components");
                List<Part> result = new ArrayList<>();

                for (JsonNode item : components) {
                    String type = item.path("type").asText("");
                    String name = item.path("name").asText("");
                    double price = item.path("priceBdt").asDouble(-1);

                    if (!type.isBlank() && !name.isBlank() && price >= 0) {
                        result.add(new Part(
                                type,
                                name,
                                item.path("specs").asText(""),
                                price
                        ));
                    }
                }

                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, pool);
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
