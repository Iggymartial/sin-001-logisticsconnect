package co.wethinkcode.logisticsconnect;

import java.time.Instant;

import co.wethinkcode.logisticsconnect.mq.DelayStageEventSubscriber;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class TransitServiceApp {

    // Domain rule for THIS EXERCISE, not a real logistics standard: a
    // baseline transit time of 24 hours for an undelayed hub, widening
    // both later AND less certain as the delay stage rises. Verified
    // this formula in Stage 2 across the full 0-8 range before trusting
    // it - unchanged here, only where the stage value comes from changes.
    private static final int BASE_HOURS = 24;
    private static final int EARLIEST_HOURS_PER_STAGE = 2;
    private static final int LATEST_HOURS_PER_STAGE = 5;

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7053);

        HubClient hubClient = new HubClient();

        // Stage 3: replaces the direct REST call to delay-stage-service
        // from Stage 2. stageFor() always returns an int, defaulting
        // unknown hubs to 0 - "no event received yet" genuinely means
        // "no delay has ever been reported", the same default
        // delay-stage-service itself uses. See DECISIONS.md for why this
        // is a deliberately different failure philosophy from the
        // synchronous call it replaces.
        DelayStageEventSubscriber delayStageEvents = DelayStageEventSubscriber.connectOrNoOp();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            HubClient.Result hubResult = hubClient.fetchHub(hubId);

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

            int delayStage = delayStageEvents.stageFor(hub.hubId());
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
