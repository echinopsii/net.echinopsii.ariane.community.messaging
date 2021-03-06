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
import net.echinopsii.ariane.community.messaging.api.MomException;
import net.echinopsii.ariane.community.messaging.api.MomMsgTranslator;
import net.echinopsii.ariane.community.messaging.api.MomRequestExecutor;
import net.echinopsii.ariane.community.messaging.common.MomAkkaAbsRequestExecutor;
import net.echinopsii.ariane.community.messaging.common.MomLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * RequestExecutor class extending {@link net.echinopsii.ariane.community.messaging.common.MomAkkaAbsRequestExecutor} abstract class
 * and implements {@link net.echinopsii.ariane.community.messaging.api.MomRequestExecutor} for RabbitMQ MoM.
 */
public class RequestExecutor extends MomAkkaAbsRequestExecutor implements MomRequestExecutor<String, AppMsgWorker> {
    private static final Logger log = MomLoggerFactory.getLogger(RequestExecutor.class);

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
    private HashMap<String, Boolean> destinationTrace = new HashMap<>();

    /**
     * @param client an initialized RabbitMQ Client
     * @throws IOException in case of RabbitMQ channel creation error.
     */
    public RequestExecutor(Client client) throws IOException {
        super(client);
        channel = client.getConnection().createChannel();
    }

