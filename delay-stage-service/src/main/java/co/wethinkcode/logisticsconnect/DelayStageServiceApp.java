package co.wethinkcode.logisticsconnect;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import co.wethinkcode.logisticsconnect.mq.DelayStageEventPublisher;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class DelayStageServiceApp {

    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 8;

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7052);

       // Now stores the full DelayStage (including when it was last
        // set), not just a bare int - needed so GET can return a real
        // timestamp, not a fabricated one, for any hub that's actually
        // had a stage explicitly set.
        Map<String, DelayStage> stagesByHub = new ConcurrentHashMap<>();

        // connectOrNoOp(): if the broker is down, this logs a warning and
        // falls back to a no-op - the REST API (fully working since
        // Stage 2) must keep working with or without MQ available.
        DelayStageEventPublisher eventPublisher = DelayStageEventPublisher.connectOrNoOp();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();

            // A hub with no stage ever explicitly set still defaults to 0
            // (no delay) rather than 404 - same reasoning as Stage 2, now
            // just carrying a freshly-generated timestamp too, since there
            // was never a real "set" moment to report.
            DelayStage stage = stagesByHub.getOrDefault(
                hubId, new DelayStage(hubId, 0, Instant.now().toString()));
            ctx.json(stage);
        });

        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();
            DelayStageRequest request = ctx.bodyAsClass(DelayStageRequest.class);

            if (request.stage() == null || request.stage() < MIN_STAGE || request.stage() > MAX_STAGE) {
                ctx.status(HttpStatus.BAD_REQUEST)
                        .json(new ErrorResponse(
                                "stage must be an integer between " + MIN_STAGE + " and " + MAX_STAGE));
                return;
            }

            DelayStage updated = new DelayStage(hubId, request.stage(), Instant.now().toString());
            stagesByHub.put(hubId, updated);

            // Stage 3: broadcast this stage change to package-status-topic,
            // right after the state change is accepted and stored -
            // transit-service subscribes to this instead of polling this
            // endpoint directly.
            eventPublisher.publish(updated);

            ctx.json(updated);
        });
    }
}
