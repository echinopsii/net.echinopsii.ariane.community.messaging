/**
 * Messaging - RabbitMQ Implementation
 * Request Executor implementation
 * Copyright (C) 8/25/14 echinopsii
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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import net.echinopsii.ariane.community.messaging.api.AppMsgWorker;
import net.echinopsii.ariane.community.messaging.api.MomMsgTranslator;
import net.echinopsii.ariane.community.messaging.api.MomRequestExecutor;
import net.echinopsii.ariane.community.messaging.common.MomAkkaAbsRequestExecutor;

import java.io.IOException;
import java.util.*;

public class RequestExecutor extends MomAkkaAbsRequestExecutor implements MomRequestExecutor<String, AppMsgWorker> {

    private static final String EXCHANGE_TYPE_DIRECT = "direct";

    private static final String FAF_EXCHANGE = "FAF";
    private static final String RPC_EXCHANGE = "RPC";

    private static boolean is_rpc_exchange_declared = false;
    private static boolean is_faf_exchange_declared = false;

    private Channel channel;
    private List<String> rpcEchangeBindedDestinations = new ArrayList<>();
    private List<String> fafEchangeBindedDestinations = new ArrayList<>();
    private Map<String, List<String>> sessionsRPCReplyQueues = new HashMap<>();
    private Map<String, Object> replyConsumers = new HashMap<>();

    public RequestExecutor(Client client) throws IOException {
        super(client);
        channel = client.getConnection().createChannel();
    }

    @Override
    public Map<String, Object> fireAndForget(Map<String, Object> request, String destination) {
        try {
            String groupID = super.getMomClient().getCurrentMsgGroup();
            if (groupID!=null) destination = groupID + "-" + destination;

            if (!is_faf_exchange_declared) {
                channel.exchangeDeclare(FAF_EXCHANGE, EXCHANGE_TYPE_DIRECT);
                is_faf_exchange_declared = true;
            }
            if (!fafEchangeBindedDestinations.contains(destination)) {
                channel.queueDeclare(destination, false, false, true, null);
                channel.queueBind(destination, FAF_EXCHANGE, destination);
                fafEchangeBindedDestinations.add(destination);
            }

            Message message = new MsgTranslator().encode(request);
            if (super.getMomClient().getClientID()!=null)
                request.put(MomMsgTranslator.MSG_APPLICATION_ID, super.getMomClient().getClientID());
            channel.basicPublish(FAF_EXCHANGE, destination, (com.rabbitmq.client.AMQP.BasicProperties) message.getProperties(), message.getBody());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return request;
    }

    @Override
    public Map<String, Object> RPC(Map<String, Object> request, String destination, String replySource, AppMsgWorker answerCB) {
        Map<String, Object> response = null;
        try {
            String groupID = super.getMomClient().getCurrentMsgGroup();
            if (groupID!=null) {
                destination = groupID + "-" + destination;
                if (replySource==null) replySource = destination + "-RET";
                if (this.sessionsRPCReplyQueues.get(groupID)==null)
                    this.sessionsRPCReplyQueues.put(groupID, new ArrayList<String>());
                this.sessionsRPCReplyQueues.get(groupID).add(replySource);
            }

            if (!is_rpc_exchange_declared) {
                channel.exchangeDeclare(RPC_EXCHANGE, EXCHANGE_TYPE_DIRECT);
                is_rpc_exchange_declared = true;
            }
            if (!rpcEchangeBindedDestinations.contains(destination)) {
                channel.queueDeclare(destination, false, false, true, null);
                channel.queueBind(destination, RPC_EXCHANGE, destination);
                rpcEchangeBindedDestinations.add(destination);
            }

            String replyQueueName;
            if (replySource==null) replyQueueName = channel.queueDeclare().getQueue();
            else replyQueueName = replySource;

            QueueingConsumer consumer;
            if (replyConsumers.get(replyQueueName)!=null) consumer = (QueueingConsumer) replyConsumers.get(replyQueueName);
            else {
                if (replySource!=null) channel.queueDeclare(replyQueueName, false, true, true, null);
                consumer = new QueueingConsumer(channel);
                channel.basicConsume(replyQueueName, true, consumer);
                replyConsumers.put(replyQueueName, consumer);
            }

            String corrId = UUID.randomUUID().toString();
            request.put(MsgTranslator.MSG_CORRELATION_ID, corrId);
            request.put(MsgTranslator.MSG_REPLY_TO, replyQueueName);
            if (super.getMomClient().getClientID()!=null)
                request.put(MomMsgTranslator.MSG_APPLICATION_ID, super.getMomClient().getClientID());

            Message message = new MsgTranslator().encode(request);
            channel.basicPublish(RPC_EXCHANGE, destination, (com.rabbitmq.client.AMQP.BasicProperties) message.getProperties(), message.getBody());

            while (true) {
                try {
                    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                    if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                        response = new MsgTranslator().decode(new Message().setEnvelope(delivery.getEnvelope()).
                                                                            setProperties(delivery.getProperties()).
                                                                            setBody(delivery.getBody()));
                        if (replySource==null) {
                            channel.queueDelete(replyQueueName);
                            replyConsumers.remove(replyQueueName);
                        }
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();

        }

        if (answerCB!=null)
            response = answerCB.apply(response);

        return response;
    }

    public void cleanGroupReqResources(String groupID) {
        if (this.sessionsRPCReplyQueues.get(groupID)!=null) {
            for (String queue : this.sessionsRPCReplyQueues.get(groupID)) {
                try {
                    channel.queueDelete(queue);
                    replyConsumers.remove(queue);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void stop() throws IOException {
        replyConsumers.clear();
        rpcEchangeBindedDestinations.clear();
        fafEchangeBindedDestinations.clear();
        channel.close();
    }
}