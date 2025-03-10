/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.agent.plugin.tracing.opentelemetry.handler;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.apache.shenyu.agent.api.entity.MethodResult;
import org.apache.shenyu.agent.api.entity.TargetObject;
import org.apache.shenyu.agent.api.handler.InstanceMethodHandler;
import org.apache.shenyu.agent.plugin.tracing.common.constant.TracingConstants;
import org.apache.shenyu.agent.plugin.tracing.opentelemetry.span.OpenTelemetrySpanManager;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The type OpenTelemetry common plugin handler.
 */
public class OpenTelemetryPluginCommonHandler implements InstanceMethodHandler {

    @Override
    public void before(final TargetObject target, final Method method, final Object[] args, final MethodResult result) {
        final ServerWebExchange exchange = (ServerWebExchange) args[0];
        final OpenTelemetrySpanManager spanManager = (OpenTelemetrySpanManager) exchange.getAttributes()
                .getOrDefault(TracingConstants.SHENYU_AGENT, new OpenTelemetrySpanManager());

        Map<String, String> attributesMap = new HashMap<>(2, 1);
        attributesMap.put(TracingConstants.COMPONENT, TracingConstants.NAME);

        Span span = spanManager.startAndRecord(method.getDeclaringClass().getSimpleName(), attributesMap);
        exchange.getAttributes().put(TracingConstants.SHENYU_AGENT, spanManager);
        target.setContext(span);
    }

    @Override
    public Object after(final TargetObject target, final Method method, final Object[] args, final MethodResult methodResult) {
        Object result = methodResult.getResult();
        Span span = (Span) target.getContext();
        ServerWebExchange exchange = (ServerWebExchange) args[0];
        OpenTelemetrySpanManager manager = (OpenTelemetrySpanManager) exchange.getAttributes().get(TracingConstants.SHENYU_AGENT);

        if (result instanceof Mono) {
            return ((Mono) result).doFinally(s -> manager.finish(span, exchange));
        }

        manager.finish(span, exchange);
        return result;
    }

    @Override
    public void onThrowing(final TargetObject target, final Method method, final Object[] args, final Throwable throwable) {
        Span span = (Span) target.getContext();
        span.setStatus(StatusCode.ERROR).recordException(throwable);

        ServerWebExchange exchange = (ServerWebExchange) args[0];
        OpenTelemetrySpanManager manager = (OpenTelemetrySpanManager) exchange.getAttributes().get(TracingConstants.SHENYU_AGENT);

        manager.finish(span, exchange);
    }
}
