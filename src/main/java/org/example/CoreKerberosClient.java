package org.example;

import jakarta.jms.*;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedAction;
import java.util.Hashtable;
import java.util.Set;

public class CoreKerberosClient {

    private static final int MAX_POOL_SIZE = 5;
    private static final int THREAD_POOL_SIZE = 5;
    private static final String brokerURL = "tcp://artemis.demo.artemis.com:61616";
    private static final String destinationName = "test-queue";
    private static volatile JmsPoolConnectionFactory amqConnectionPool = null;

    static {
        System.setProperty("java.security.krb5.conf", "C:\\Users\\sidde\\krb5.conf");
        System.setProperty("java.security.auth.login.config", "C:\\Users\\sidde\\login.config");
    }

    public static void main(String[] args) {
        try {
            LoginContext loginContext = new LoginContext("amqp-jms-client", new javax.security.auth.callback.CallbackHandler() {
                @Override
                public void handle(javax.security.auth.callback.Callback[] callbacks) throws java.io.IOException, javax.security.auth.callback.UnsupportedCallbackException {
                    // No callbacks are expected for Kerberos.
                    

                }
            });

            loginContext.login();
            Subject subject = loginContext.getSubject();
            Set<KerberosPrincipal> principals = subject.getPrincipals(KerberosPrincipal.class);
            if (!principals.isEmpty()) {
                KerberosPrincipal principal = principals.iterator().next();
                System.out.println("Kerberos Principal: " + principal.getName());
            } else {
                System.out.println("Kerberos Principal not found.");
            }

            Set<KerberosTicket> tickets = subject.getPrivateCredentials(KerberosTicket.class);

            if (!tickets.isEmpty()) {
                for (KerberosTicket ticket : tickets) {
                    if (ticket.getClient().equals(ticket.getServer())) {
                        System.out.println("TGT: " + ticket); //Ticket Granting Ticket
                    } else {
                        System.out.println("Service Ticket: " + ticket); //Service Ticket
                    }
                }
            } else {
                System.out.println("No Kerberos tickets found.");
            }

            Subject.doAs(subject, (PrivilegedAction<Object>) () -> {
                try {
                    Hashtable<String, String> env = new Hashtable<>();
                    env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
                    env.put(Context.PROVIDER_URL, brokerURL); // Replace with your broker URL.

                    InitialContext initialContext = new InitialContext(env);
                    ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup("ConnectionFactory");

                    subject.getPrincipals().forEach(System.err::println);

                    Connection connection = connectionFactory.createConnection();
                    connection.start();

                    System.err.println("Connection established with Kerberos.");

                    try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                        Queue queue = session.createQueue(destinationName);
                        TextMessage message = session.createTextMessage("Hello Krb5");
                        MessageProducer producer = session.createProducer(queue);
                        producer.send(message);
                        System.out.println("Sent: " + message.getText());
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    connection.close();
                    initialContext.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            });

        } catch (LoginException  e) {
            System.err.println("Kerberos login failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
