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

import java.nio.file.Path;

import org.apache.http.HttpVersion;
import org.apache.http.message.BasicStatusLine;
import org.apache.sling.testing.clients.SlingHttpResponse;
import org.apache.sling.testing.clients.exceptions.TestingValidationException;
import org.apache.sling.testing.clients.osgi.OsgiConsoleClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * Tests for {@link McpItSupportBundle}.
 */
public class McpItSupportBundleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private McpItSupportBundle bundle;
    private Path bundlePath;
    private OsgiConsoleClient client;

    @Before
    public void setUp() throws Exception {
        bundle = new McpItSupportBundle(temporaryFolder.getRoot().toPath());
        bundlePath = bundle.generate();
        client = Mockito.mock(OsgiConsoleClient.class);
    }

    @Test
    public void installationWaitsForStartupBeforeUploadingBundle() throws Exception {
        SlingHttpResponse starting = Mockito.mock(SlingHttpResponse.class);
        SlingHttpResponse ready = Mockito.mock(SlingHttpResponse.class);
        Mockito.when(starting.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 503, "Starting"));
        Mockito.when(ready.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        Mockito.when(client.doGet("/system/console/bundles.json", 200, 503)).thenReturn(starting, ready);

        bundle.install(client);

        InOrder requests = Mockito.inOrder(client);
        requests.verify(client, Mockito.times(2)).doGet("/system/console/bundles.json", 200, 503);
        requests.verify(client).waitInstallBundle(bundlePath.toFile(), true, 10, 10000, 500);
        requests.verifyNoMoreInteractions();
    }

    @Test
    public void installationPropagatesUnexpectedReadinessFailureWithoutUploading() throws Exception {
        TestingValidationException failure = new TestingValidationException("Web Console returned HTTP 401");
        Mockito.when(client.doGet("/system/console/bundles.json", 200, 503)).thenThrow(failure);

        TestingValidationException actual =
                Assert.assertThrows(TestingValidationException.class, () -> bundle.install(client));

        Assert.assertSame(failure, actual);
        Mockito.verify(client, Mockito.never())
                .waitInstallBundle(
                        Mockito.any(), Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong());
    }
}
