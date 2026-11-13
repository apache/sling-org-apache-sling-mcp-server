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
package org.apache.sling.mcp.server.itbundle;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.sling.mcp.server.spi.McpServerContribution;
import org.osgi.service.component.annotations.Component;

/**
 * A minimal {@link McpServerContribution} deployed via TinyBundles during integration tests.
 * Registers a single tool named {@value #TOOL_NAME} that returns a fixed text response.
 */
@Component(service = McpServerContribution.class)
public class ItToolContribution implements McpServerContribution {

    public static final String TOOL_NAME = "it-hello";
    public static final String TOOL_RESPONSE = "Hello from IT";
    public static final String TYPED_TOOL_NAME = "it-hello-person";

    @Override
    public List<SyncToolSpecification> getSyncToolSpecification() {
        McpSchema.Tool tool = McpSchema.Tool.builder(
                        TOOL_NAME, Map.of("type", "object", "properties", Map.of(), "additionalProperties", false))
                .description("Integration test tool that returns a fixed greeting")
                .build();

        McpSchema.Tool typedTool = McpSchema.Tool.builder(
                        TYPED_TOOL_NAME,
                        Map.of(
                                "type",
                                "object",
                                "properties",
                                Map.of("name", Map.of("type", "string", "minLength", 1)),
                                "required",
                                List.of("name"),
                                "additionalProperties",
                                false))
                .description("Integration test tool with a required, typed argument")
                .build();

        return List.of(
                new SyncToolSpecification(tool, (ctx, req) -> McpSchema.CallToolResult.builder()
                        .content(List.of(
                                McpSchema.TextContent.builder(TOOL_RESPONSE).build()))
                        .isError(false)
                        .build()),
                new SyncToolSpecification(typedTool, (ctx, req) -> McpSchema.CallToolResult.builder()
                        .content(List.of(McpSchema.TextContent.builder(
                                        TOOL_RESPONSE + " " + req.arguments().get("name"))
                                .build()))
                        .isError(false)
                        .build()));
    }
}
