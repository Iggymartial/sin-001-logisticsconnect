package co.wethinkcode.logisticsconnect.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Publishes to a TOPIC (broadcast, fire-and-forget), not a queue -
 * session.createTopic(), not createQueue(). Uses javax.jms, not
 * jakarta.jms - confirmed this project's pom.xml depends on plain
 * `activemq-client` (not `activemq-client-jakarta`), same check
 * already done for the companion HealthSafe project.
 */
class JmsDelayStageEventPublisher implements DelayStageEventPublisher {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final ObjectMapper mapper = new ObjectMapper();

    JmsDelayStageEventPublisher() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        producer = session.createProducer(topic);
    }

    @Override
    public void publish(Object event) {
        try {
            String json = mapper.writeValueAsString(event);
            TextMessage message = session.createTextMessage(json);
            producer.send(message);
        } catch (Exception e) {
            System.err.println("Failed to publish delay stage event: " + e.getMessage());
        }
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
