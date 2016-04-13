/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.rest.protocols.tcp.redis;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.rest.GridRestCommand;
import org.apache.ignite.internal.processors.rest.GridRestProtocolHandler;
import org.apache.ignite.internal.processors.rest.GridRestResponse;
import org.apache.ignite.internal.processors.rest.protocols.tcp.redis.handler.GridRedisCommandHandler;
import org.apache.ignite.internal.processors.rest.protocols.tcp.redis.handler.GridRedisConnectionCommandHandler;
import org.apache.ignite.internal.processors.rest.request.GridRestCacheRequest;
import org.apache.ignite.internal.processors.rest.request.GridRestRequest;
import org.apache.ignite.internal.util.nio.GridNioFuture;
import org.apache.ignite.internal.util.nio.GridNioServerListenerAdapter;
import org.apache.ignite.internal.util.nio.GridNioSession;
import org.apache.ignite.internal.util.typedef.CIX1;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.internal.processors.rest.GridRestCommand.CACHE_GET;

/**
 * Listener for Redis protocol requests.
 */
public class GridRedisNioListener extends GridNioServerListenerAdapter<GridRedisMessage> {
    /** Logger. */
    private final IgniteLogger log;

    /** Protocol handler. */
    private GridRestProtocolHandler hnd;

    /** Redis-specific handlers. */
    protected final Map<GridRedisCommand, GridRedisCommandHandler> handlers = new EnumMap<>(GridRedisCommand.class);

    /**
     * @param log Logger.
     * @param hnd REST protocol handler.
     * @param ctx Context.
     */
    public GridRedisNioListener(IgniteLogger log, GridRestProtocolHandler hnd, GridKernalContext ctx) {
        this.hnd = hnd;
        this.log = log;

        addCommandHandler(new GridRedisConnectionCommandHandler());
    }

    /**
     * Adds Redis-specific command handlers.
     * <p>
     * Generic commands are treated by REST.
     *
     * @param hnd Redis-specific command handler.
     */
    private void addCommandHandler(GridRedisCommandHandler hnd) {
        assert !handlers.containsValue(hnd);

        if (log.isDebugEnabled())
            log.debug("Added Redis command handler: " + hnd);

        for (GridRedisCommand cmd : hnd.supportedCommands()) {
            assert !handlers.containsKey(cmd) : cmd;

            handlers.put(cmd, hnd);
        }
    }

    /** {@inheritDoc} */
    @Override public void onConnected(GridNioSession ses) {
        // No-op, never called.
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(GridNioSession ses, @Nullable Exception e) {
        // No-op, never called.
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void onMessage(final GridNioSession ses, final GridRedisMessage msg) {
        if (handlers.get(msg.command()) != null) {
            IgniteInternalFuture<GridRedisMessage> f = handlers.get(msg.command()).handleAsync(msg);

            f.listen(new CIX1<IgniteInternalFuture<GridRedisMessage>>() {
                @Override public void applyx(IgniteInternalFuture<GridRedisMessage> f) throws IgniteCheckedException {
                    GridRedisMessage res = f.get();

                    sendResponse(ses, res);
                }
            });
        }
        else {
            IgniteInternalFuture<GridRestResponse> f = hnd.handleAsync(toRestRequest(msg));

            f.listen(new CIX1<IgniteInternalFuture<GridRestResponse>>() {
                @Override public void applyx(IgniteInternalFuture<GridRestResponse> f) throws IgniteCheckedException {
                    GridRestResponse restRes = f.get();

                    GridRedisMessage res = msg;
                    ByteBuffer resp;

                    if (restRes.getSuccessStatus() == GridRestResponse.STATUS_SUCCESS) {
                        switch (res.command()) {
                            case GET:
                                resp = (restRes.getResponse() == null ? GridRedisProtocolParser.nil()
                                    : GridRedisProtocolParser.toBulkString(restRes.getResponse()));

                                break;
                            default:
                                resp = GridRedisProtocolParser.toGenericError("Unsupported operation!");
                        }
                        res.setResponse(resp);
                    }
                    else
                        res.setResponse(GridRedisProtocolParser.toGenericError("Operation error!"));

                    sendResponse(ses, res);
                }
            });
        }
    }

    /**
     * Sends a response to be decoded and sent to the Redis client.
     *
     * @param ses NIO session.
     * @param res Response.
     * @return NIO send future.
     */
    private GridNioFuture<?> sendResponse(GridNioSession ses, GridRedisMessage res) {
        return ses.send(res);
    }

    /**
     * @param msg {@link GridRedisMessage}
     * @return {@link GridRestRequest}
     */
    private GridRestRequest toRestRequest(GridRedisMessage msg) {
        assert msg != null;

        GridRestCacheRequest restReq = new GridRestCacheRequest();

        restReq.command(redisToRestCommand(msg.command()));
        restReq.clientId(msg.clientId());
        restReq.key(msg.key());

        return restReq;
    }

    private GridRestCommand redisToRestCommand(GridRedisCommand cmd) {
        GridRestCommand restCmd;

        switch (cmd) {
            case GET:
                restCmd = CACHE_GET;

                break;
            default:
                return null;
        }

        return restCmd;
    }
}
