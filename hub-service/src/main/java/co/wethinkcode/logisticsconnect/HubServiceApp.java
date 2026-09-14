package co.wethinkcode.logisticsconnect;

import java.util.List;
import java.util.Optional;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class HubServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7051);
        IngestionClient ingestionClient = new IngestionClient();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> {
            try {
                ctx.json(ingestionClient.fetchHubs());
            } catch (IngestionClient.IngestionUnavailableException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ingestion-service unavailable: " + e.getMessage()));
            }
        });

        app.get("/hubs/{id}", ctx -> {
            String hubId = ctx.pathParam("id").toUpperCase();

            try {
                List<HubRecord> hubs = ingestionClient.fetchHubs();
                Optional<HubRecord> match = hubs.stream()
                        .filter(h -> h.hubId().equals(hubId))
                        .findFirst();

                if (match.isPresent()) {
                    ctx.json(match.get());
                } else {
                    ctx.status(HttpStatus.NOT_FOUND)
                            .json(new ErrorResponse("no hub found with id '" + hubId + "'"));
                }
            } catch (IngestionClient.IngestionUnavailableException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ingestion-service unavailable: " + e.getMessage()));
            }
        });
    }
}