    /**
     * Fire And Forget : send request to target destination
     * @param request the request message
     * @param destination the target destination queue
     * @return the sent request
     */
    @Override
    public Map<String, Object> FAF(Map<String, Object> request, String destination) {
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

    /**
     * Remote procedure call : send request to target destination and wait answer to be treated by the answer worker.
     * @param request the request message
     * @param destination the target destination queue
     * @param answerSource the source to get the answer from
     * @param answerWorker the worker object to treat the answer (can be null)
     * @return the answer (treated or not by answer worker)
     * @throws TimeoutException if no answers has been receiver after timeout * retry as configured in RabbitMQ Client to this RequestExecutor
     *         IOException while publishing request or receiving answer,
     *         MomException if response is null when apply to answerWorker
     */
    @Override
    public Map<String, Object> RPC(Map<String, Object> request, String destination, String answerSource, AppMsgWorker answerWorker) throws TimeoutException, IOException, MomException {
        Map<String, Object> response = null;
        QueueingConsumer.Delivery delivery = null;
        long beginWaitingAnswer = 0;
        String groupID = super.getMomClient().getCurrentMsgGroup();
        if (groupID!=null && !destination.contains(groupID)) {
            destination = groupID + "-" + destination;
            if (answerSource ==null) answerSource = destination + "-RET";
            if (this.sessionsRPCReplyQueues.get(groupID)==null)
                this.sessionsRPCReplyQueues.put(groupID, new ArrayList<String>());
            this.sessionsRPCReplyQueues.get(groupID).add(answerSource);
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
        if (destinationTrace.get(destination)==null) destinationTrace.put(destination, false);

        String replyQueueName;
        if (answerSource ==null) replyQueueName = channel.queueDeclare().getQueue();
        else replyQueueName = answerSource;

        QueueingConsumer consumer;
        if (replyConsumers.get(replyQueueName)!=null) consumer = (QueueingConsumer) replyConsumers.get(replyQueueName);
        else {
            if (answerSource !=null) channel.queueDeclare(replyQueueName, false, true, true, null);
            consumer = new QueueingConsumer(channel);
            channel.basicConsume(replyQueueName, true, consumer);
            replyConsumers.put(replyQueueName, consumer);
        }

        String corrId;
        if (request.get(MsgTranslator.MSG_CORRELATION_ID)==null) {
            synchronized (UUID.class) {
                corrId = UUID.randomUUID().toString();
            }
            request.put(MsgTranslator.MSG_CORRELATION_ID, corrId);
        } else corrId = (String) request.get(MsgTranslator.MSG_CORRELATION_ID);
        request.put(MsgTranslator.MSG_REPLY_TO, replyQueueName);

        if (super.getMomClient().getClientID()!=null)
            request.put(MomMsgTranslator.MSG_APPLICATION_ID, super.getMomClient().getClientID());

        if (destinationTrace.get(destination)) request.put(MomMsgTranslator.MSG_TRACE, true);
        else request.remove(MomMsgTranslator.MSG_TRACE);

        if (destinationTrace.get(destination)) log.info("send request " + corrId);
        Message message = new MsgTranslator().encode(request);
        channel.basicPublish(RPC_EXCHANGE, destination, (com.rabbitmq.client.AMQP.BasicProperties) message.getProperties(), message.getBody());

        long rpcTimeout = super.getMomClient().getRPCTimout() * 1000;
        beginWaitingAnswer = System.nanoTime();
        while (delivery==null && rpcTimeout>=0) {
            try {
                delivery = consumer.nextDelivery(rpcTimeout);
                if (delivery!=null) {
                    if (!delivery.getProperties().getCorrelationId().equals(corrId))
                        log.warn("Response discarded ( " + delivery.getProperties().getCorrelationId() + " ) ...");
                }
            } catch (InterruptedException e) {
                log.debug("Thread interrupted while waiting for RPC answer...");
            } finally {
                if (super.getMomClient().getRPCTimout()>0)
                    rpcTimeout = (super.getMomClient().getRPCTimout()*1000000000 - (System.nanoTime()-beginWaitingAnswer))/1000000;
                else rpcTimeout = 0;
                if (destinationTrace.get(destination)) log.info("rpcTimeout left: " + rpcTimeout);
            }
        }

        if (answerSource == null) {
            channel.queueDelete(replyQueueName);
            replyConsumers.remove(replyQueueName);
        }

        if (delivery!=null) {
            long endWaitingAnswer = System.nanoTime();
            long rpcTime = endWaitingAnswer - beginWaitingAnswer;
            log.debug("RPC time : " + rpcTime);
            if (super.getMomClient().getRPCTimout()>0 && beginWaitingAnswer>0 && rpcTime > super.getMomClient().getRPCTimout()*1000000000 * 3 / 5) {
                log.debug("Slow RPC time (" + rpcTime / 1000000000 + ") on request to queue " + destination);
            } else  destinationTrace.put(destination, false);
            response = new MsgTranslator().decode(new Message().setEnvelope(delivery.getEnvelope()).
                    setProperties(delivery.getProperties()).
                    setBody(delivery.getBody()));
        } else {
            if (request.containsKey(MomMsgTranslator.MSG_RETRY_COUNT)) {
                int retryCount = (int)request.get(MomMsgTranslator.MSG_RETRY_COUNT);
                log.warn("No response returned from request on " + destination + " queue after (" +
                        super.getMomClient().getRPCTimout() + "*" + retryCount + 1 + ") sec...");
                if ((super.getMomClient().getRPCRetry() - retryCount+1) > 0) {
                    request.put(MomMsgTranslator.MSG_RETRY_COUNT, retryCount+1);
                    destinationTrace.put(destination, true);
                    log.warn("Retry (" + request.get(MomMsgTranslator.MSG_RETRY_COUNT) + ")");
                    return this.RPC(request, destination, answerSource, answerWorker);
                } else
                    throw new TimeoutException(
                            "No response returned from request on " + destination + " queue after " +
                                    super.getMomClient().getRPCTimout() + "*" + super.getMomClient().getRPCRetry() + " sec..."
                    );
            } else {
                request.put(MomMsgTranslator.MSG_RETRY_COUNT, 1);
                return this.RPC(request, destination, answerSource, answerWorker);
            }
        }

        if (answerWorker!=null)
            if (response!=null) response = answerWorker.apply(response);
            else throw new MomException("Response to apply on answerWorker is null !?");

        return response;
    }

    /**
     * close groupID message group answer subscriptions and clean registry
     * @param groupID message group ID
     */
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

    /**
     * clear and close this RequestExecutor resources
     * @throws IOException if error occurs when closing RabbitMQ channel
     */
    public void stop() throws IOException {
        replyConsumers.clear();
        rpcEchangeBindedDestinations.clear();
        fafEchangeBindedDestinations.clear();
        channel.close();
    }
}