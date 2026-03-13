# Apache Sling MCP Server

Experimental MCP Server implementation for Apache Sling.

## Usage

Build the project with Maven and start up the MCP server, based on the Apache Sling Starter:

```
$ mvn install feature-launcher:start feature-launcher:stop -Dfeature-launcher.waitForInput
```

Then build and deploy the [MCP server contributions bundle from the Sling Whiteboard](https://github.com/apache/sling-whiteboard/tree/master/mcp-server-contributions):

```
$ mvn -f whiteboard/mcp-server-contributions/ install sling:install 
```

Then open up your coding assistant tool and add an remote MCP server with location http://localhost:8080/mcp . Access is only
permitted for the `admin` user therefore basic authentication headers need to be specified. In case of the default credentials
the configuration can look as follows

```json
"aem-cs-sdk": {
  "type": "streamable-http",
  "url": "http://localhost:4502/bin/mcp",
  "headers": {
    "Authorization": "Basic YWRtaW46YWRtaW4="
  }
}
```

Please refer to the documentation of your coding assistant tool for details on how to add a remote MCP server and specify authentication headers.

## Legacy artifact

For applications still using the older slf4j 1.x and javax.servlet APIs a classifier with the 'legacy' classifier is built.
