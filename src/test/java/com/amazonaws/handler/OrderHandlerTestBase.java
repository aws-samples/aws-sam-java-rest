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

import com.amazonaws.config.DaggerOrderTestComponent;
import com.amazonaws.config.OrderTestComponent;
import org.junit.After;
import org.junit.Before;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import javax.inject.Inject;

/**
 * This class serves as the base class for Integration tests. do not include I T in
 * the class name so that it does not get picked up by failsafe.
 */
public abstract class OrderHandlerTestBase {
    private static final String TABLE_NAME = "orders_table";

    private final OrderTestComponent orderComponent;

    @Inject
    DynamoDbClient dynamoDb;

    public OrderHandlerTestBase() {
        orderComponent = DaggerOrderTestComponent.builder().build();
        orderComponent.inject(this);
    }

    @Before
    public void setup() {
        dynamoDb.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .keySchema(KeySchemaElement.builder()
                        .keyType(KeyType.HASH)
                        .attributeName("orderId")
                        .build())
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName("orderId")
                                .attributeType(ScalarAttributeType.S)
                                .build())
                .provisionedThroughput(
                        ProvisionedThroughput.builder()
                                .readCapacityUnits(1L)
                                .writeCapacityUnits(1L)
                                .build())
                .build());

    }

    @After
    public void teardown() {
        dynamoDb.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build());
    }
}
