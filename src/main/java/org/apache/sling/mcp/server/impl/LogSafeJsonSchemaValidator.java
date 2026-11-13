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

import java.util.Map;
import java.util.Objects;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;

/**
 * Escapes caller-controlled line breaks before the SDK logs validation diagnostics.
 */
record LogSafeJsonSchemaValidator(JsonSchemaValidator delegate) implements JsonSchemaValidator {

    LogSafeJsonSchemaValidator {
        Objects.requireNonNull(delegate, "JSON schema validator must not be null");
    }

    /**
     * Preserves input validation while keeping its diagnostic on one log line.
     */
    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
        return escapeDiagnostic(delegate.validate(schema, structuredContent));
    }

    /**
     * Retains meta-schema checks when validating contributed tool schemas.
     */
    @Override
    public ValidationResponse validateSchema(Map<String, Object> schema) {
        return escapeDiagnostic(delegate.validateSchema(schema));
    }

    private static ValidationResponse escapeDiagnostic(ValidationResponse response) {
        if (response.errorMessage() == null) {
            return response;
        }
        return new ValidationResponse(
                response.valid(),
                response.errorMessage().replace("\r", "\\r").replace("\n", "\\n"),
                response.jsonStructuredOutput());
    }
}
