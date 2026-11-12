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
package org.apache.sling.mcp.server.impl.contribs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.mcp.server.impl.DiscoveredPrompt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveredPromptBuilderTest {

    @Test
    void basicMarkdownFile() throws IOException {

        String markdownFile = """
                # Basic Prompt
                """;

        Resource promptResource = mock(Resource.class);
        when(promptResource.getResourceMetadata()).thenReturn(new ResourceMetadata());
        when(promptResource.adaptTo(InputStream.class))
                .thenReturn(new ByteArrayInputStream(markdownFile.getBytes(StandardCharsets.UTF_8)));

        DiscoveredPromptBuilder builder = new DiscoveredPromptBuilder();
        DiscoveredPrompt prompt = builder.buildPrompt(promptResource, "basic-prompt");

        assertThat(prompt)
                .as("prompt")
                .extracting(DiscoveredPrompt::asPrompt)
                .extracting(Prompt::name, Prompt::title, Prompt::description, Prompt::arguments)
                .containsExactly("basic-prompt", null, null, List.of());
    }

    @Test
    void basicFrontMatter() throws IOException {

        String markdownFile = """
                ---
                title: Basic Prompt
                description: |
                    A basic prompt for testing.
                    Spans multiple lines.
                ---
                # Basic Prompt
                """;

        Resource promptResource = mock(Resource.class);
        when(promptResource.getResourceMetadata()).thenReturn(new ResourceMetadata());
        when(promptResource.adaptTo(InputStream.class))
                .thenReturn(new ByteArrayInputStream(markdownFile.getBytes(StandardCharsets.UTF_8)));

        DiscoveredPromptBuilder builder = new DiscoveredPromptBuilder();
        DiscoveredPrompt prompt = builder.buildPrompt(promptResource, "prompt-with-front-matter");

        assertThat(prompt)
                .as("prompt")
                .extracting(DiscoveredPrompt::asPrompt)
                .extracting(Prompt::name, Prompt::title, Prompt::description, Prompt::arguments)
                .containsExactly(
                        "prompt-with-front-matter",
                        "Basic Prompt",
                        "A basic prompt for testing.\nSpans multiple lines.",
                        List.of());
    }

    @Test
    void arguments() throws IOException {

        String markdownFile = """
                ---
                title: Basic Prompt
                argument.first:
                    - title: First Argument
                    - description: This is the first argument
                    - required: true
                argument.second:
                    - title: Second Argument
                ---
                # Basic Prompt
                """;

        Resource promptResource = mock(Resource.class);
        when(promptResource.getResourceMetadata()).thenReturn(new ResourceMetadata());
        when(promptResource.adaptTo(InputStream.class))
                .thenReturn(new ByteArrayInputStream(markdownFile.getBytes(StandardCharsets.UTF_8)));

        DiscoveredPromptBuilder builder = new DiscoveredPromptBuilder();
        DiscoveredPrompt prompt = builder.buildPrompt(promptResource, "prompt-with-arguments");
        assertThat(prompt)
                .as("prompt")
                .extracting(DiscoveredPrompt::asPrompt)
                .extracting(Prompt::name, Prompt::title, Prompt::description, Prompt::arguments)
                .containsExactly(
                        "prompt-with-arguments",
                        "Basic Prompt",
                        null,
                        List.of(
                                PromptArgument.builder("first")
                                        .title("First Argument")
                                        .description("This is the first argument")
                                        .required(true)
                                        .build(),
                                PromptArgument.builder("second")
                                        .title("Second Argument")
                                        .required(false)
                                        .build()));
    }

    @Test
    void emptyArgumentNameRejectsEntirePrompt() {
        Resource resource = promptResource("invalid", """
                ---
                argument.valid:
                    - required: true
                argument.:
                    - required: true
                ---
                # Invalid Prompt
                """);

        Assertions.assertThatThrownBy(() -> new DiscoveredPromptBuilder().buildPrompt(resource, "invalid"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("argument.<name>");
    }

    @Test
    void promptMessagesSubstituteArgumentsAndStripFrontMatter() throws IOException {
        String markdown = """
                ---
                title: Greeting
                argument.person:
                    - title: Person
                    - required: true
                ---
                Hello {person}
                """;
        Resource resource = promptResource("greeting", markdown);
        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resolver.getResource(resource.getPath())).thenReturn(resource);
        when(resource.adaptTo(InputStream.class))
                .thenAnswer(invocation -> new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
        DiscoveredPrompt prompt = new DiscoveredPromptBuilder().buildPrompt(resource, "greeting");

        var messages = prompt.getPromptMessages(
                McpTransportContext.create(Map.of("resourceResolver", resolver)),
                GetPromptRequest.builder("greeting")
                        .arguments(Map.of("person", "Ada"))
                        .build());

        assertThat(messages).hasSize(1);
        assertThat(((TextContent) messages.get(0).content()).text()).isEqualTo("Hello Ada\n");
    }

    @Test
    void blankPromptNamesAreRejectedBeforeRegistration() {
        for (String name : List.of("", " \t")) {
            Resource resource = promptResource(name, "# Invalid Prompt");

            Assertions.assertThatThrownBy(() -> new DiscoveredPromptBuilder().buildPrompt(resource, name))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Prompt name must not be blank");
        }
    }

    @Test
    void malformedPromptsDoNotPreventValidPeersDuringActivation() throws Exception {
        Resource invalidArgument = promptResource("invalid", """
                ---
                argument.:
                    - required: true
                ---
                # Invalid Prompt
                """);
        Resource invalidName = promptResource("", "# Invalid Name");
        Resource valid = promptResource("valid", "# Valid Prompt");
        ResourceResolver resolver = mock(ResourceResolver.class);
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        BundleContext context = mock(BundleContext.class);
        when(factory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resolver);
        when(resolver.findResources(Mockito.anyString(), Mockito.eq("xpath")))
                .thenReturn(List.of(invalidArgument, invalidName, valid).iterator());
        when(context.registerService(
                        Mockito.eq(DiscoveredPrompt.class), Mockito.any(DiscoveredPrompt.class), Mockito.any()))
                .thenAnswer(invocation -> mock(ServiceRegistration.class));

        new RepositoryPromptsRegistrar(factory, null, context);

        ArgumentCaptor<DiscoveredPrompt> prompts = ArgumentCaptor.forClass(DiscoveredPrompt.class);
        Mockito.verify(context).registerService(Mockito.eq(DiscoveredPrompt.class), prompts.capture(), Mockito.any());
        assertThat(prompts.getValue().asPrompt().name()).isEqualTo("valid");
    }

    @Test
    void invalidUpdateUnregistersPreviousPromptAndContinuesBatch() throws Exception {
        Resource original = promptResource("updated", "# Original Prompt");
        Resource invalidUpdate = promptResource("updated", """
                ---
                argument.:
                    - required: true
                ---
                # Invalid Update
                """);
        Resource invalidName = promptResource("", "# Invalid Name");
        Resource validPeer = promptResource("peer", "# Valid Peer");
        ResourceResolver resolver = mock(ResourceResolver.class);
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        BundleContext context = mock(BundleContext.class);
        ServiceRegistration<?> registration = mock(ServiceRegistration.class);
        when(factory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resolver);
        when(resolver.findResources(Mockito.anyString(), Mockito.eq("xpath")))
                .thenReturn(List.of(original).iterator());
        when(resolver.getResource(invalidUpdate.getPath())).thenReturn(invalidUpdate);
        when(resolver.getResource(invalidName.getPath())).thenReturn(invalidName);
        when(resolver.getResource(validPeer.getPath())).thenReturn(validPeer);
        when(context.registerService(
                        Mockito.eq(DiscoveredPrompt.class), Mockito.any(DiscoveredPrompt.class), Mockito.any()))
                .thenAnswer(invocation -> registration);

        new RepositoryPromptsRegistrar(factory, null, context);
        ArgumentCaptor<ResourceChangeListener> listener = ArgumentCaptor.forClass(ResourceChangeListener.class);
        Mockito.verify(context)
                .registerService(Mockito.eq(ResourceChangeListener.class), listener.capture(), Mockito.any());

        listener.getValue()
                .onChange(List.of(
                        new ResourceChange(ChangeType.CHANGED, invalidUpdate.getPath(), false),
                        new ResourceChange(ChangeType.ADDED, invalidName.getPath(), false),
                        new ResourceChange(ChangeType.ADDED, validPeer.getPath(), false)));

        Mockito.verify(registration).unregister();
        ArgumentCaptor<DiscoveredPrompt> prompts = ArgumentCaptor.forClass(DiscoveredPrompt.class);
        Mockito.verify(context, Mockito.times(2))
                .registerService(Mockito.eq(DiscoveredPrompt.class), prompts.capture(), Mockito.any());
        assertThat(prompts.getAllValues())
                .extracting(prompt -> prompt.asPrompt().name())
                .containsExactly("updated", "peer");
    }

    private Resource promptResource(String name, String contents) {
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn("/libs/sling/mcp/prompts/" + name + ".md");
        when(resource.getResourceMetadata()).thenReturn(new ResourceMetadata());
        when(resource.adaptTo(InputStream.class))
                .thenReturn(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
        return resource;
    }
}
