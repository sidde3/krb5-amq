package org.example;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import jakarta.jms.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.*;

public class ArtemisPooledProducerMS {

    private static final int MAX_POOL_SIZE = 5;
    private static final int THREAD_POOL_SIZE = 5;
    private static final String brokerURL = "(tcp://localhost:61616,tcp://localhost:51516)?ha=true";
    private static final String destinationName = "test-queue";
    private static volatile JmsPoolConnectionFactory amqConnectionPool = null;

    public static void main(String[] args) {

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);
        factory.setUser("master");
        factory.setPassword("master");

        factory.setReconnectAttempts(-1); 
        factory.setUseTopologyForLoadBalancing(true); // Load balancing among brokers
        factory.setRetryInterval(1000); // Retry every 100 seconds
        factory.setRetryIntervalMultiplier(1.0); // No exponential backoff

        // Initialize JmsPoolConnectionFactory and set the connection factory
        amqConnectionPool = new JmsPoolConnectionFactory();
        amqConnectionPool.setConnectionFactory(factory);
        amqConnectionPool.setMaxConnections(MAX_POOL_SIZE);

        // Create session and producer outside the loop
        // Get a connection from the pool
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (int i = 0; i < 20000; i++) {
            final int messageId = i;
            executorService.submit(() -> {
                try (Connection connection = amqConnectionPool.createConnection()) {
                    connection.start();

                    try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                        Queue queue = session.createQueue(destinationName);
                        TextMessage message = session.createTextMessage("Message " + messageId);
                        MessageProducer producer = session.createProducer(queue);
                        producer.send(message);
                        System.out.println("Sent: " + message.getText());
                    }

                    // Create session and producer
                } catch (JMSException ex) {
                    System.out
                            .println(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()) + ": ERROR on sending message " + messageId + ":" + ex.getMessage());
                    // throw new RuntimeException(ex);
                }
            });
        }
        executorService.shutdown();
    }

}
