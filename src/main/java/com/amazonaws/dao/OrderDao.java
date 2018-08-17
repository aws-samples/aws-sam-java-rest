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

package com.amazonaws.dao;

import com.amazonaws.exception.CouldNotCreateOrderException;
import com.amazonaws.exception.OrderDoesNotExistException;
import com.amazonaws.exception.TableDoesNotExistException;
import com.amazonaws.exception.TableExistsException;
import com.amazonaws.exception.UnableToDeleteException;
import com.amazonaws.exception.UnableToUpdateException;
import com.amazonaws.model.Order;
import com.amazonaws.model.OrderPage;
import com.amazonaws.model.request.CreateOrderRequest;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrderDao {

    private static final String UPDATE_EXPRESSION
            = "SET customerId = :cid, preTaxAmount = :pre, postTaxAmount = :post ADD version :o";
    private static final String ORDER_ID = "orderId";
    private static final String PRE_TAX_AMOUNT_WAS_NULL = "preTaxAmount was null";
    private static final String POST_TAX_AMOUNT_WAS_NULL = "postTaxAmount was null";
    private static final String VERSION_WAS_NULL = "version was null";

    private final String tableName;
    private final DynamoDbClient dynamoDb;
    private final int pageSize;

    /**
     * Constructs an OrderDao.
     * @param dynamoDb dynamodb client
     * @param tableName name of table to use for orders
     * @param pageSize size of pages for getOrders
     */
    public OrderDao(final DynamoDbClient dynamoDb, final String tableName,
                    final int pageSize) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
        this.pageSize = pageSize;
    }

    /**
     * Creates an orders table in DynamoDB.
     */
    public void createOrdersTable() {
        try {
            dynamoDb.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L)
                            .build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName(ORDER_ID)
                            .keyType(KeyType.HASH)
                            .build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(ORDER_ID)
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .build());
        } catch (ResourceInUseException e) {
            throw new TableExistsException("Orders table already exists");
        }
    }

    /**
     * Returns an order or throws if the order does not exist.
     * @param orderId id of order to get
     * @return the order if it exists
     * @throws OrderDoesNotExistException if the order does not exist
     */
    public Order getOrder(final String orderId) {
        try {
            return Optional.ofNullable(
                    dynamoDb.getItem(GetItemRequest.builder()
                            .tableName(tableName)
                            .key(Collections.singletonMap(ORDER_ID,
                                    AttributeValue.builder().s(orderId).build()))
                            .build()))
                    .map(GetItemResponse::item)
                    .map(this::convert)
                    .orElseThrow(() -> new OrderDoesNotExistException("Order "
                            + orderId + " does not exist"));
        } catch (ResourceNotFoundException e) {
            throw new TableDoesNotExistException("Order table " + tableName + " does not exist");
        }
    }

    /**
     * Gets a page of orders, at most pageSize long.
     * @param exclusiveStartOrderId the exclusive start id for the next page.
     * @return a page of orders.
     * @throws TableDoesNotExistException if the order table does not exist
     */
    public OrderPage getOrders(final String exclusiveStartOrderId) {
        final ScanResponse result;

        try {
            ScanRequest.Builder scanBuilder = ScanRequest.builder()
                    .tableName(tableName)
                    .limit(pageSize);
            if (!isNullOrEmpty(exclusiveStartOrderId)) {
                scanBuilder.exclusiveStartKey(Collections.singletonMap(ORDER_ID,
                        AttributeValue.builder().s(exclusiveStartOrderId).build()));
            }
            result = dynamoDb.scan(scanBuilder.build());
        } catch (ResourceNotFoundException e) {
            throw new TableDoesNotExistException("Order table " + tableName
                    + " does not exist");
        }

        final List<Order> orders = result.items().stream()
                .map(this::convert)
                .collect(Collectors.toList());

        OrderPage.OrderPageBuilder builder = OrderPage.builder().orders(orders);
        if (result.lastEvaluatedKey() != null && !result.lastEvaluatedKey().isEmpty()) {
            if ((!result.lastEvaluatedKey().containsKey(ORDER_ID)
                    || isNullOrEmpty(result.lastEvaluatedKey().get(ORDER_ID).s()))) {
                throw new IllegalStateException(
                    "orderId did not exist or was not a non-empty string in the lastEvaluatedKey");
            } else {
                builder.lastEvaluatedKey(result.lastEvaluatedKey().get(ORDER_ID).s());
            }
        }

        return builder.build();
    }

    /**
     * Updates an order object.
     * @param order order to update
     * @return updated order
     */
    public Order updateOrder(final Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order to update was null");
        }
        String orderId = order.getOrderId();
        if (isNullOrEmpty(orderId)) {
            throw new IllegalArgumentException("orderId was null or empty");
        }
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":cid",
                AttributeValue.builder().s(validateCustomerId(order.getCustomerId())).build());

        try {
            expressionAttributeValues.put(":pre",
                    AttributeValue.builder().n(order.getPreTaxAmount().toString()).build());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(PRE_TAX_AMOUNT_WAS_NULL);
        }
        try {
            expressionAttributeValues.put(":post",
                    AttributeValue.builder().n(order.getPostTaxAmount().toString()).build());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(POST_TAX_AMOUNT_WAS_NULL);
        }
        expressionAttributeValues.put(":o", AttributeValue.builder().n("1").build());
        try {
            expressionAttributeValues.put(":v",
                    AttributeValue.builder().n(order.getVersion().toString()).build());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(VERSION_WAS_NULL);
        }
        final UpdateItemResponse result;
        try {
            result = dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Collections.singletonMap(ORDER_ID,
                            AttributeValue.builder().s(order.getOrderId()).build()))
                    .returnValues(ReturnValue.ALL_NEW)
                    .updateExpression(UPDATE_EXPRESSION)
                    .conditionExpression("attribute_exists(orderId) AND version = :v")
                    .expressionAttributeValues(expressionAttributeValues)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new UnableToUpdateException(
                    "Either the order did not exist or the provided version was not current");
        } catch (ResourceNotFoundException e) {
            throw new TableDoesNotExistException("Order table " + tableName
                    + " does not exist and was deleted after reading the order");
        }
        return convert(result.attributes());
    }

    /**
     * Deletes an order.
     * @param orderId order id of order to delete
     * @return the deleted order
     */
    public Order deleteOrder(final String orderId) {
        final DeleteItemResponse result;
        try {
            return Optional.ofNullable(dynamoDb.deleteItem(DeleteItemRequest.builder()
                            .tableName(tableName)
                            .key(Collections.singletonMap(ORDER_ID,
                                    AttributeValue.builder().s(orderId).build()))
                            .conditionExpression("attribute_exists(orderId)")
                            .returnValues(ReturnValue.ALL_OLD)
                            .build()))
                    .map(DeleteItemResponse::attributes)
                    .map(this::convert)
                    .orElseThrow(() -> new IllegalStateException(
                            "Condition passed but deleted item was null"));
        } catch (ConditionalCheckFailedException e) {
            throw new UnableToDeleteException(
                    "A competing request changed the order while processing this request");
        } catch (ResourceNotFoundException e) {
            throw new TableDoesNotExistException("Order table " + tableName
                    + " does not exist and was deleted after reading the order");
        }
    }

    private Order convert(final Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        Order.OrderBuilder builder = Order.builder();

        try {
            builder.orderId(item.get(ORDER_ID).s());
        } catch (NullPointerException e) {
            throw new IllegalStateException(
                    "item did not have an orderId attribute or it was not a String");
        }

        try {
            builder.customerId(item.get("customerId").s());
        } catch (NullPointerException e) {
            throw new IllegalStateException(
                    "item did not have an customerId attribute or it was not a String");
        }

        try {
            builder.preTaxAmount(new BigDecimal(item.get("preTaxAmount").n()));
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalStateException(
                    "item did not have an preTaxAmount attribute or it was not a Number");
        }

        try {
            builder.postTaxAmount(new BigDecimal(item.get("postTaxAmount").n()));
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalStateException(
                    "item did not have an postTaxAmount attribute or it was not a Number");
        }

        try {
            builder.version(Long.valueOf(item.get("version").n()));
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalStateException(
                    "item did not have an version attribute or it was not a Number");
        }

        return builder.build();
    }

    private Map<String, AttributeValue> createOrderItem(final CreateOrderRequest order) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ORDER_ID, AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        item.put("version", AttributeValue.builder().n("1").build());
        item.put("customerId",
                AttributeValue.builder().s(validateCustomerId(order.getCustomerId())).build());
        try {
            item.put("preTaxAmount",
                    AttributeValue.builder().n(order.getPreTaxAmount().toString()).build());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(PRE_TAX_AMOUNT_WAS_NULL);
        }
        try {
            item.put("postTaxAmount",
                    AttributeValue.builder().n(order.getPostTaxAmount().toString()).build());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(POST_TAX_AMOUNT_WAS_NULL);
        }

        return item;
    }

    private String validateCustomerId(final String customerId) {
        if (isNullOrEmpty(customerId)) {
            throw new IllegalArgumentException("customerId was null or empty");
        }
        return customerId;
    }

    /**
     * Creates an order.
     * @param createOrderRequest details of order to create
     * @return created order
     */
    public Order createOrder(final CreateOrderRequest createOrderRequest) {
        if (createOrderRequest == null) {
            throw new IllegalArgumentException("CreateOrderRequest was null");
        }
        int tries = 0;
        while (tries < 10) {
            try {
                Map<String, AttributeValue> item = createOrderItem(createOrderRequest);
                dynamoDb.putItem(PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .conditionExpression("attribute_not_exists(orderId)")
                        .build());
                return Order.builder()
                        .orderId(item.get(ORDER_ID).s())
                        .customerId(item.get("customerId").s())
                        .preTaxAmount(new BigDecimal(item.get("preTaxAmount").n()))
                        .postTaxAmount(new BigDecimal(item.get("postTaxAmount").n()))
                        .version(Long.valueOf(item.get("version").n()))
                        .build();
            } catch (ConditionalCheckFailedException e) {
                tries++;
            } catch (ResourceNotFoundException e) {
                throw new TableDoesNotExistException(
                        "Order table " + tableName + " does not exist");
            }
        }
        throw new CouldNotCreateOrderException(
                "Unable to generate unique order id after 10 tries");
    }

    private static boolean isNullOrEmpty(final String string) {
        return string == null || string.isEmpty();
    }
}

