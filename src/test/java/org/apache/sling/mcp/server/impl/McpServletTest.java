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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.sling.mcp.server.spi.McpServerContribution;
import org.apache.sling.mcp.server.spi.McpServerDescription;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

/**
 * Tests for {@link McpServlet}.
 */
public class McpServletTest {

    /**
     * Keeps valid tools from the same and subsequent contributions when the SDK rejects schemas.
     */
    @Test
    public void activationIsolatesInvalidToolSchemas() throws Exception {
        var first = tool("first", Map.of("type", "object"));
        var invalidInput = tool("invalid\r\ninput", Map.of("type", "not-a-json-schema-type"));
        var invalidOutput = new SyncToolSpecification(
                McpSchema.Tool.builder("invalid-output", Map.of("type", "object"))
                        .outputSchema(Map.of("required", "not-an-array"))
                        .build(),
                (context, request) -> McpSchema.CallToolResult.builder().build());
        var last = tool("last", Map.of("type", "object"));
        var later = tool("later", Map.of("type", "object"));

        McpServlet servlet = createServlet(
                List.of(contribution(List.of(first, invalidInput, invalidOutput, last)), contribution(List.of(later))));
        try {
            Field serverField = McpServlet.class.getDeclaredField("syncServer");
            serverField.setAccessible(true);
            var server = (McpStatelessSyncServer) serverField.get(servlet);

            Assert.assertEquals(
                    List.of("first", "last", "later"),
                    server.listTools().stream().map(McpSchema.Tool::name).toList());
        } finally {
            servlet.close();
        }
    }

    private static SyncToolSpecification tool(String name, Map<String, Object> inputSchema) {
        return new SyncToolSpecification(
                McpSchema.Tool.builder(name, inputSchema).build(),
                (context, request) -> McpSchema.CallToolResult.builder().build());
    }

    private static McpServerContribution contribution(List<SyncToolSpecification> tools) {
        McpServerContribution contribution = Mockito.mock(McpServerContribution.class);
        Mockito.when(contribution.getSyncToolSpecification()).thenReturn(tools);
        return contribution;
    }

    private static McpServlet createServlet(List<McpServerContribution> contributions)
            throws IllegalAccessException, NoSuchMethodException {
        McpJsonMapperSupplier mapperSupplier = Mockito.mock(McpJsonMapperSupplier.class);
        Mockito.when(mapperSupplier.get()).thenReturn(new JacksonMcpJsonMapperSupplier().get());
        JsonSchemaValidatorSupplier validatorSupplier = Mockito.mock(JsonSchemaValidatorSupplier.class);
        Mockito.when(validatorSupplier.get()).thenReturn(new JacksonJsonSchemaValidatorSupplier().get());
        McpServerDescription description = Mockito.mock(McpServerDescription.class);
        Mockito.when(description.name()).thenReturn("test-server");
        Mockito.when(description.version()).thenReturn("1.0");
        return new McpServlet(
                Mockito.mock(BundleContext.class),
                Mockito.mock(McpServlet.Config.class),
                mapperSupplier,
                validatorSupplier,
                description,
                contributions);
    }
}
