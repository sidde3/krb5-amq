package org.example;

import jakarta.jms.*;
import org.apache.qpid.jms.JmsConnectionFactory;

import javax.naming.InitialContext;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Set;

public class AmqpQueueBrowser {

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
            Subject.doAs(subject, (PrivilegedAction<Object>) () -> {
                try {
                    ConnectionFactory connectionFactory = new JmsConnectionFactory(connectionUri);
                    Connection connection = connectionFactory.createConnection();
                    connection.start();
                    System.err.println("Connection established with Kerberos.");
                    try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                        InitialContext initialContext = new InitialContext();
                        Queue queue = session.createQueue(destinationName);
                        QueueBrowser browser = session.createBrowser(queue);
                        Enumeration messageEnum = browser.getEnumeration();
                        while (messageEnum.hasMoreElements()) {
                            TextMessage message = (TextMessage) messageEnum.nextElement();
                            System.err.println(">>>>> Browsing: " + message.getText());
                        }
                        browser.close();
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
