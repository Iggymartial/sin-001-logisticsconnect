package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Talks to ingestion-service over plain synchronous HTTP.
 *
 * Deliberately NOT cached: fetches fresh on every request, so this
 * service stays independently runnable even if ingestion-service
 * restarts with corrected data - the same reasoning applied in the
 * companion HealthSafe project's ward-service.
 */
public class IngestionClient {

    private static final String INGESTION_URL = "http://localhost:7050/hubs";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<HubRecord> fetchHubs() throws IngestionUnavailableException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IngestionUnavailableException(
                        "ingestion-service responded with status " + response.statusCode());
            }
            return mapper.readValue(
                    response.body(),
                    mapper.getTypeFactory().constructCollectionType(List.class, HubRecord.class)
            );
        } catch (IOException | InterruptedException e) {
            throw new IngestionUnavailableException(
                    "could not reach ingestion-service at " + INGESTION_URL, e);
        }
    }

    public static class IngestionUnavailableException extends Exception {
        public IngestionUnavailableException(String message) {
            super(message);
        }

        public IngestionUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
