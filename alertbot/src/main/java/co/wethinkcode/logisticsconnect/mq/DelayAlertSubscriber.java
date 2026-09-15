package co.wethinkcode.logisticsconnect.mq;

import co.wethinkcode.logisticsconnect.DelayAlert;

import java.util.List;

/**
 * Subscribes to package-status-topic (the SAME topic delay-stage-service
 * already publishes to in Stage 3 - alertbot is a second consumer on
 * an existing topic, not a new producer/queue pair like the
 * equivalent stretch stage in the companion HealthSafe project).
 *
 * Same graceful-degradation pattern used throughout: if the broker is
 * unreachable, connectOrNoOp() logs a warning and falls back to a
 * no-op - /health and /alerts must keep working (returning an empty
 * list) with or without MQ available.
 */
public interface DelayAlertSubscriber {

    List<DelayAlert> allAlerts();

    static DelayAlertSubscriber connectOrNoOp() {
        try {
            return new JmsDelayAlertSubscriber();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - alertbot will not receive stage updates, but its REST "
                            + "endpoints will still work normally: " + e.getMessage());
            return List::of; // no-op: broker unavailable, already logged
        }
    }
}
