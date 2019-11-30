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
import com.amazonaws.exception.UnableToDeleteException;
import com.amazonaws.exception.UnableToUpdateException;
import com.amazonaws.model.Order;
import com.amazonaws.model.OrderPage;
import com.amazonaws.model.request.CreateOrderRequest;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class OrderDaoTest {
    private static final String ORDER_ID = "some order id";
    private DynamoDbClient dynamoDb = mock(DynamoDbClient.class);
    private OrderDao sut = new OrderDao(dynamoDb, "table_name", 10);

    @Test(expected = IllegalArgumentException.class)
    public void createOrder_whenRequestNull_throwsIllegalArgumentException() {
        sut.createOrder(null);
    }

    //test CRUD when table does not exist
    @Test(expected = TableDoesNotExistException.class)
    public void createOrder_whenTableDoesNotExist_throwsTableDoesNotExistException() {
        doThrow(ResourceNotFoundException.builder().build()).when(dynamoDb).putItem(any(PutItemRequest.class));
        sut.createOrder(CreateOrderRequest.builder()
                .preTaxAmount(100L).postTaxAmount(109L).customerId("me").build());
    }

    @Test(expected = TableDoesNotExistException.class)
    public void getOrder_whenTableDoesNotExist_throwsTableDoesNotExistException() {
        doThrow(ResourceNotFoundException.builder().build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = TableDoesNotExistException.class)
    public void getOrders_whenTableDoesNotExist_throwsTableDoesNotExistException() {
        doThrow(ResourceNotFoundException.builder().build()).when(dynamoDb).scan(any(ScanRequest.class));
        sut.getOrders(any());
    }

    @Test
    public void getOrders_whenTableEmpty_returnsEmptyPage() {
        doReturn(ScanResponse.builder()
                .items(new ArrayList<>())
                .lastEvaluatedKey(null)
                .build()).when(dynamoDb).scan(any(ScanRequest.class));
        OrderPage page = sut.getOrders(any());
        assertNotNull(page);
        assertNotNull(page.getOrders());
        assertTrue(page.getOrders().isEmpty());
        assertNull(page.getLastEvaluatedKey());
    }

    @Test(expected = IllegalStateException.class)
    public void getOrders_whenTableNotEmpty_butLastEvaluatedKeyHasOrderIdSetToWrongType_throwsIllegalStateException() {
        doReturn(ScanResponse.builder()
                .items(Collections.singletonList(new HashMap<>()))
                .lastEvaluatedKey(Collections.singletonMap("orderId", AttributeValue.builder().nul(true).build()))
                .build()
        ).when(dynamoDb).scan(any(ScanRequest.class));
        sut.getOrders(any());
    }

    @Test(expected = IllegalStateException.class)
    public void getOrders_whenTableNotEmpty_butLastEvaluatedKeyHasOrderIdSetToUnsetAv_throwsIllegalStateException() {
        doReturn(ScanResponse.builder()
                .items(Collections.singletonList(new HashMap<>()))
                .lastEvaluatedKey(Collections.singletonMap("orderId", AttributeValue.builder().build()))
                .build()
        ).when(dynamoDb).scan(any(ScanRequest.class));
        sut.getOrders(any());
    }

    @Test(expected = IllegalStateException.class)
    public void getOrders_whenTableNotEmpty_butLastEvaluatedKeyHasOrderIdSetToEmptyString_throwsIllegalStateException() {
        doReturn(ScanResponse.builder()
                .items(Collections.singletonList(new HashMap<>()))
                .lastEvaluatedKey(Collections.singletonMap("orderId", AttributeValue.builder().s("").build()))
                .build()
        ).when(dynamoDb).scan(any(ScanRequest.class));
        sut.getOrders(any());
    }

    @Test
    public void getOrders_whenTableNotEmpty_returnsPage() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("orderId", AttributeValue.builder().s("d").build());
        item.put("customerId", AttributeValue.builder().s("d").build());
        item.put("preTaxAmount", AttributeValue.builder().n("1").build());
        item.put("postTaxAmount", AttributeValue.builder().n("10").build());
        item.put("version", AttributeValue.builder().n("1").build());
        doReturn(ScanResponse.builder()
                .items(Collections.singletonList(item))
                .lastEvaluatedKey(Collections.singletonMap("orderId", AttributeValue.builder().s("d").build()))
                .build()).when(dynamoDb).scan(any(ScanRequest.class));
        sut.getOrders(any());
    }

    @Test(expected = TableDoesNotExistException.class)
    public void updateOrder_whenTableDoesNotExistOnLoadItem_throwsTableDoesNotExistException() {
        doThrow(ResourceNotFoundException.builder().build()).when(dynamoDb).updateItem(any(UpdateItemRequest.class));
        sut.updateOrder(Order.builder()
                .orderId(ORDER_ID)
                .customerId("customer")
                .preTaxAmount(BigDecimal.ONE)
                .postTaxAmount(BigDecimal.TEN)
                .version(0L)
                .build());
    }

    @Test(expected = TableDoesNotExistException.class)
    public void updateOrder_whenTableDoesNotExist_throwsTableDoesNotExistException() {
        doThrow(ResourceNotFoundException.builder().build()).when(dynamoDb).updateItem(any(UpdateItemRequest.class));
        sut.updateOrder(Order.builder()
                .orderId(ORDER_ID)
                .customerId("customer")
                .preTaxAmount(BigDecimal.ONE)
                .postTaxAmount(BigDecimal.TEN)
                .version(1L)
                .build());
    }

    @Test(expected = TableDoesNotExistException.class)
    public void deleteOrder_whenTableDoesNotExist_throwsTableDoesNotExistException() {
        Map<String, AttributeValue> orderItem = new HashMap<>();
        orderItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
        orderItem.put("version", AttributeValue.builder().n("1").build());
        doReturn(GetItemResponse.builder().item(orderItem).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        doThrow(ResourceNotFoundException.builder().build()).when(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        sut.deleteOrder(ORDER_ID);
    }

    //conditional failure tests
    @Test(expected = CouldNotCreateOrderException.class)
    public void createOrder_whenAlreadyExists_throwsCouldNotCreateOrderException() {
        doThrow(ConditionalCheckFailedException.builder().build()).when(dynamoDb).putItem(any(PutItemRequest.class));
        sut.createOrder(CreateOrderRequest.builder()
                .preTaxAmount(100L).postTaxAmount(109L).customerId("me").build());
    }

    @Test(expected = UnableToDeleteException.class)
    public void deleteOrder_whenVersionMismatch_throwsUnableToDeleteException() {
        doThrow(ConditionalCheckFailedException.builder().build())
                .when(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        sut.deleteOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void deleteOrder_whenDeleteItemReturnsNull_throwsIllegalStateException() {
        doReturn(null).when(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        sut.deleteOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void deleteOrder_whenDeleteItemReturnsNoAttributes_throwsIllegalStateException() {
        doReturn(DeleteItemResponse.builder().build())
                .when(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        sut.deleteOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void deleteOrder_whenDeleteItemReturnsEmptyAttributes_throwsIllegalStateException() {
        doReturn(DeleteItemResponse.builder().attributes(new HashMap<>()).build())
                .when(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        sut.deleteOrder(ORDER_ID);
    }

    @Test
    public void deleteOrder_whenDeleteItemReturnsOkOrderItem_returnsDeletedOrder() {
        Map<String, AttributeValue> orderItem = new HashMap<>();
        orderItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
        orderItem.put("customerId", AttributeValue.builder().s("customer").build());
        orderItem.put("preTaxAmount", AttributeValue.builder().n("1").build());
        orderItem.put("postTaxAmount", AttributeValue.builder().n("10").build());
        orderItem.put("version", AttributeValue.builder().n("1").build());
        doReturn(DeleteItemResponse.builder().attributes(orderItem).build())
                .when(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        Order deleted = sut.deleteOrder(ORDER_ID);
        assertNotNull(deleted);
    }

    @Test(expected = UnableToUpdateException.class)
    public void updateOrder_whenVersionMismatch_throwsUnableToUpdateException() {
        Map<String, AttributeValue> orderItem = new HashMap<>();
        orderItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
        orderItem.put("version", AttributeValue.builder().n("0").build());
        doReturn(GetItemResponse.builder().item(orderItem).build())
                .when(dynamoDb).getItem(any(GetItemRequest.class));
        Order postOrder = new Order();
        postOrder.setOrderId(ORDER_ID);
        postOrder.setVersion(0L);
        postOrder.setCustomerId("customer");
        postOrder.setPreTaxAmount(BigDecimal.ONE);
        postOrder.setPostTaxAmount(BigDecimal.TEN);
        doThrow(ConditionalCheckFailedException.builder().build())
                .when(dynamoDb).updateItem(any(UpdateItemRequest.class));
        sut.updateOrder(postOrder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenOrderIsNull_throwsIllegalArgumentException() {
        sut.updateOrder(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenAllNotSet_throwsIllegalArgumentException() {
        Order postOrder = new Order();
        sut.updateOrder(postOrder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenOrderIdSetButEmpty_throwsIllegalArgumentException() {
        Order postOrder = new Order();
        postOrder.setOrderId("");
        sut.updateOrder(postOrder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenCustomerIdSetButEmpty_throwsIllegalArgumentException() {
        Order postOrder = new Order();
        postOrder.setOrderId("s");
        postOrder.setCustomerId("");
        sut.updateOrder(postOrder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenPreTaxAmountNull_throwsIllegalArgumentException() {
        Order postOrder = new Order();
        postOrder.setOrderId("s");
        postOrder.setCustomerId("c");
        postOrder.setPreTaxAmount(null);
        postOrder.setPostTaxAmount(BigDecimal.TEN);
        postOrder.setVersion(1L);
        sut.updateOrder(postOrder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenPostTaxAmountNull_throwsIllegalArgumentException() {
        Order postOrder = new Order();
        postOrder.setOrderId("s");
        postOrder.setCustomerId("c");
        postOrder.setPreTaxAmount(BigDecimal.ONE);
        postOrder.setPostTaxAmount(null);
        postOrder.setVersion(1L);
        sut.updateOrder(postOrder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateOrder_whenVersionNull_throwsIllegalArgumentException() {
        Order postOrder = new Order();
        postOrder.setOrderId("s");
        postOrder.setCustomerId("c");
        postOrder.setPreTaxAmount(BigDecimal.ONE);
        postOrder.setPostTaxAmount(BigDecimal.TEN);
        postOrder.setVersion(null);
        sut.updateOrder(postOrder);
    }

    @Test
    public void updateOrder_whenAllSet_returnsUpdate() {
        Map<String, AttributeValue> createdItem = new HashMap<>();
        createdItem.put("orderId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        createdItem.put("customerId", AttributeValue.builder().s("customer").build());
        createdItem.put("preTaxAmount", AttributeValue.builder().n("1").build());
        createdItem.put("postTaxAmount", AttributeValue.builder().n("10").build());
        createdItem.put("version", AttributeValue.builder().n("1").build());

        doReturn(UpdateItemResponse.builder().attributes(createdItem).build())
                .when(dynamoDb).updateItem(any(UpdateItemRequest.class));

        Order postOrder = new Order();
        postOrder.setOrderId(createdItem.get("orderId").s());
        postOrder.setCustomerId("customer");
        postOrder.setPreTaxAmount(BigDecimal.ONE);
        postOrder.setPostTaxAmount(BigDecimal.TEN);
        postOrder.setVersion(1L);
        Order order = sut.updateOrder(postOrder);
        assertEquals(createdItem.get("orderId").s(), order.getOrderId());
    }

    //positive functional tests
    @Test
    public void createOrder_whenOrderDoesNotExist_createsOrderWithPopulatedOrderId() {
        Map<String, AttributeValue> createdItem = new HashMap<>();
        createdItem.put("orderId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        createdItem.put("customerId", AttributeValue.builder().s("customer").build());
        createdItem.put("preTaxAmount", AttributeValue.builder().n("1").build());
        createdItem.put("postTaxAmount", AttributeValue.builder().n("10").build());
        createdItem.put("version", AttributeValue.builder().n("1").build());
        doReturn(PutItemResponse.builder().attributes(createdItem).build()).when(dynamoDb).putItem(any(PutItemRequest.class));

        Order order = sut.createOrder(CreateOrderRequest.builder()
                .customerId("customer")
                .preTaxAmount(1L)
                .postTaxAmount(10L).build());
        assertNotNull(order.getVersion());
        //for a new item, object mapper sets version to 1
        assertEquals(1L, order.getVersion().longValue());
        assertEquals("customer", order.getCustomerId());
        assertEquals(BigDecimal.ONE, order.getPreTaxAmount());
        assertEquals(BigDecimal.TEN, order.getPostTaxAmount());
        assertNotNull(order.getOrderId());
        assertNotNull(UUID.fromString(order.getOrderId()));
    }

    @Test(expected = OrderDoesNotExistException.class)
    public void getOrder_whenOrderDoesNotExist_throwsOrderDoesNotExist() {
        doReturn(GetItemResponse.builder().item(null).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = OrderDoesNotExistException.class)
    public void getOrder_whenGetItemReturnsEmptyHashMap_throwsIllegalStateException() {
        doReturn(GetItemResponse.builder().item(new HashMap<>()).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithOrderIdWrongType_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().nul(true).build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithUnsetOrderIdAV_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithEmptyOrderId_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("").build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithCustomerIdWrongType_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().nul(true).build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithUnsetCustomerIdAV_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithEmptyCustomerId_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("").build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithPreTaxWrongType_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().nul(true).build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithUnsetPreTaxAV_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithInvalidPreTax_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("a").build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithPostTaxWrongType_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("1").build());
        map.put("postTaxAmount", AttributeValue.builder().nul(true).build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithUnsetPostTaxAV_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("1").build());
        map.put("postTaxAmount", AttributeValue.builder().build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithInvalidPostTax_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("1").build());
        map.put("postTaxAmount", AttributeValue.builder().n("a").build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithVersionOfWrongType_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("1").build());
        map.put("postTaxAmount", AttributeValue.builder().n("10").build());
        map.put("version", AttributeValue.builder().ss("").build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithUnsetVersionAV_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("1").build());
        map.put("postTaxAmount", AttributeValue.builder().n("10").build());
        map.put("version", AttributeValue.builder().build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void getOrder_whenGetItemReturnsHashMapWithInvalidVersion_throwsIllegalStateException() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("orderId", AttributeValue.builder().s("a").build());
        map.put("customerId", AttributeValue.builder().s("a").build());
        map.put("preTaxAmount", AttributeValue.builder().n("1").build());
        map.put("postTaxAmount", AttributeValue.builder().n("10").build());
        map.put("version", AttributeValue.builder().n("a").build());
        doReturn(GetItemResponse.builder().item(map).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        sut.getOrder(ORDER_ID);
    }

    @Test
    public void getOrder_whenOrderExists_returnsOrder() {
        Map<String, AttributeValue> orderItem = new HashMap<>();
        orderItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
        orderItem.put("version", AttributeValue.builder().n("1").build());
        orderItem.put("preTaxAmount", AttributeValue.builder().n("1").build());
        orderItem.put("postTaxAmount", AttributeValue.builder().n("10").build());
        orderItem.put("customerId", AttributeValue.builder().s("customer").build());
        doReturn(GetItemResponse.builder().item(orderItem).build()).when(dynamoDb).getItem(any(GetItemRequest.class));
        Order order = sut.getOrder(ORDER_ID);
        assertEquals(ORDER_ID, order.getOrderId());
        assertEquals(1L, order.getVersion().longValue());
        assertEquals(1L, order.getPreTaxAmount().longValue());
        assertEquals(10L, order.getPostTaxAmount().longValue());
        assertEquals("customer", order.getCustomerId());
    }

    //connection dropped corner cases
}
