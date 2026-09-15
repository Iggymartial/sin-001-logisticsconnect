package co.wethinkcode.logisticsconnect.mq;

/**
 * Reads delay stage updates from package-status-topic.
 *
 * stageFor() always returns an int (never a failure type) - "no
 * message received yet for this hub" genuinely means "no delay has
 * ever been reported for it", the same default delay-stage-service
 * itself already applies. This is a deliberately different failure
 * philosophy from the Stage 2 REST call it replaces: there,
 * "unreachable" meant "I might have stale/wrong information", which
 * is worth failing loudly over. Here, "no event yet" means "nothing
 * has happened for this hub", which isn't stale - it's just true.
 */
public interface DelayStageEventSubscriber {

    int stageFor(String hubId);

    static DelayStageEventSubscriber connectOrNoOp() {
        try {
            return new JmsDelayStageEventSubscriber();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - this service will not receive delay stage events, so every hub "
                            + "will be treated as stage 0 until the broker is reachable: "
                            + e.getMessage());
            return hubId -> 0; // no-op: broker unavailable, already logged
        }
    }
}
