package com.sparta.spring_7_reactive.mongo.web.fn;


import com.sparta.spring_7_reactive.mongo.domain.Customer;

import com.sparta.spring_7_reactive.mongo.mapper.CustomerMapper;

import com.sparta.spring_7_reactive.mongo.model.CustomerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import static org.assertj.core.api.Assertions.assertThat;

/*
 * Author: M
 * Date: 10-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@Testcontainers
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class CustomerEndpointTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CustomerMapper customerMapper;


    /**
     * Given a valid customer payload,
     * when the client creates a customer,
     * then the API should return 201 Created with Location header and created customer body.
     */
    @Test
    @DisplayName("given valid customer payload when create beer then return created customer")
    void givenValidCustomerPayload_whenCreateCustomer_thenReturnCreatedCustomer() {
        webTestClient.post()
                .uri(CustomerRouterConfig.CUSTOMER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(getTestCustomerDto())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId").isNotEmpty()
                .jsonPath("$.customerName").isEqualTo("Martin")
                .jsonPath("$.customerJob").isEqualTo("Software Architect");
    }

    /**
     * Given an invalid customer payload,
     * when the client creates a customer,
     * then the API should return 400 Bad Request with an error body.
     */
    @Test
    @DisplayName("given invalid customer payload when create customer then return bad request")
    void givenInvalidCustomerPayload_whenCreateCustomer_thenReturnBadRequest() {
        CustomerDTO invalidCustomer = getTestCustomerDto();
        invalidCustomer.setCustomerName("");

        webTestClient.post()
                .uri(CustomerRouterConfig.CUSTOMER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidCustomer)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").exists();
    }

    /**
     * Given malformed JSON,
     * when the client creates a customer,
     * then the API should return 400 Bad Request with a standard malformed JSON message.
     */
    @Test
    @DisplayName("given malformed json when create customer then return bad request")
    void givenMalformedJson_whenCreateBeer_thenReturnBadRequest() {
        String malformedJson = """
            {
              "customerName": "Space Dust",
              "customerJob":
            }
            """;

        webTestClient.post()
                .uri(CustomerRouterConfig.CUSTOMER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Malformed JSON request body");
    }

    /**
     * Given an existing customer id,
     * when the client retrieves the customer by id,
     * then the API should return 200 OK with the customer body.
     */
    @Test
    @DisplayName("given existing customer id when get customer by id then return customer")
    void givenExistingCustomerId_whenGetCustomerById_thenReturnCustomer() {
        CustomerDTO savedCustomer = saveTestCustomer();

        webTestClient.get()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId").isEqualTo(savedCustomer.getCustomerId())
                .jsonPath("$.customerName").isEqualTo(savedCustomer.getCustomerName())
                .jsonPath("$.customerJob").isEqualTo(savedCustomer.getCustomerJob());
    }

    /**
     * Given a missing customer id,
     * when the client retrieves the customer by id,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given missing customer id when get customer by id then return not found")
    void givenMissingCustomerId_whenGetCustomerById_thenReturnNotFound() {
        webTestClient.get()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, "missing-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given at least one customer exists,
     * when the client lists all customers,
     * then the API should return a non-empty array.
     */
    @Test
    @DisplayName("given customers in database when list customers then return customer collection")
    void givenCustomersInDatabase_whenListCustomers_thenReturnCustomerCollection() {
        saveTestCustomer();

        webTestClient.get()
                .uri(CustomerRouterConfig.CUSTOMER_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0));
    }

    /**
     * Given customers in the database,
     * when the client filters by customer name,
     * then the API should return matching customers.
     */
    @Test
    @DisplayName("given customer name filter when list customers then return matching customers")
    void givenCustomerNameFilter_whenListCustomers_thenReturnMatchingCustomers() {
        saveTestCustomer();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CustomerRouterConfig.CUSTOMER_PATH)
                        .queryParam("customerName", "Martin")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].customerName").isEqualTo("Martin");
    }

    /**
     * Given customers in the database,
     * when the client filters by customer style,
     * then the API should return matching customers.
     */
    @Test
    @DisplayName("given customer style filter when list customers then return matching customers")
    void givenCustomerJobFilter_whenListCustomers_thenReturnMatchingCustomers() {
        saveTestCustomer();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CustomerRouterConfig.CUSTOMER_PATH)
                        .queryParam("customerJob", "Software Architect")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].customerJob").isEqualTo("Software Architect");
    }

    /**
     * Given a valid payload and an existing customer id,
     * when the client updates the customer,
     * then the API should return 200 OK with the updated customer body.
     */
    @Test
    @DisplayName("given valid payload and existing customer id when update customer then return updated customer")
    void givenValidPayloadAndExistingCustomerId_whenUpdateCustomer_thenReturnUpdatedCustomer() {
        CustomerDTO savedCustomer = saveTestCustomer();
        savedCustomer.setCustomerName("Updated Space Dust");
        savedCustomer.setCustomerJob("Double IPA");

        webTestClient.put()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(savedCustomer)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId").isEqualTo(savedCustomer.getCustomerId())
                .jsonPath("$.customerName").isEqualTo("Updated Space Dust")
                .jsonPath("$.customerJob").isEqualTo("Double IPA");
    }

    /**
     * Given an invalid payload,
     * when the client updates the customer,
     * then the API should return 400 Bad Request with an error body.
     */
    @Test
    @DisplayName("given invalid payload when update customer then return bad request")
    void givenInvalidPayload_whenUpdateCustomer_thenReturnBadRequest() {
        CustomerDTO savedCustomer = saveTestCustomer();
        savedCustomer.setCustomerJob("");

        webTestClient.put()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(savedCustomer)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").exists();
    }

    /**
     * Given a valid payload and a missing customer id,
     * when the client updates the customer,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given valid payload and missing customer id when update customer then return not found")
    void givenValidPayloadAndMissingCustomerId_whenUpdateCustomer_thenReturnNotFound() {
        CustomerDTO customerDTO = getTestCustomerDto();


        webTestClient.put()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, "missing-id")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(customerDTO)
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given malformed JSON,
     * when the client updates the customer,
     * then the API should return 400 Bad Request with the standard malformed JSON message.
     */
    @Test
    @DisplayName("given malformed json when update customer then return bad request")
    void givenMalformedJson_whenUpdateCustomer_thenReturnBadRequest() {
        CustomerDTO savedTestCustomer = saveTestCustomer();


        String malformedJson = """
            {
              "customerName": "Updated Space Dust",
              "customerJob":":
            }
            """;

        webTestClient.put()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedTestCustomer.getCustomerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Malformed JSON request body");
    }

    /**
     * Given a partial payload and an existing customer id,
     * when the client patches the customer,
     * then the API should return 200 OK with the updated beer body.
     */
    @Test
    @DisplayName("given partial payload and existing customer id when patch customer then return updated customer")
    void givenPartialPayloadAndExistingCustomerId_whenPatchCustomer_thenReturnUpdatedCustomer() {
        CustomerDTO savedCustomer = saveTestCustomer();

        CustomerDTO patchedPayloadCustomer = new CustomerDTO();
        patchedPayloadCustomer.setCustomerName("Updated Space Dust");



        webTestClient.patch()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchedPayloadCustomer)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId").isEqualTo(savedCustomer.getCustomerId())
                .jsonPath("$.customerName").isEqualTo("Updated Space Dust");
    }

    /**
     * Given a partial payload and a missing customer id,
     * when the client patches the customer,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given partial payload and missing customer id when patch customer then return not found")
    void givenPartialPayloadAndMissingCustomerId_whenPatchCustomer_thenReturnNotFound() {

        CustomerDTO patchedPayloadCustomer = new CustomerDTO();
        patchedPayloadCustomer.setCustomerName("Updated Space Dust");


        webTestClient.patch()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, "missing-id")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchedPayloadCustomer)
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given malformed JSON,
     * when the client patches the customer,
     * then the API should return 400 Bad Request with the standard malformed JSON message.
     */
    @Test
    @DisplayName("given malformed json when patch customer then return bad request")
    void givenMalformedJson_whenPatchCustomer_thenReturnBadRequest() {
        CustomerDTO savedCustomer = saveTestCustomer();


        String malformedJson = """
            {
              "customerName": "Patched Customer Name",
            }
            """;

        webTestClient.patch()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Malformed JSON request body");
    }

    /**
     * Given an existing customer id,
     * when the client deletes the customer,
     * then the API should return 200 OK with the deleted beer body.
     */
    @Test
    @DisplayName("given existing customer id when delete customer then return deleted customer")
    void givenExistingCustomerId_whenDeleteCustomer_thenReturnDeletedCustomer() {
        CustomerDTO savedCustomer = saveTestCustomer();


        webTestClient.delete()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId").isEqualTo(savedCustomer.getCustomerId())
                .jsonPath("$.customerName").isEqualTo(savedCustomer.getCustomerName())
                .jsonPath("$.customerJob").isEqualTo(savedCustomer.getCustomerJob());

    }

    /**
     * Given an existing customer id,
     * when the client deletes the customer,
     * then the resource should no longer be retrievable.
     */
    @Test
    @DisplayName("given existing customer id when delete customer then customer is no longer retrievable")
    void givenExistingCustomerId_whenDeleteCustomer_thenCustomerIsNoLongerRetrievable() {
        CustomerDTO savedCustomer = saveTestCustomer();


        webTestClient.delete()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, savedCustomer.getCustomerId())
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given a missing customer id,
     * when the client deletes the customer,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given missing customer id when delete customer then return not found")
    void givenMissingCustomerId_whenDeleteCustomer_thenReturnNotFound() {
        webTestClient.delete()
                .uri(CustomerRouterConfig.CUSTOMER_PATH_ID, "missing-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Creates a customer through the public POST endpoint and returns the created resource.
     * This helper guarantees that the returned customer is the exact created customer.
     */
    private CustomerDTO saveTestCustomer() {
        CustomerDTO savedCustomer = webTestClient.post()
                .uri(CustomerRouterConfig.CUSTOMER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(getTestCustomerDto())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(CustomerDTO.class)
                .returnResult()
                .getResponseBody();

        if (savedCustomer == null) {
            throw new IllegalStateException("Expected created customer in POST response body, but body was null");
        }

        if (savedCustomer.getCustomerId() == null || savedCustomer.getCustomerId().isBlank()) {
            throw new IllegalStateException("Expected created customer to contain a valid customerId");
        }

        return savedCustomer;
    }

    /**
     * Returns a valid CustomerDTO fixture for integration tests.
     */
    private CustomerDTO getTestCustomerDto() {
        return customerMapper.customerToCustomerDto(getTestCustomer());
    }

    /**
     * Returns a valid Customer domain fixture for integration tests.
     */
    private Customer getTestCustomer() {
        return Customer.builder()
                .customerName("Martin")
                .customerJob("Software Architect")
                .build();
    }
}


