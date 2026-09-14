package co.wethinkcode.logisticsconnect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class DelayStageServiceApp {

    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 8;

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7052);

        // ConcurrentHashMap, not a plain HashMap: Javalin handles each
        // request on its own thread, so reads and writes to per-hub
        // stages need to be genuinely thread-safe, not just usually fine.
        Map<String, Integer> stagesByHub = new ConcurrentHashMap<>();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();

            // A hub with no stage ever explicitly set defaults to 0 (no
            // delay), rather than 404. Reasoning: transit-service needs
            // SOME stage value for any hub it's asked to calculate an ETA
            // for, and "no delay reported" is a more useful default than
            // forcing every hub to be explicitly initialised before an
            // ETA can ever be computed.
            int stage = stagesByHub.getOrDefault(hubId, 0);
            ctx.json(new DelayStage(hubId, stage));
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

            stagesByHub.put(hubId, request.stage());

            // MQ TODO (Stage 3): publish this stage change to
            // package-status-topic here, right after the state change is
            // accepted and stored.

            ctx.json(new DelayStage(hubId, request.stage()));
        });
    }
}
