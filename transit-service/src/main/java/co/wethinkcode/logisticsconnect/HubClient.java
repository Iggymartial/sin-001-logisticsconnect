package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to hub-service to look up hub/sorting-center details for an
 * ETA calculation.
 *
 * Uses a sealed interface (Java 17) to represent the three genuinely
 * different outcomes of this call, rather than a nullable return or an
 * exception used for control flow - the same pattern used for the
 * equivalent call in the companion HealthSafe project. The compiler
 * forces every caller to handle all three cases; there's no way to
 * accidentally forget one.
 */
public class HubClient {

    private static final String HUB_SERVICE_BASE = "http://localhost:7051";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public sealed interface Result permits Found, NotFound, Unavailable {}

    public record Found(HubRecord hub) implements Result {}

    public record NotFound() implements Result {}

    public record Unavailable(String reason) implements Result {}

    public Result fetchHub(String hubId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HUB_SERVICE_BASE + "/hubs/" + hubId))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new Found(mapper.readValue(response.body(), HubRecord.class));
            }
            if (response.statusCode() == 404) {
                return new NotFound();
            }
            return new Unavailable("hub-service responded with status " + response.statusCode());
        } catch (IOException | InterruptedException e) {
            return new Unavailable("could not reach hub-service: " + e.getMessage());
        }
    }
}
