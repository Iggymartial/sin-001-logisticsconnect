package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.DelayAlertSubscriber;
import io.javalin.Javalin;

public class AlertBotApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7054);

        // connectOrNoOp(): if the broker is down, this logs a warning and
        // falls back to a no-op - /health and /alerts must keep working
        // (returning an empty list) with or without MQ available.
        DelayAlertSubscriber alertSubscriber = DelayAlertSubscriber.connectOrNoOp();

        app.get("/health", ctx -> ctx.result("OK"));

        // Every simulated alert posted so far - makes the threshold-crossing
        // logic independently verifiable with a plain curl call, the same
        // way every other async flow in this project has been checked.
        app.get("/alerts", ctx -> ctx.json(alertSubscriber.allAlerts()));
    }
}
