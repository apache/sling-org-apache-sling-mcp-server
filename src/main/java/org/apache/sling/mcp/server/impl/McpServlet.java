/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.mcp.server.impl;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessRequestHandler;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper;
import org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.mcp.server.spi.McpServerContribution;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

@Component(service = Servlet.class)
@SlingServletPaths(value = {McpServlet.ENDPOINT})
@Designate(ocd = McpServlet.Config.class)
public class McpServlet extends SlingAllMethodsServlet {

    @ObjectClassDefinition(name = "Apache Sling MCP Server Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Server Title", description = "The title of the MCP server")
        String serverTitle() default "Apache Sling";

        @AttributeDefinition(
                name = "Server Version",
                description = "The version of the MCP server. Defaults to the bundle version if not set")
        String serverVersion();

        @AttributeDefinition(name = "Instructions", description = "Initial instructions for the MCP server")
        String instructions() default
                "This MCP server provides access to an Apache Sling development instance. Exposed tools and information always reference the Sling deployment and not local projects or files";
    }

    static final String ENDPOINT = "/bin/mcp";
    private static final long serialVersionUID = 1L;
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private McpStatelessSyncServer syncServer;
    private HttpServletStatelessServerTransport transportProvider;
    private MethodHandle doGetMethod;
    private MethodHandle doPostMethod;

    @Activate
    public McpServlet(
            BundleContext ctx,
            Config config,
            @Reference(cardinality = MULTIPLE, policyOption = GREEDY) List<McpServerContribution> contributions)
            throws IllegalAccessException, NoSuchMethodException {

        transportProvider = HttpServletStatelessServerTransport.builder()
                .messageEndpoint(ENDPOINT)
                .jsonMapper(McpJsonDefaults.getMapper())
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                        "resourceResolver",
                        ((BridgedJakartaHttpServletRequest) request)
                                .getSlingRequest()
                                .getResourceResolver())))
                .build();

        MethodHandles.Lookup privateLookup =
                MethodHandles.privateLookupIn(HttpServletStatelessServerTransport.class, LOOKUP);

        doGetMethod = privateLookup.findVirtual(
                HttpServletStatelessServerTransport.class,
                "doGet",
                java.lang.invoke.MethodType.methodType(
                        void.class,
                        jakarta.servlet.http.HttpServletRequest.class,
                        jakarta.servlet.http.HttpServletResponse.class));
        doPostMethod = privateLookup.findVirtual(
                HttpServletStatelessServerTransport.class,
                "doPost",
                java.lang.invoke.MethodType.methodType(
                        void.class,
                        jakarta.servlet.http.HttpServletRequest.class,
                        jakarta.servlet.http.HttpServletResponse.class));

        String serverVersion = config.serverVersion();
        if (serverVersion == null || serverVersion.isEmpty()) {
            serverVersion = ctx.getBundle().getVersion().toString();
        }

        var completions = contributions.stream()
                .map(McpServerContribution::getSyncCompletionSpecification)
                .flatMap(List::stream)
                .toList();

        syncServer = McpServer.sync(transportProvider)
                .serverInfo(config.serverTitle(), serverVersion)
                .jsonMapper(McpJsonDefaults.getMapper())
                .jsonSchemaValidator(McpJsonDefaults.getSchemaValidator())
                .instructions(config.instructions())
                .completions(completions)
                .capabilities(ServerCapabilities.builder()
                        .tools(false)
                        .prompts(false)
                        .resources(false, false)
                        .completions()
                        .build())
                .build();

        // workaround for https://github.com/modelcontextprotocol/java-sdk/issues/776
        // cursor tries to register for resource updates even if we don't advertise that capability
        tryRegisterNoopResourcesSubscribeHandler();

        contributions.stream()
                .map(McpServerContribution::getSyncToolSpecification)
                .flatMap(List::stream)
                .forEach(syncTool -> syncServer.addTool(syncTool));

        contributions.stream()
                .map(McpServerContribution::getSyncResourceSpecification)
                .flatMap(List::stream)
                .forEach(syncResource -> syncServer.addResource(syncResource));

        contributions.stream()
                .map(McpServerContribution::getSyncResourceTemplateSpecification)
                .flatMap(List::stream)
                .forEach(syncResource -> syncServer.addResourceTemplate(syncResource));

        contributions.stream()
                .map(McpServerContribution::getSyncPromptSpecification)
                .flatMap(List::stream)
                .forEach(syncPrompt -> syncServer.addPrompt(syncPrompt));
    }

    private void tryRegisterNoopResourcesSubscribeHandler() {
        try {
            MethodHandles.Lookup transportLookup =
                    MethodHandles.privateLookupIn(HttpServletStatelessServerTransport.class, LOOKUP);
            MethodHandle mcpHandlerGetter = transportLookup.findGetter(
                    HttpServletStatelessServerTransport.class, "mcpHandler", McpStatelessServerHandler.class);
            Object mcpHandler = mcpHandlerGetter.invoke(transportProvider);

            Class<?> handlerClass = mcpHandler.getClass();
            MethodHandles.Lookup handlerLookup = MethodHandles.privateLookupIn(handlerClass, LOOKUP);
            MethodHandle requestHandlersGetter = handlerLookup.findGetter(handlerClass, "requestHandlers", Map.class);
            Map<String, McpStatelessRequestHandler<?>> handlers =
                    (Map<String, McpStatelessRequestHandler<?>>) requestHandlersGetter.invoke(mcpHandler);

            handlers.put(McpSchema.METHOD_RESOURCES_SUBSCRIBE, (context, params) -> Mono.just(Map.of()));
        } catch (Throwable t) {
            logger.warn(
                    "Failed to register MCP resources subscribe handler, non-compliant clients requesting resource updates might fail",
                    t);
        }
    }

    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = GREEDY, cardinality = MULTIPLE)
    protected void bindPrompt(DiscoveredPrompt prompt, Map<String, Object> properties) {
        syncServer.addPrompt(new SyncPromptSpecification(prompt.asPrompt(), (c, r) -> {
            var messages = prompt.getPromptMessages(c, r);
            return new McpSchema.GetPromptResult(null, messages);
        }));
    }

    protected void unbindPrompt(Map<String, Object> properties) {
        String promptName = (String) properties.get(DiscoveredPrompt.SERVICE_PROP_NAME);
        syncServer.removePrompt(promptName);
    }

    @Override
    protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response)
            throws ServletException, IOException {
        try {
            doGetMethod.invoke(
                    transportProvider,
                    new BridgedJakartaHttpServletRequest(request),
                    new HttpServletResponseWrapper(response));
        } catch (ServletException | IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new ServletException(t);
        }
    }

    @Override
    protected void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response)
            throws ServletException, IOException {
        try {
            doPostMethod.invoke(
                    transportProvider,
                    new BridgedJakartaHttpServletRequest(request),
                    new HttpServletResponseWrapper(response));
        } catch (ServletException | IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new ServletException(t);
        }
    }

    @Deactivate
    public void close() {
        if (syncServer != null) {
            syncServer.close();
        }
    }

    static class BridgedJakartaHttpServletRequest extends HttpServletRequestWrapper {
        private SlingHttpServletRequest slingRequest;

        public BridgedJakartaHttpServletRequest(SlingHttpServletRequest request) {
            super(request);
            this.slingRequest = request;
        }

        public SlingHttpServletRequest getSlingRequest() {
            return slingRequest;
        }
    }
}
