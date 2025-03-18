package org.example;

import jakarta.jms.*;
import org.apache.qpid.jms.JmsConnectionFactory;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedAction;
import java.util.Set;

public class AmqpKerberosClient {

    private static final int MAX_POOL_SIZE = 5;
    private static final int THREAD_POOL_SIZE = 5;
    private static final String connectionUri = "amqp://artemis.demo.artemis.com:61616?amqp.saslMechanisms=GSSAPI&amqp.traceFrames=true&saslInitialResponseCallbackHandler=null";
    private static final String destinationName = "QueueOne";

    static {
        System.setProperty("java.security.krb5.conf", "C:\\Users\\sidde\\krb5.conf");
        System.setProperty("java.security.auth.login.config", "C:\\Users\\sidde\\login.config");
        System.setProperty("sun.security.krb5.debug", "true");
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
                    /*HashMap<String, String> properties = new HashMap<>();
                    properties.put("saslMechanism", "GSSAPI");
                    properties.put("saslInitialResponseCallbackHandler", "null");
                    */

                    ConnectionFactory connectionFactory = new JmsConnectionFactory(connectionUri);


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

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            });

        } catch (LoginException e) {
            System.err.println("Kerberos login failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
