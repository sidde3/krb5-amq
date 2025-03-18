package org.example;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import jakarta.jms.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ArtemisPooledProducerTest {

    private static final int MAX_POOL_SIZE = 5;
    private static final int THREAD_POOL_SIZE = 5;
    private static final String brokerURL = "(tcp://localhost:61616,tcp://localhost:61617)?ha=true";
    private static final String DESTINATION_NAME = "new-queue";
    private static volatile JmsPoolConnectionFactory amqConnectionPool = null;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 6000;

    public static void main(String[] args) {

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);
        factory.setUser("master");
        factory.setPassword("master");
        factory.setCallTimeout(5000); 

        factory.setReconnectAttempts(1); 
        factory.setUseTopologyForLoadBalancing(true); 
        factory.setRetryInterval(100); 
        factory.setRetryIntervalMultiplier(1.0); 

        // Initialize JmsPoolConnectionFactory and set the connection factory
        amqConnectionPool = new JmsPoolConnectionFactory();
        amqConnectionPool.setConnectionFactory(factory);
        amqConnectionPool.setMaxConnections(MAX_POOL_SIZE);

        // Create session and producer outside the loop
        // Get a connection from the pool
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (int i = 0; i < 40000; i++) {
            final int messageId = i;
            executorService.submit(() -> sendMessageWithRetry(messageId + ""));
        }

        executorService.shutdown();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Error: InterruptedException when shutting down executor service.");
        }
    }

    /**
     * Attempts to send a message with a configurable number of retries.
     */
    private static void sendMessageWithRetry(String messageId) {
        int attempt = 0;
        boolean sent = false;
        while (!sent && attempt < MAX_RETRIES) {
            attempt++;

            try (Connection connection = amqConnectionPool.createConnection()) {
                connection.start();

                // Use a transacted session to allow explicit commit/rollback.
                try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                    Queue queue = session.createQueue(DESTINATION_NAME);
                    MessageProducer producer = session.createProducer(queue);
                    producer.setDeliveryMode(DeliveryMode.PERSISTENT);

                    // Create and send the message.
                    TextMessage message = session.createTextMessage(messageId);
                    message.setStringProperty("ClientMessageId",messageId);
                    // message.setLongProperty("MessageID", Long.valueOf(messageId));
                    // message.setJMSMessageID("ID:" + messageId);

                    message.setStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID.toString(), messageId);

                    producer.send(message);

                    System.out.println("Sent: " + message.getText() + " on attempt " + attempt);
                    sent = true;
                }
            }

            catch (JMSException ex) {
                System.out.println(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
                        + " - ERROR sending message " + messageId + " on attempt " + attempt + ": "
                        + ex.getMessage());

                // Wait a short delay before retrying.
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                    System.out
                            .println("Waiting for " + RETRY_DELAY_MS + " ms before resending message " + messageId);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.println("Error: InterruptedException when sleeping.");
                    break;
                }
            }
        }
        if (!sent) {
            System.out.println("Failed to send message " + messageId + " after " + MAX_RETRIES + " attempts.");
            // Optionally, persist or log the message for later reprocessing.
        }
    }
}
