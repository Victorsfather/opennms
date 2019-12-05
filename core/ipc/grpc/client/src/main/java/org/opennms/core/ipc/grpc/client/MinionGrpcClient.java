/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.core.ipc.grpc.client;

import static org.opennms.core.ipc.sink.api.Message.SINK_METRIC_PRODUCER_DOMAIN;
import static org.opennms.core.ipc.sink.api.SinkModule.HEARTBEAT_MODULE_ID;
import static org.opennms.core.rpc.api.RpcModule.MINION_HEADERS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.opennms.core.ipc.grpc.common.OnmsIpcGrpc;
import org.opennms.core.ipc.grpc.common.RpcMessage;
import org.opennms.core.ipc.grpc.common.SinkMessage;
import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.common.AbstractMessageDispatcherFactory;
import org.opennms.core.logging.Logging;
import org.opennms.core.rpc.api.RpcModule;
import org.opennms.core.rpc.api.RpcRequest;
import org.opennms.core.rpc.api.RpcResponse;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.protobuf.ByteString;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class MinionGrpcClient extends AbstractMessageDispatcherFactory<String> {

    private static final Logger LOG = LoggerFactory.getLogger(MinionGrpcClient.class);
    private final ManagedChannel channel;
    private final OnmsIpcGrpc.OnmsIpcStub asyncStub;
    private BundleContext bundleContext;
    private String location;
    private String systemId;
    private StreamObserver<RpcMessage> rpcResponseSender;
    private StreamObserver<SinkMessage> sinkMessageSender;
    private ConnectivityState connectivityState;
    private RpcMessageHandler rpcMessageHandler;
    private final Map<String, RpcModule<RpcRequest, RpcResponse>> registerdModules = new ConcurrentHashMap<>();
    private List<RpcMessage> requestsInProcess = Collections.synchronizedList(new ArrayList<>());
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();


    public MinionGrpcClient(String location, String systemId, String host, int port) {

        this(ManagedChannelBuilder.forAddress(host, port)
                .keepAliveWithoutCalls(true)
                .usePlaintext());
        this.location = location;
        this.systemId = systemId;
    }

    public MinionGrpcClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        asyncStub = OnmsIpcGrpc.newStub(channel);
    }

    public void start() {
        connectivityState = getChannelState();
        initializeRpcStub();
        initializeSinkStub();
        //executor.scheduleAtFixedRate(this::sendMinionHeaders, 0, 30, TimeUnit.SECONDS);
        LOG.info("Minion at location {} with systemId {} started", location, systemId);
    }

    private void initializeRpcStub() {
        rpcMessageHandler = new RpcMessageHandler();
        rpcResponseSender = asyncStub.rpcStreaming(rpcMessageHandler);
        sendMinionHeaders();
        LOG.info("Initialized RPC stub");
        processPendingRequests();
    }

    private void processPendingRequests() {
        requestsInProcess.forEach(rpcMessage -> {
            try {
                processRpcMessage(rpcMessage);
            } catch (Throwable e) {
                LOG.error("Error while re-attempting to process RPC message {}", rpcMessage, e);
            }
        });
        requestsInProcess.clear();
    }

    private void initializeSinkStub() {
        sinkMessageSender = asyncStub.sinkStreaming(new EmptyMessageReceiver());
    }


    @Override
    public <S extends Message, T extends Message> void dispatch(SinkModule<S, T> module, String metadata, T message) {

        try (Logging.MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
            byte[] sinkMessageContent = module.marshal(message);
            String messageId = UUID.randomUUID().toString();
            SinkMessage.Builder sinkMessageBuilder = SinkMessage.newBuilder()
                    .setMessageId(messageId)
                    .setLocation(location)
                    .setModuleId(module.getId())
                    .setContent(ByteString.copyFrom(sinkMessageContent));


            if (module.getId().equals(HEARTBEAT_MODULE_ID)) {
                if (hasChangedToReadyState()) {
                    LOG.info("Channel is in READY STATE");
                    initializeSinkStub();
                    initializeRpcStub();
                }
            }
            if (getChannelState().equals(ConnectivityState.READY)) {
                sinkMessageSender.onNext(sinkMessageBuilder.build());
            } else {
                LOG.info("gRPC server is not in ready state");
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void bind(RpcModule module) throws Exception {
        if (module != null) {
            final RpcModule<RpcRequest, RpcResponse> rpcModule = (RpcModule<RpcRequest, RpcResponse>) module;
            if (registerdModules.containsKey(rpcModule.getId())) {
                LOG.warn(" {} module is already registered", rpcModule.getId());
            } else {
                registerdModules.put(rpcModule.getId(), rpcModule);
                LOG.info("Registered module {} with RPC message receivers.", rpcModule.getId());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void unbind(RpcModule module) throws Exception {
        if (module != null) {
            final RpcModule<RpcRequest, RpcResponse> rpcModule = (RpcModule<RpcRequest, RpcResponse>) module;
            registerdModules.remove(rpcModule.getId());
            LOG.info("Removing module {} from RPC message receivers.", rpcModule.getId());
        }
    }

    private void sendMinionHeaders() {

        RpcMessage.Builder rpcMessageBuilder = RpcMessage.newBuilder()
                .setLocation(location)
                .setSystemId(systemId)
                .setModuleId(MINION_HEADERS)
                .setRpcId(systemId);
        if (getChannelState().equals(ConnectivityState.READY)) {
            if (rpcResponseSender != null) {
                rpcResponseSender.onNext(rpcMessageBuilder.build());
                LOG.debug("Sending Minion Headers to gRPC server");
            }
        } else {
            LOG.info("gRPC server is not in ready state");
        }

    }

    private void sendAck(RpcMessage rpcMessage) {

        RpcMessage.Builder rpcMessageBuilder = RpcMessage.newBuilder()
                .setLocation(rpcMessage.getLocation())
                .setModuleId(MINION_HEADERS)
                .setRpcId(rpcMessage.getRpcId());
        if(!Strings.isNullOrEmpty(rpcMessage.getSystemId())) {
            rpcMessageBuilder.setSystemId(rpcMessage.getSystemId());
        }
        if (getChannelState().equals(ConnectivityState.READY)) {
            if (rpcResponseSender != null) {
                rpcResponseSender.onNext(rpcMessageBuilder.build());
                LOG.debug("Sending Ack for rpcId {}", rpcMessage.getRpcId());
            }
        } else {
            LOG.info("gRPC server is not in ready state");
        }

    }



    private void processRpcMessage(RpcMessage rpcMessage) {
        String moduleId = rpcMessage.getModuleId();
        LOG.debug("Received message for module {} with Id {}, message {}", moduleId, rpcMessage.getRpcId(), rpcMessage.getRpcContent().toStringUtf8());
        RpcModule<RpcRequest, RpcResponse> rpcModule = registerdModules.get(moduleId);
        if (rpcModule == null) {
            return;
        }
        RpcRequest rpcRequest = rpcModule.unmarshalRequest(rpcMessage.getRpcContent().toStringUtf8());
        CompletableFuture<RpcResponse> future = rpcModule.execute(rpcRequest);
        future.whenComplete((res, ex) -> {
            final RpcResponse response;
            if (ex != null) {
                // An exception occurred, store the exception in a new response
                LOG.warn("An error occured while executing a call in {}.", rpcModule.getId(), ex);
                response = rpcModule.createResponseWithException(ex);
            } else {
                // No exception occurred, use the given response
                response = res;
            }
            // Construct response using the same rpcId;
            String responseAsString = rpcModule.marshalResponse(response);
            RpcMessage.Builder rpcMessageBuilder = RpcMessage.newBuilder()
                    .setRpcId(rpcMessage.getRpcId())
                    .setSystemId(systemId)
                    .setLocation(rpcMessage.getLocation())
                    .setModuleId(rpcMessage.getModuleId())
                    .setRpcContent(ByteString.copyFrom(responseAsString.getBytes()));
            if (rpcResponseSender != null) {
                try {
                    rpcResponseSender.onNext(rpcMessageBuilder.build());
                    LOG.debug("Response sent for module {} with Id {} and response = {}", moduleId, rpcMessage.getRpcId(), responseAsString);
                    requestsInProcess.remove(rpcMessage);
                } catch (Throwable e) {
                    LOG.error("Error while sending response {}", responseAsString, e);
                    rpcMessageHandler.onError(e);
                }
            }

        });
    }


    private class RpcMessageHandler implements StreamObserver<RpcMessage> {

        @Override
        public void onNext(RpcMessage rpcMessage) {

            try {
                requestsInProcess.add(rpcMessage);
                sendAck(rpcMessage);
                processRpcMessage(rpcMessage);
            } catch (Throwable e) {
                LOG.error("Error while processing the RPC Request", e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            LOG.error("Error in RPC streaming", throwable);
            onCompleted();
        }

        @Override
        public void onCompleted() {
            LOG.error("Closing RPC message handler");
            initializeRpcStub();
        }
    }

    private boolean hasChangedToReadyState() {
        ConnectivityState prevState = connectivityState;
        connectivityState = getChannelState();
        return !prevState.equals(ConnectivityState.READY) && connectivityState.equals(ConnectivityState.READY);
    }

    public void shutdown() {
        executor.shutdown();
        if (rpcResponseSender != null) {
            rpcResponseSender.onCompleted();
        }
        channel.shutdown();
        LOG.info("Minion at location {} with systemId {} stopped", location, systemId);
    }

    public StreamObserver<SinkMessage> getSinkMessageSender() {
        return sinkMessageSender;
    }

    public StreamObserver<RpcMessage> getRpcResponseSender() {
        return rpcResponseSender;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }


    @Override
    public String getMetricDomain() {
        return SINK_METRIC_PRODUCER_DOMAIN;
    }

    @Override
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    @Override
    public Tracer getTracer() {
        return GlobalTracer.get();
    }

    public ConnectivityState getChannelState() {
        return channel.getState(true);
    }
}
