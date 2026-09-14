package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Talks to delay-stage-service to read the current delay stage for a
 * hub. No NotFound case needed here - delay-stage-service always
 * returns a value (defaulting unset hubs to 0), so the only failure
 * mode is genuine unavailability.
 */
public class DelayStageClient {

    private static final String DELAY_STAGE_BASE = "http://localhost:7052";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<Integer> fetchStage(String hubId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DELAY_STAGE_BASE + "/delay-stage/" + hubId))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            DelayStage delayStage = mapper.readValue(response.body(), DelayStage.class);
            return Optional.of(delayStage.stage());
        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }
}
