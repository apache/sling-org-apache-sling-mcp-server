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
import java.util.ServiceLoader;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link LogSafeJsonSchemaValidator}.
 */
public class LogSafeJsonSchemaValidatorTest {

    private final JsonSchemaValidator validator =
            new LogSafeJsonSchemaValidator(ServiceLoader.load(JsonSchemaValidatorSupplier.class)
                    .findFirst()
                    .orElseThrow()
                    .get());

    /** Ensures argument names cannot forge lines in the SDK's warning log. */
    @Test
    public void escapesCallerControlledLineBreaks() {
        var result = validator.validate(
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                Map.of("unexpected\r\nFORGED", "value"));

        Assert.assertFalse(result.valid());
        Assert.assertFalse(result.errorMessage().contains("\r"));
        Assert.assertFalse(result.errorMessage().contains("\n"));
        Assert.assertTrue(result.errorMessage().contains("unexpected\\r\\nFORGED"));
    }

    /** Verifies that successful validation and structured output are unchanged. */
    @Test
    public void preservesSuccessfulValidation() {
        var result = validator.validate(Map.of("type", "object"), Map.of("value", 1));

        Assert.assertTrue(result.valid());
        Assert.assertNull(result.errorMessage());
    }

    /** Ensures the decorator does not lose the SDK's tool-schema registration checks. */
    @Test
    public void rejectsInvalidSchema() {
        Assert.assertFalse(validator
                .validateSchema(Map.of("type", "object", "required", true))
                .valid());
        Assert.assertTrue(validator.validateSchema(Map.of("type", "object")).valid());
    }
}
