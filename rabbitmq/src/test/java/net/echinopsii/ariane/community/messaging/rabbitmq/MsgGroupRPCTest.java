/**
 * [DEFINE YOUR PROJECT NAME/MODULE HERE]
 * [DEFINE YOUR PROJECT DESCRIPTION HERE] 
 * Copyright (C) 8/27/14 echinopsii
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.echinopsii.ariane.community.messaging.rabbitmq;

import net.echinopsii.ariane.community.messaging.api.AppMsgWorker;
import net.echinopsii.ariane.community.messaging.api.MomClient;
import net.echinopsii.ariane.community.messaging.api.MomException;
import net.echinopsii.ariane.community.messaging.api.MomMsgTranslator;
import net.echinopsii.ariane.community.messaging.common.MomClientFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static junit.framework.TestCase.assertTrue;

public class MsgGroupRPCTest {

    private static MomClient client = null;
    private static TestSessionRequestWorker sessionRequestWorker;
    private static TestSessionServiceWorker sessionServiceWorker;
    private static TestRequestWorker requestWorker;
    private static TestReplyWorker   replyWorker;


    @BeforeClass
    public static void testSetup() throws IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
        Properties props = new Properties();
        props.load(ClientTest.class.getResourceAsStream("/rabbitmq-test.properties"));
        /*
        Properties props = new Properties();
        props.put(MomClient.MOM_HOST, "localhost");
        props.put(MomClient.MOM_PORT, "5672");
        props.put(MomClient.MOM_USER, "ariane");
        props.put(MomClient.MOM_PSWD, "password");
        props.put(MomClient.RBQ_VHOST, "/ariane");
        */
        props.put("ariane.pgurl", "jmx://frontoffice-01.lab01.dev.dekatonshivr.echinopsii.net:9010");
        props.put("ariane.osi", "frontoffice-01.lab01.dev.dekatonshivr.echinopsii.net");
        props.put("ariane.otm", "FrontOffice OPS Team");
        props.put("ariane.dtm", "FrontOffice DEV Team");

        client = MomClientFactory.make(props.getProperty(MomClient.MOM_CLI));

        try {
            client.init(props);
            sessionRequestWorker = new TestSessionRequestWorker(client);
            sessionServiceWorker = new TestSessionServiceWorker(client);
            requestWorker = new TestRequestWorker();
            replyWorker   = new TestReplyWorker();

            client.getServiceFactory().requestService("SESSION_SUBJECT", sessionServiceWorker);
            client.getServiceFactory().msgGroupRequestService("RPC_SUBJECT", requestWorker);

        } catch (Exception e) {
            System.err.println("No local rabbit to test");
            client = null;
        }
    }

    @AfterClass
    public static void testCleanup() throws Exception {
        if (client!=null)
            client.close();
    }

    final static String sendedRequestBody = "Hello NATS!";
    final static String sendedReplyBody   = "Hello Client!";

    static class TestSessionServiceWorker implements AppMsgWorker {
        MomClient client ;

        TestSessionServiceWorker(MomClient client) {
            this.client = client;
        }

        @Override
        public Map<String, Object> apply(Map<String, Object> message) {
            Map<String, Object> reply = new HashMap<>(message);
            if (message.get("OP").equals("OPEN_SESSION")) {
                String sessionID = String.valueOf(UUID.randomUUID());
                client.openMsgGroupServices(sessionID);
                reply.put("SESSION_ID", sessionID);
            } else if (message.get("OP").equals("CLOSE_SESSION")) {
                String sessionID = (String) message.get("SESSION_ID");
                client.closeMsgGroupServices(sessionID);
            }
            return reply;
        }
    }

    static class TestSessionRequestWorker implements AppMsgWorker {
        MomClient client ;

        TestSessionRequestWorker(MomClient client) {
            this.client = client;
        }

        @Override
        public Map<String, Object> apply(Map<String, Object> message) {
            return message;
        }
    }

    static class TestRequestWorker implements AppMsgWorker {
        boolean OK = false;

        @Override
        public Map<String, Object> apply(Map<String, Object> message) {
            String recvMsgBody = new String((byte [])message.get(MomMsgTranslator.MSG_BODY));
            if (recvMsgBody.equals(sendedRequestBody))
                OK = !OK;

            Map<String, Object> reply = new HashMap<String, Object>();
            reply.put(MomMsgTranslator.MSG_BODY, sendedReplyBody);

            return reply;
        }

        public boolean isOK() {
            return OK;
        }
    }

    static class TestReplyWorker implements AppMsgWorker {
        boolean OK = false;

        @Override
        public Map<String, Object> apply(Map<String, Object> message) {
            String recvMsgBody = new String((byte [])message.get(MomMsgTranslator.MSG_BODY));
            if (recvMsgBody.equals(sendedReplyBody))
                OK = !OK;
            return message;
        }

        public boolean isOK() {
            return OK;
        }
    }

    private void openSession() throws TimeoutException, IOException, MomException {
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("OP", "OPEN_SESSION");
        String sessionID = (String) client.createRequestExecutor().RPC(request, "SESSION_SUBJECT", sessionRequestWorker).get("SESSION_ID");
        client.openMsgGroupRequest(sessionID);
    }

    private void closeSession() throws TimeoutException, IOException, MomException {
        String sessionID = client.getCurrentMsgGroup();
        client.closeMsgGroupRequest(sessionID);
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("OP", "CLOSE_SESSION");
        request.put("SESSION_ID", sessionID);
        client.createRequestExecutor().RPC(request, "SESSION_SUBJECT", sessionRequestWorker);
    }

    @Test
    public void testGroupRPC() throws InterruptedException, TimeoutException, IOException, MomException {
        if (client!=null) {
            openSession();

            Map<String, Object> request = new HashMap<String, Object>();
            request.put("OP", "TEST");
            request.put("ARGS_BOOL", true);
            request.put("ARGS_LONG", 0);
            request.put("ARGS_STRING", "toto");
            request.put(MomMsgTranslator.MSG_BODY, sendedRequestBody);
            client.createRequestExecutor().RPC(request, "RPC_SUBJECT", replyWorker);

            assertTrue(requestWorker.isOK());
            assertTrue(replyWorker.isOK());

            closeSession();

            request.clear();
            request.put("OP", "TEST");
            request.put("ARGS_BOOL", true);
            request.put("ARGS_LONG", 0);
            request.put("ARGS_STRING", "toto");
            request.put(MomMsgTranslator.MSG_BODY, sendedRequestBody);
            client.createRequestExecutor().RPC(request, "RPC_SUBJECT", replyWorker);

            assertTrue(!requestWorker.isOK());
            assertTrue(!replyWorker.isOK());
        }
    }

}