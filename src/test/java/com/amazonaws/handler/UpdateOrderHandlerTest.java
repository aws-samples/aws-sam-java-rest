/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazonaws.handler;

import com.amazonaws.services.lambda.runtime.TestContext;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class UpdateOrderHandlerTest {
    private UpdateOrderHandler sut = new UpdateOrderHandler();

    @Test
    public void handleRequest_whenUpdateOrderInputStreamEmpty_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        sut.handleRequest(new ByteArrayInputStream(new byte[0]), os, TestContext.builder().build());
        assertTrue(os.toString().contains("order_id was not set"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamHasNoMappedOrderIdPathParam_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { }}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("order_id was not set"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamHasNoBody_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { \"order_id\" : \"a\" }}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("Body was null"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamHasNullBody_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { \"order_id\" : \"a\" }, \"body\": \"null\"}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("Request was null"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamHasWrongTypeForBody_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { \"order_id\" : \"a\" }, \"body\": \"1\"}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("Invalid JSON"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamHasEmptyBodyDict_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { \"order_id\" : \"a\" }, \"body\": \"{}\"}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("customerId was null"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamOnlyHasCustomer_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { \"order_id\" : \"a\" }, \"body\": \"{\\\"customerId\\\": \\\"customer\\\"}\"}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("preTaxAmount was null"));
        assertTrue(os.toString().contains("400"));
    }

    @Test
    public void handleRequest_whenUpdateOrderInputStreamDoesNotHavePostTaxAmount_puts400InOutputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String input = "{\"pathParameters\": { \"order_id\" : \"a\" }, \"body\": \"{\\\"customerId\\\": \\\"customer\\\", \\\"preTaxAmount\\\": 1}\"}";
        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, TestContext.builder().build());
        assertTrue(os.toString().contains("postTaxAmount was null"));
        assertTrue(os.toString().contains("400"));
    }
}
