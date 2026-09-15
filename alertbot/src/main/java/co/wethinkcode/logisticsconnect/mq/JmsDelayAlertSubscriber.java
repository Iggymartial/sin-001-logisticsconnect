package co.wethinkcode.logisticsconnect.mq;

import co.wethinkcode.logisticsconnect.DelayAlert;
import co.wethinkcode.logisticsconnect.DelayStage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subscribes to package-status-topic and simulates posting an alert
 * when a hub's delay stage CROSSES the threshold - not merely "is at
 * or above it". This distinction matters: without tracking each hub's
 * previous stage, a hub sitting at stage 7 for ten consecutive
 * messages would trigger ten identical alerts, which is noisy and
 * doesn't match what the README actually asks for ("crosses a
 * threshold"). Verified this crossing logic in a quick simulation
 * before writing it here: it correctly avoids re-alerting while a hub
 * stays high, correctly re-alerts if a hub drops and crosses again
 * later, and correctly treats a brand-new hub's first high reading as
 * a genuine crossing (no prior record is treated as 0, consistent
 * with how the rest of this project already treats unknown state).
 *
 * ALERT_THRESHOLD = 5 is a deliberate domain choice for this
 * exercise, not given anywhere in the brief: stages 0-4 are treated as
 * minor/manageable delays, 5-8 as significant enough to be worth a
 * proactive public notification.
 */
class JmsDelayAlertSubscriber implements DelayAlertSubscriber, MessageListener {

    private static final int ALERT_THRESHOLD = 5;

    private final Connection connection;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<String, Integer> previousStageByHub = new ConcurrentHashMap<>();
    private final List<DelayAlert> alerts = new CopyOnWriteArrayList<>();

    JmsDelayAlertSubscriber() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageConsumer consumer = session.createConsumer(topic);
        consumer.setMessageListener(this);
    }

    @Override
    public void onMessage(Message message) {
        try {
            String json = ((TextMessage) message).getText();
            DelayStage event = mapper.readValue(json, DelayStage.class);

            String hubId = event.hubId().toUpperCase();
            int newStage = event.stage();

            int previousStage = previousStageByHub.getOrDefault(hubId, 0);
            previousStageByHub.put(hubId, newStage);

            boolean crossedThreshold = previousStage < ALERT_THRESHOLD && newStage >= ALERT_THRESHOLD;

            if (crossedThreshold) {
                String postText = "Significant delay at hub " + hubId + " (stage " + newStage
                        + ") - service disruption expected, please allow extra time for deliveries.";

                DelayAlert alert = new DelayAlert(hubId, newStage, postText, Instant.now().toString());
                alerts.add(alert);

                System.out.println("[package-status-topic] ALERT posted (simulated): " + postText);
            }
        } catch (Exception e) {
            System.err.println("Failed to process delay stage event: " + e.getMessage());
        }
    }

    @Override
    public List<DelayAlert> allAlerts() {
        return List.copyOf(alerts);
    }
}
