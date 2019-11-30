# SAM DynamoDB Application for Managing Orders

This is a sample application to demonstrate how to build an application on DynamoDB using the
DynamoDBMapper ORM framework to map Order items in a DynamoDB table to a RESTful API for order
management.

```bash
.
├── README.md                               <-- This instructions file
├── LICENSE.txt                             <-- Apache Software License 2.0
├── NOTICE.txt                              <-- Copyright notices
├── pom.xml                                 <-- Java dependencies, Docker integration test orchestration
├── src
│   ├── main
│   │   └── java
│   │       ├── com.amazonaws.config              <-- Classes to manage Dagger 2 dependency injection
│   │       │   ├── OrderComponent.java           <-- Contains inject methods for handler entrypoints
│   │       │   └── OrderModule.java              <-- Provides dependencies like the DynamoDB client for injection
│   │       ├── com.amazonaws.dao                 <-- Package for DAO objects
│   │       │   └── OrderDao.java                 <-- DAO Wrapper around the DynamoDBTableMapper for Orders
│   │       ├── com.amazonaws.exception           <-- Source code for custom exceptions
│   │       ├── com.amazonaws.handler             <-- Source code for lambda functions
│   │       │   ├── CreateOrderHandler.java       <-- Lambda function code for creating orders
│   │       │   ├── CreateOrdersTableHandler.java <-- Lambda function code for creating the orders table
│   │       │   ├── DeleteOrderHandler.java       <-- Lambda function code for deleting orders
│   │       │   ├── GetOrderHandler.java          <-- Lambda function code for getting one order
│   │       │   ├── GetOrdersHandler.java         <-- Lambda function code for getting a page of orders
│   │       │   └── UpdateOrderHandler.java       <-- Lambda function code for updating an order
│   │       └── com.amazonaws.model               <-- Source code for model classes
│   │           ├── request                       <-- Source code for request model classes
│   │           │   ├── CreateOrderRequest.java      <-- POJO shape for creating an order
│   │           │   ├── GetOrDeleteOrderRequest.java <-- POJO shape for getting or deleting an order
│   │           │   ├── GetOrdersRequest.java        <-- POJO shape for getting a page of orders
│   │           │   └── UpdateOrderRequest.java      <-- POJO shape for updating an order
│   │           ├── response                      <-- Source code for response model classes
│   │           │   ├── GatewayResponse.java         <-- Generic POJO shape for the APIGateway integration
│   │           │   └── GetOrdersResponse.java       <-- POJO shape for a page of orders
│   │           └── Order.java                    <-- POJO for Order resources
│   └── test                                      <-- Unit and integration tests
│       └── java
│           ├── com.amazonaws.config              <-- Classes to manage Dagger 2 dependency injection
│           ├── com.amazonaws.dao                 <-- Tests for OrderDao
│           ├── com.amazonaws.handler             <-- Unit and integration tests for handlers
│           │   ├── CreateOrderHandlerIT.java     <-- Integration tests for creating orders
│           │   ├── CreateOrderHandlerTest.java   <-- Unit tests for creating orders
│           │   ├── DeleteOrderHandlerTest.java   <-- Unit tests for deleting orders
│           │   ├── GetOrderHandlerTest.java      <-- Unit tests for getting one order
│           │   ├── GetOrdersHandlerTest.java     <-- Unit tests for getting a page of orders
│           │   └── UpdateOrderHandlerTest.java   <-- Unit tests for updating an order
│           └── com.amazonaws.services.lambda.runtime <-- Unit and integration tests for handlers
│               └── TestContext.java              <-- Context implementation for use in tests
└── template.yaml                                 <-- Contains SAM API Gateway + Lambda definitions
```

## Requirements

