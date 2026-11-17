# Apache Sling MCP Server

Experimental MCP Server implementation for Apache Sling.

## Development

Build the project with Maven and start up the MCP server, based on the Apache Sling Starter:

```
$ mvn install feature-launcher:start feature-launcher:stop -Dfeature-launcher.waitForInput -Dhttp.port=8080
```

Then build and deploy the [MCP server contributions bundle](https://github.com/apache/sling-org-apache-sling-mcp-server-contributions):

```
$ mvn -f ../org-apache-sling-mcp-server-contributions/pom.xml install sling:install 
```

Then open up your coding assistant tool and add an remote MCP server with location http://localhost:8080/mcp . Access is only
permitted for the `admin` user therefore basic authentication headers need to be specified. In case of the default credentials
the configuration can look as follows

```json
"sling-starter": {
  "type": "streamable-http",
  "url": "http://localhost:8080/bin/mcp",
  "headers": {
    "Authorization": "Basic YWRtaW46YWRtaW4="
  }
}
```

Please refer to the documentation of your coding assistant tool for details on how to add a remote MCP server and specify authentication headers.

## Deployment

The [src/main/features/main.json](src/main/features/main.json) files contains bundles and configurations that are required for deploying
on top of the version of the Sling Starter defined in [pom.xml](pom.xml).

This file is intended mostly for local development but can also be used as a blueprint for including the bundle
in Sling Starter based deployments.

To obtain a ready to use target file:

- build the project with `mvn package`
- copy the file `target/slingfeature-tmp/main.json` to your own project
- adjust the metadata from the feature file as needed ( id, title, etc )

## Contribution compatibility

The server uses the stable MCP Java SDK 2.1.0. Existing contribution bundles built against SDK 1.x
must be migrated and rebuilt together with the server; replacing the server alone does not upgrade
their OSGi SDK imports. In particular, deploy a compatible build of the MCP server contributions bundle.

When migrating contributions:

- Supply tool input schemas as maps using `McpSchema.Tool.builder(name, schema)` instead of the SDK 1.x
  `JsonSchema` record. Access schema properties through the map API.
- Use the SDK 2.x required-field builders for prompt arguments, messages, and tool results.
- Supply structurally valid JSON schemas. SDK 2.1.0 checks schemas during tool registration and validates
  call arguments before invoking handlers. Incorrect types, missing required properties, and forbidden
  extra properties return tool errors; null arguments are validated as an empty object. Correct schemas
  or client arguments rather than disabling validation.
- Use nonblank prompt and argument names in repository prompt front matter.

SDK 2.1.0 also requires Jackson annotations/core/databind 2.21 or newer (below 3.0) and Reactor 3.7
package exports, including `reactor.adapter`. These are provided by the supplied Sling Starter feature.

## Legacy artifact

For applications still using the older slf4j 1.x and javax.servlet APIs a bundle with the 'legacy' classifier is built.

This bundle is not included in the feature model used for local development and integration tests and needs to be deployed
manually in the applications that consume it.
