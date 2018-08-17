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

import com.amazonaws.config.DaggerOrderComponent;
import com.amazonaws.config.OrderComponent;
import com.amazonaws.dao.OrderDao;
import com.amazonaws.exception.CouldNotCreateOrderException;
import com.amazonaws.model.Order;
import com.amazonaws.model.request.CreateOrderRequest;
import com.amazonaws.model.response.ErrorMessage;
import com.amazonaws.model.response.GatewayResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import javax.inject.Inject;

public class CreateOrderHandler implements OrderRequestStreamHandler {
    private static final ErrorMessage REQUIRE_CUSTOMER_ID_ERROR
            = new ErrorMessage("Require customerId to create an order", SC_BAD_REQUEST);
    private static final ErrorMessage REQUIRE_PRETAX_AMOUNT_ERROR
            = new ErrorMessage("Require preTaxAmount to create an order",
            SC_BAD_REQUEST);
    private static final ErrorMessage REQUIRE_POST_TAX_AMOUNT_ERROR
            = new ErrorMessage("Require postTaxAmount to create an order",
            SC_BAD_REQUEST);

    @Inject
    ObjectMapper objectMapper;
    @Inject
    OrderDao orderDao;
    private final OrderComponent orderComponent;

    public CreateOrderHandler() {
        orderComponent = DaggerOrderComponent.builder().build();
        orderComponent.inject(this);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output,
                              Context context) throws IOException {
        final JsonNode event;
        try {
            event = objectMapper.readTree(input);
        } catch (JsonMappingException e) {
            writeInvalidJsonInStreamResponse(objectMapper, output, e.getMessage());
            return;
        }

        if (event == null) {
            writeInvalidJsonInStreamResponse(objectMapper, output, "event was null");
            return;
        }
        JsonNode createOrderRequestBody = event.findValue("body");
        if (createOrderRequestBody == null) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(
                                    new ErrorMessage("Body was null",
                                            SC_BAD_REQUEST)),
                            APPLICATION_JSON, SC_BAD_REQUEST));
            return;
        }
        final CreateOrderRequest request;
        try {
            request = objectMapper.treeToValue(
                    objectMapper.readTree(createOrderRequestBody.asText()),
                    CreateOrderRequest.class);
        } catch (JsonParseException | JsonMappingException e) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(
                                    new ErrorMessage("Invalid JSON in body: "
                                            + e.getMessage(), SC_BAD_REQUEST)),
                            APPLICATION_JSON, SC_BAD_REQUEST));
            return;
        }

        if (request == null) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(REQUEST_WAS_NULL_ERROR),
                            APPLICATION_JSON, SC_BAD_REQUEST));
            return;
        }

        if (isNullOrEmpty(request.getCustomerId())) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(REQUIRE_CUSTOMER_ID_ERROR),
                            APPLICATION_JSON, SC_BAD_REQUEST));
            return;
        }
        if (request.getPreTaxAmount() == null) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(REQUIRE_PRETAX_AMOUNT_ERROR),
                            APPLICATION_JSON, SC_BAD_REQUEST));
            return;
        }
        if (request.getPostTaxAmount() == null) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(REQUIRE_POST_TAX_AMOUNT_ERROR),
                            APPLICATION_JSON, SC_BAD_REQUEST));
            return;
        }
        try {
            final Order order = orderDao.createOrder(request);
            objectMapper.writeValue(output,
                    new GatewayResponse<>(objectMapper.writeValueAsString(order),
                            APPLICATION_JSON, SC_CREATED)); //TODO redirect with a 303
        } catch (CouldNotCreateOrderException e) {
            objectMapper.writeValue(output,
                    new GatewayResponse<>(
                            objectMapper.writeValueAsString(
                                    new ErrorMessage(e.getMessage(),
                                            SC_INTERNAL_SERVER_ERROR)),
                            APPLICATION_JSON, SC_INTERNAL_SERVER_ERROR));
        }
    }
}
