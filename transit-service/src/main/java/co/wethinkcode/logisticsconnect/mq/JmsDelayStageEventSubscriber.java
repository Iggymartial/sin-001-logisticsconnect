package co.wethinkcode.logisticsconnect.mq;

import co.wethinkcode.logisticsconnect.DelayStage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-durable topic subscription, same as the companion HealthSafe
 * project's staffing event subscriber: if transit-service is offline
 * when a stage-change event is published, that specific update is
 * simply missed, not queued for later delivery. That's the accepted
 * limitation of a broadcast topic (guaranteed delivery is what a
 * queue is for) - not new to this project, the same caveat already
 * documented and accepted there.
 *
 * ConcurrentHashMap, not a plain HashMap: onMessage() runs on the JMS
 * listener thread, while stageFor() is read from Javalin's HTTP
 * threads.
 */
class JmsDelayStageEventSubscriber implements DelayStageEventSubscriber, MessageListener {

    private final Connection connection;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<String, Integer> latestStageByHub = new ConcurrentHashMap<>();

    JmsDelayStageEventSubscriber() throws JMSException {
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
            latestStageByHub.put(event.hubId().toUpperCase(), event.stage());

            System.out.println("[package-status-topic] received stage update for hub "
                    + event.hubId() + ": stage=" + event.stage());
        } catch (Exception e) {
            System.err.println("Failed to process delay stage event: " + e.getMessage());
        }
    }

    @Override
    public int stageFor(String hubId) {
        return latestStageByHub.getOrDefault(hubId.toUpperCase(), 0);
    }
}