* AWS CLI already configured with at least PowerUser permission
* [Java SE Development Kit 8 installed](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [Docker installed](https://www.docker.com/community-edition)
* [Maven](https://maven.apache.org/install.html)
* [SAM CLI](https://github.com/awslabs/aws-sam-cli)
* [Python 3](https://docs.python.org/3/)

## Setup process

### Installing dependencies

We use `maven` to install our dependencies and package our application into a JAR file:

```bash
mvn package
```

### Local development

**Invoking function locally through local API Gateway**
1. Start DynamoDB Local in a Docker container. `docker-compose up`
2. Start the SAM local API.
 - On a Mac: `sam local start-api --env-vars src/test/resources/test_environment_mac.json`.
 - On Windows: `sam local start-api --env-vars src/test/resources/test_environment_windows.json`
 - On Linux: `sam local start-api --env-vars src/test/resources/test_environment_linux.json`

If the previous command ran successfully you should now be able to hit the following local endpoint to
invoke the functions rooted at `http://localhost:3000/orders`

**SAM CLI** is used to emulate both Lambda and API Gateway locally and uses our `template.yaml` to
understand how to bootstrap this environment (runtime, where the source code is, etc.) - The
following excerpt is what the CLI will read in order to initialize an API and its routes:

```yaml
...
Events:
    GetOrders:
        Type: Api # More info about API Event Source: https://github.com/awslabs/serverless-application-model/blob/master/versions/2016-10-31.md#api
        Properties:
            Path: /orders
            Method: get
```

## Packaging and deployment

AWS Lambda Java runtime accepts either a zip file or a standalone JAR file - We use the latter in
this example. SAM will use `CodeUri` property to know where to look up for both application and
dependencies:

```yaml
...
    GetOrdersFunction:
        Type: AWS::Serverless::Function
        Properties:
            CodeUri: target/aws-sam-java-rest-1.0.0.jar
            Handler: com.amazonaws.handler.GetOrdersHandler::handleRequest
```

Firstly, we need a `S3 bucket` where we can upload our Lambda functions packaged as ZIP before we
deploy anything - If you don't have a S3 bucket to store code artifacts then this is a good time to
create one:

```bash
export BUCKET_NAME=my_cool_new_bucket
aws s3 mb s3://$BUCKET_NAME
```

Next, run the following command to package our Lambda function to S3:

```bash
sam package \
    --template-file template.yaml \
    --output-template-file packaged.yaml \
    --s3-bucket $BUCKET_NAME
```

Next, the following command will create a Cloudformation Stack and deploy your SAM resources.

```bash
sam deploy \
    --template-file packaged.yaml \
    --stack-name sam-orderHandler \
    --capabilities CAPABILITY_IAM
```

> **See [Serverless Application Model (SAM) HOWTO Guide](https://github.com/awslabs/serverless-application-model/blob/master/HOWTO.md) for more details in how to get started.**

After deployment is complete you can run the following command to retrieve the API Gateway Endpoint URL:

```bash
aws cloudformation describe-stacks \
    --stack-name sam-orderHandler \
    --query 'Stacks[].Outputs'
```

## Testing

### Running unit tests
We use `JUnit` for testing our code.
Unit tests in this sample package mock out the DynamoDBTableMapper class for Order objects.
Unit tests do not require connectivity to a DynamoDB endpoint. You can run unit tests with the
following command:

```bash
mvn test
```

### Running integration tests
Integration tests in this sample package do not mock out the DynamoDBTableMapper and use a real
AmazonDynamoDB client instance. Integration tests require connectivity to a DynamoDB endpoint, and
as such the POM starts DynamoDB Local from the Dockerhub repository for integration tests.

```bash
mvn verify
```

### Running end to end tests through the SAM CLI Local endpoint
Running the following end-to-end tests requires Python 3 and the `requests` pip
package to be installed. For these tests to succeed,
```bash
pip3 install requests
python3 src/test/resources/api_tests.py 3
```

The number that follows the test script name is the number of orders to create in the
test. For these tests to work, you must start DynamoDB Local (`docker-compose up`)
and then start SAM Local with the appropriate CLI command (for example,
`sam local start-api --env-vars src/test/resources/test_environment_mac.json`).

# Appendix

## AWS CLI commands

AWS CLI commands to package, deploy and describe outputs defined within the cloudformation stack:

```bash
sam package \
    --template-file template.yaml \
    --output-template-file packaged.yaml \
    --s3-bucket REPLACE_THIS_WITH_YOUR_S3_BUCKET_NAME

sam deploy \
    --template-file packaged.yaml \
    --stack-name sam-orderHandler \
    --capabilities CAPABILITY_IAM \
    --parameter-overrides MyParameterSample=MySampleValue

aws cloudformation describe-stacks \
    --stack-name sam-orderHandler --query 'Stacks[].Outputs'
```

## Bringing to the next level

Next, you can use the following resources to know more about beyond hello world samples and how others
structure their Serverless applications:

* [AWS Serverless Application Repository](https://aws.amazon.com/serverless/serverlessrepo/)
