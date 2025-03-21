package org.example;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import jakarta.jms.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * NOTE: This is an example code showing the use of connection pooling for
 * consumers. This is NOT a production ready code.
 */
public class ArtemisPooledConsumer {

    private static final int MAX_POOL_SIZE = 5;
    private static final int THREAD_POOL_SIZE = 5;
    private static final String brokerURL = "(tcp://artemis.demo.artemis.com:61616,tcp://artemis.demo.artemis.com:61617)?sslEnabled=true&trustStorePath=C:/Users/sidde/wildfly.jks&trustStorePassword=jboss@123&ha=trueamp&reconnectAttempts=10&failoverOnServerShutdown=true&consumerWindowsSize=0";
    private static final String destinationName = "test-queue";
    private static volatile JmsPoolConnectionFactory amqConnectionPool = null;
    private static final Set<String> receivedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> expectedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> duplicateMessageIds = Collections.synchronizedSet(new HashSet<>());
    private static final CountDownLatch latch = new CountDownLatch(THREAD_POOL_SIZE); // Track completion of all threads


    public static void main(String[] args) {

        System.setProperty("org.apache.activemq.ssl.trustStore","C:/Users/sidde/wildfly.jks");
        System.setProperty("org.apache.activemq.ssl.trustStorePassword","jboss@123");

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);
        factory.setUser("amq-broker");
        factory.setPassword("secret");
        factory.setCallTimeout(5000);

        factory.setReconnectAttempts(1);
        factory.setUseTopologyForLoadBalancing(true);
        factory.setRetryInterval(100);
        factory.setRetryIntervalMultiplier(1.0);

        // Initialize JmsPoolConnectionFactory
        amqConnectionPool = new JmsPoolConnectionFactory();
        amqConnectionPool.setConnectionFactory(factory);
        amqConnectionPool.setMaxConnections(MAX_POOL_SIZE);

        // Set to store all expected message IDs
        for (int i = 0; i <= 39999; i++) {
            expectedMessageIds.add(i + "");
        }

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            executorService.submit(() -> {
                try (Connection connection = factory.createConnection()) {
                    connection.start();
                    while(true){
                        Session session = null;
                    try  {
                         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        Queue queue = session.createQueue(destinationName);
                        MessageConsumer consumer = session.createConsumer(queue);

                        //while (true) {
                            Message message = consumer.receive(20000);
                            if (message == null) {
                                System.out.println(Thread.currentThread().getName() + " finished consuming messages..........");
                                break; // Exit if no more messages
                            }

                            if (message instanceof TextMessage) {
                                TextMessage textMessage = (TextMessage) message;
                                System.out.println("Received: " + textMessage.getText());
                                expectedMessageIds.remove(textMessage.getText());

                                if (!receivedMessageIds.contains(textMessage.getText())) {
                                    receivedMessageIds.add(textMessage.getText());
                                } else {
                                    duplicateMessageIds.add(textMessage.getText());
                                }
                            }

                    }catch(Exception ignored){
                        ignored.printStackTrace();
                        }finally {
                        assert session != null;
                        session.close();
                    }
                    }
                } catch (JMSException ex) {
                    System.out.println(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()) +
                            ": ERROR while receiving message: " + ex.getMessage());
                    // TODO handle retries as required..
                } finally {
                    latch.countDown(); // Signal that this thread has completed execution
                }
            });
        }

          // Wait for all threads to complete execution
          try {
            latch.await(); // Blocks until all threads call `latch.countDown()`
        } catch (InterruptedException e) {
            System.out.println("Thread was interrupted while waiting for consumers to finish.");
        }

        // Print results after all threads have completed
        System.out.println("All threads have completed execution.");
        //System.out.println("Missing message IDs: " + expectedMessageIds);
        //System.out.println("Duplicate Message IDs: " + duplicateMessageIds);
        System.out.println("Size of received messages: " + receivedMessageIds.size());

    }
}
