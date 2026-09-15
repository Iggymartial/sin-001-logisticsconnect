package co.wethinkcode.logisticsconnect.mq;

/**
 * Publishes stage-change events to package-status-topic.
 *
 * Same graceful-degradation pattern used throughout: if the broker is
 * unreachable, connectOrNoOp() logs a warning and falls back to a
 * no-op rather than stopping delay-stage-service's REST API (already
 * fully working since Stage 2) from working.
 */
public interface DelayStageEventPublisher extends AutoCloseable {

    void publish(Object event);

    static DelayStageEventPublisher connectOrNoOp() {
        try {
            return new JmsDelayStageEventPublisher();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - delay stage events will NOT be published, but REST endpoints "
                            + "will still work normally: " + e.getMessage());
            return new DelayStageEventPublisher() {
                @Override
                public void publish(Object event) {
                    // no-op: broker unavailable, already logged at startup
                }

                @Override
                public void close() {
                    // nothing to close
                }
            };
        }
    }
}
