package co.wethinkcode.logisticsconnect;

import java.time.Instant;
import java.util.Optional;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class TransitServiceApp {

    // Domain rule for THIS EXERCISE, not a real logistics standard: a
    // baseline transit time of 24 hours for an undelayed hub, widening
    // both later AND less certain as the delay stage rises - a bigger
    // disruption genuinely means harder to predict, not just "later".
    // Verified this formula stays monotonically increasing and the
    // window never shrinks across the full 0-8 range before trusting it.
    private static final int BASE_HOURS = 24;
    private static final int EARLIEST_HOURS_PER_STAGE = 2;
    private static final int LATEST_HOURS_PER_STAGE = 5;

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7053);

        HubClient hubClient = new HubClient();
        DelayStageClient delayStageClient = new DelayStageClient();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            HubClient.Result hubResult = hubClient.fetchHub(hubId);

            // A sealed interface means these are the ONLY possible cases -
            // the compiler would flag it if a new Result type were ever
            // added here without being handled.
            if (hubResult instanceof HubClient.NotFound) {
                ctx.status(HttpStatus.NOT_FOUND)
                        .json(new ErrorResponse("no hub found with id '" + hubId + "'"));
                return;
            }
            if (hubResult instanceof HubClient.Unavailable unavailable) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("hub-service unavailable: " + unavailable.reason()));
                return;
            }

            HubRecord hub = ((HubClient.Found) hubResult).hub();

            Optional<Integer> stage = delayStageClient.fetchStage(hub.hubId());
            if (stage.isEmpty()) {
                // Deliberately fails loudly here rather than silently
                // assuming stage 0 ("no delay") when delay-stage-service
                // can't be reached - a falsely optimistic ETA is worse
                // than an honest 503, the same reasoning already applied
                // to the equivalent situation in the companion project.
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("delay-stage-service unavailable"));
                return;
            }

            int delayStage = stage.get();
            int earliestHours = BASE_HOURS + delayStage * EARLIEST_HOURS_PER_STAGE;
            int latestHours = BASE_HOURS + delayStage * LATEST_HOURS_PER_STAGE;

            ctx.json(new EtaResponse(
                    hub.hubId(),
                    hub.province(),
                    hub.sortingCenter(),
                    delayStage,
                    earliestHours,
                    latestHours,
                    Instant.now().toString()
            ));
        });
    }
}
