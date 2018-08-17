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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.TestContext;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

public class CreateOrderHandlerIT extends OrderHandlerTestBase {

    private CreateOrderHandler sut = new CreateOrderHandler();
    private GetOrderHandler getOrder = new GetOrderHandler();
    private GetOrdersHandler getOrders = new GetOrdersHandler();
    private UpdateOrderHandler updateOrder = new UpdateOrderHandler();

    @Test
    public void handleRequest_whenCreateOrderInputStreamOk_puts200InOutputStream() throws IOException {
        Context ctxt = TestContext.builder().build();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String input = "{\"body\": \"{\\\"customerId\\\": \\\"foo\\\", \\\"preTaxAmount\\\": 3, \\\"postTaxAmount\\\": 10}\"}";

        sut.handleRequest(new ByteArrayInputStream(input.getBytes()), os, ctxt);
        Item outputWrapper = Item.fromJSON(os.toString());
        assertTrue(outputWrapper.hasAttribute("headers"));
        Map<String, Object> headers = outputWrapper.getMap("headers");
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertTrue(headers.containsKey("Content-Type"));
        assertEquals("application/json", headers.get("Content-Type"));
        assertTrue(outputWrapper.hasAttribute("statusCode"));
        assertEquals(201, outputWrapper.getInt("statusCode"));
        assertTrue(outputWrapper.hasAttribute("body"));
        String bodyString = outputWrapper.getString("body");
        assertNotNull(bodyString);
        Item body = Item.fromJSON(bodyString);
        verifyOrderItem(body, 1, "3");

        //now that we verified the created order, lets see if we can get it anew
        os = new ByteArrayOutputStream();
        String orderId = body.getString("orderId");

        getOrder.handleRequest(new ByteArrayInputStream(("{\"pathParameters\": { \"order_id\": \"" + orderId + "\"}}").getBytes()), os, ctxt);

        outputWrapper = Item.fromJSON(os.toString());
        assertTrue(outputWrapper.hasAttribute("headers"));
        headers = outputWrapper.getMap("headers");
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertTrue(headers.containsKey("Content-Type"));
        assertEquals("application/json", headers.get("Content-Type"));
        assertTrue(outputWrapper.hasAttribute("statusCode"));
        assertEquals(200, outputWrapper.getInt("statusCode"));
        assertTrue(outputWrapper.hasAttribute("body"));
        bodyString = outputWrapper.getString("body");
        assertNotNull(bodyString);
        body = Item.fromJSON(bodyString);
        verifyOrderItem(body, 1, "3");

        //now that we can get the singleton lets see if we can get it in a page
        os = new ByteArrayOutputStream();
        getOrders.handleRequest(new ByteArrayInputStream("{}".getBytes()), os, ctxt);
        assertTrue(os.toString().contains(orderId));

        //update the order with invalid arguments (try to change the version from 1 to 2 and the preTaxAmount from 3 to 4)
        os = new ByteArrayOutputStream();
        updateOrder.handleRequest(
                new ByteArrayInputStream(("{\"pathParameters\": { \"order_id\": \"" + orderId + "\"}, "
                        + "\"body\": \"{\\\"customerId\\\": \\\"foo\\\", \\\"preTaxAmount\\\": 4, \\\"postTaxAmount\\\": 10, \\\"version\\\": 2}\"}").getBytes()),
                os, ctxt);
        assertTrue(os.toString().contains("409")); //SC_CONFLICT

        //update the order with invalid arguments (try to change the pretax amount from 3 to 4)
        os = new ByteArrayOutputStream();
        updateOrder.handleRequest(
                new ByteArrayInputStream(("{\"pathParameters\": { \"order_id\": \"" + orderId + "\"}, "
                        + "\"body\": \"{\\\"customerId\\\": \\\"foo\\\", \\\"preTaxAmount\\\": 4, \\\"postTaxAmount\\\": 10, \\\"version\\\": 1}\"}").getBytes()),
                os, ctxt);
        outputWrapper = Item.fromJSON(os.toString());
        assertTrue(outputWrapper.hasAttribute("headers"));
        headers = outputWrapper.getMap("headers");
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertTrue(headers.containsKey("Content-Type"));
        assertEquals("application/json", headers.get("Content-Type"));
        assertTrue(outputWrapper.hasAttribute("statusCode"));
        assertEquals(200, outputWrapper.getInt("statusCode"));
        assertTrue(outputWrapper.hasAttribute("body"));
        bodyString = outputWrapper.getString("body");
        assertNotNull(bodyString);
        body = Item.fromJSON(bodyString);
        verifyOrderItem(body, 2, "4");

        assertTrue(os.toString().contains("200")); //SC_OK
    }

    private void verifyOrderItem(Item body, long expectedVersion, String expectedPreTaxAmount) {
        assertTrue(body.hasAttribute("orderId"));
        String orderId = body.getString("orderId");
        assertNotNull(orderId);
        assertTrue(orderId.contains("-"));
        assertTrue(body.hasAttribute("customerId"));
        String customerId = body.getString("customerId");
        assertEquals("foo", customerId);
        assertTrue(body.hasAttribute("preTaxAmount"));
        BigDecimal preTaxAmount = body.getNumber("preTaxAmount");
        assertEquals(new BigDecimal(expectedPreTaxAmount), preTaxAmount);
        assertTrue(body.hasAttribute("postTaxAmount"));
        BigDecimal postTaxAmount = body.getNumber("postTaxAmount");
        assertEquals(new BigDecimal("10"), postTaxAmount);
        assertTrue(body.hasAttribute("version"));
        long version = body.getLong("version");
        assertEquals(expectedVersion, version);
    }
}
