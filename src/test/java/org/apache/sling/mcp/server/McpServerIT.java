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
package org.apache.sling.mcp.server;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.sling.mcp.server.itbundle.ItToolContribution;
import org.apache.sling.mcp.server.itbundle.McpItSupportBundle;
import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.SlingClient;
import org.apache.sling.testing.clients.osgi.OsgiConsoleClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class McpServerIT {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration AWAIT_POLL_INTERVAL = Duration.ofMillis(500);

    private static int slingPort;
    private static McpItSupportBundle supportBundle;

    private SlingClient sling;
    private McpSyncClient mcpClient;

    @BeforeAll
    static void buildSupportBundle(@TempDir Path tempDir) throws Exception {
        slingPort = Integer.getInteger("sling.http.port", 8080);
        supportBundle = new McpItSupportBundle(tempDir);
        supportBundle.generate();
    }

    @BeforeEach
    void setup() throws ClientException, InterruptedException, TimeoutException {
        sling = SlingClient.Builder.create(URI.create("http://localhost:" + slingPort), "admin", "admin")
                .build();

        // deploy the IT tool contribution bundle.
        supportBundle.install(sling.adaptTo(OsgiConsoleClient.class));

        // build the MCP sync client with HTTP Basic Auth for admin access.
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes());
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + slingPort)
                .endpoint("/bin/mcp")
                .customizeRequest(rb -> rb.header("Authorization", basicAuthHeader))
                .build();

        mcpClient = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mcp-server-it", "1.0"))
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        mcpClient.initialize();
    }

    @AfterEach
    void teardown() {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (sling != null) {
            try {
                supportBundle.uninstall(sling.adaptTo(OsgiConsoleClient.class));
            } catch (ClientException e) {
                // ignore
            }
        }
    }

    @Test
    void toolIsRegisteredAndCanBeInvoked() {
        // wait for the tool to be registered
        await("it-hello tool appears in tool listing")
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .until(() -> mcpClient.listTools().tools().stream()
                        .anyMatch(t -> ItToolContribution.TOOL_NAME.equals(t.name())));

        // call the tool and verify the response.
        McpSchema.CallToolResult result =
                mcpClient.callTool(new McpSchema.CallToolRequest(ItToolContribution.TOOL_NAME, Map.of()));

        assertThat(result.isError())
                .as("tool result should not indicate an error")
                .isNotEqualTo(Boolean.TRUE);
        assertThat(result.content())
                .as("tool result content")
                .hasSize(1)
                .first()
                .isInstanceOfSatisfying(McpSchema.TextContent.class, tc -> assertThat(tc.text())
                        .isEqualTo(ItToolContribution.TOOL_RESPONSE));
    }
}
