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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.osgi.OsgiConsoleClient;
import org.ops4j.pax.tinybundles.TinyBundles;
import org.osgi.framework.Constants;

/**
 * Builds and manages the IT support bundle containing {@link ItToolContribution}.
 *
 * <p>The bundle is assembled at test time using TinyBundles + bnd so it gets correct
 * OSGi {@code Import-Package} headers for all MCP SDK and Sling SPI packages.</p>
 */
public class McpItSupportBundle {

    public static final String BUNDLE_SYMBOLIC_NAME = "org.apache.sling.mcp.server.itbundle";

    private final Path parentDirectory;
    private Path bundlePath;

    public McpItSupportBundle(Path parentDirectory) {
        this.parentDirectory = parentDirectory;
    }

    /**
     * Builds the support bundle JAR and returns its path.
     */
    public Path generate() throws IOException {
        InputStream bundleStream = TinyBundles.bundle()
                .setHeader(Constants.BUNDLE_SYMBOLICNAME, BUNDLE_SYMBOLIC_NAME)
                .setHeader(Constants.BUNDLE_VERSION, "1.0.0.SNAPSHOT")
                .addClass(ItToolContribution.class)
                .build(TinyBundles.bndBuilder());

        bundlePath = parentDirectory.resolve("mcp-it-support-bundle.jar");
        Files.copy(bundleStream, bundlePath);
        return bundlePath;
    }

    /**
     * Installs the support bundle into the running Sling instance and waits until it is active.
     */
    public void install(OsgiConsoleClient client) throws ClientException, InterruptedException, TimeoutException {
        if (bundlePath == null) {
            throw new IllegalStateException("Bundle not generated; call generate() first");
        }
        client.waitInstallBundle(bundlePath.toFile(), true, 10, TimeUnit.SECONDS.toMillis(10), 500);
    }

    /**
     * Uninstalls the support bundle from the running Sling instance.
     */
    public void uninstall(OsgiConsoleClient client) throws ClientException {
        client.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
    }
}
