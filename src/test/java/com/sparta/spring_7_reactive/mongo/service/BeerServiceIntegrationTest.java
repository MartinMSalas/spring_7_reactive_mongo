package com.sparta.spring_7_reactive.mongo.service;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import com.sparta.spring_7_reactive.mongo.repository.BeerRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;



import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;



/*
 * Author: M
 * Date: 02-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class BeerServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer =
            new MongoDBContainer("mongo:7.0");

    @Autowired
    BeerService beerService;

    @Autowired
    BeerRepository beerRepository;

    @BeforeEach
    void setUp() {
        // given
        // Start every test with a clean database state.
        beerRepository.deleteAll().block();
    }

    @Nested
    class SaveBeerTests {

        @Test
        @DisplayName("Given BeerDTO with client-managed id when saveBeer then persist beer with generated id")
        void givenBeerDtoWithClientManagedId_whenSaveBeer_thenPersistBeerWithGeneratedId() {
            // given
            // Build an incoming DTO with a client-provided id.
            // The service should sanitize server-managed fields before save.
            BeerDTO incomingDto = BeerDTO.builder()
                    .beerId("client-id-should-not-survive")
                    .beerName("Galaxy Cat")
                    .beerStyle("IPA")
                    .upc("12345")
                    .quantityOnHand(10)
                    .price(BigDecimal.valueOf(12.99))
                    .build();

            // when + then
            // Save through the real service and verify the returned DTO.
            StepVerifier.create(beerService.saveBeer(Mono.just(incomingDto)))
                    .assertNext(savedDto -> {
                        assertThat(savedDto.getBeerId()).isNotNull();
                        assertThat(savedDto.getBeerId()).isNotEqualTo("client-id-should-not-survive");
                        assertThat(savedDto.getBeerName()).isEqualTo("Galaxy Cat");
                        assertThat(savedDto.getBeerStyle()).isEqualTo("IPA");
                    })
                    .verifyComplete();

            // then
            // Verify the persisted entity in MongoDB also has a generated id.
            StepVerifier.create(beerRepository.findAll())
                    .assertNext(savedBeer -> {
                        assertThat(savedBeer.getBeerId()).isNotNull();
                        assertThat(savedBeer.getBeerId()).isNotEqualTo("client-id-should-not-survive");
                        assertThat(savedBeer.getBeerName()).isEqualTo("Galaxy Cat");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    class FindBeerByIdTests {

        @Test
        @DisplayName("Given existing beer when findBeerById then return persisted beer")
        void givenExistingBeer_whenFindBeerById_thenReturnPersistedBeer() {
            // given
            // Persist a beer directly to prepare real database state.
            Beer savedBeer = saveDirectly("Hazy Sky", "NEIPA", "99999", 8, "15.50");

            // when + then
            // Query by id through the service and verify the returned DTO.
            StepVerifier.create(beerService.findBeerById(savedBeer.getBeerId()))
                    .assertNext(foundDto -> {
                        assertThat(foundDto.getBeerId()).isEqualTo(savedBeer.getBeerId());
                        assertThat(foundDto.getBeerName()).isEqualTo("Hazy Sky");
                        assertThat(foundDto.getBeerStyle()).isEqualTo("NEIPA");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given missing beer id when findBeerById then emit not found error")
        void givenMissingBeerId_whenFindBeerById_thenEmitNotFoundError() {
            // given
            String missingBeerId = "missing-id";

            // when + then
            // Querying a missing id should emit a 404 error.
            StepVerifier.create(beerService.findBeerById(missingBeerId))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(ex.getReason()).contains(missingBeerId);
                    })
                    .verify();
        }
    }

    @Nested
    class FindBeersByNameTests {

        @Test
        @DisplayName("Given beers with same name when findAllBeersByBeerName then return only matching beers")
        void givenBeersWithSameName_whenFindAllBeersByBeerName_thenReturnOnlyMatchingBeers() {
            // given
            saveDirectly("Galaxy Cat", "IPA", "11111", 10, "10.00");
            saveDirectly("Galaxy Cat", "NEIPA", "22222", 8, "12.50");
            saveDirectly("Dark Peak", "STOUT", "33333", 5, "15.00");

            // when + then
            // Search by beer name and verify that only matching beers are returned.
            StepVerifier.create(beerService.findAllBeersByBeerName("Galaxy Cat").collectList())
                    .assertNext(results -> {
                        assertThat(results).hasSize(2);
                        assertThat(results)
                                .extracting(BeerDTO::getBeerName)
                                .containsOnly("Galaxy Cat");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given no beers with matching name when findAllBeersByBeerName then return empty flux")
        void givenNoBeersWithMatchingName_whenFindAllBeersByBeerName_thenReturnEmptyFlux() {
            // given
            saveDirectly("Dark Peak", "STOUT", "33333", 5, "15.00");

            // when + then
            // Search by a missing beer name and verify no items are emitted.
            StepVerifier.create(beerService.findAllBeersByBeerName("Missing Beer"))
                    .verifyComplete();
        }
    }

    @Nested
    class FindBeersByStyleTests {

        @Test
        @DisplayName("Given beers with same style in mixed case when findAllBeersByBeerStyle then return matching beers ignoring case")
        void givenBeersWithSameStyleInMixedCase_whenFindAllBeersByBeerStyle_thenReturnMatchingBeersIgnoringCase() {
            // given
            saveDirectly("Galaxy Cat", "IPA", "11111", 10, "10.00");
            saveDirectly("Hazy Sky", "ipa", "22222", 8, "12.50");
            saveDirectly("Cloud Drift", "IpA", "33333", 7, "11.25");
            saveDirectly("Dark Peak", "STOUT", "44444", 5, "15.00");

            // when + then
            // Search by style ignoring case and verify only IPA beers are returned.
            StepVerifier.create(beerService.findAllBeersByBeerStyle("IPA").collectList())
                    .assertNext(results -> {
                        assertThat(results).hasSize(3);
                        assertThat(results)
                                .extracting(BeerDTO::getBeerStyle)
                                .allMatch(style -> style.equalsIgnoreCase("IPA"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given no beers with matching style when findAllBeersByBeerStyle then return empty flux")
        void givenNoBeersWithMatchingStyle_whenFindAllBeersByBeerStyle_thenReturnEmptyFlux() {
            // given
            saveDirectly("Golden Day", "LAGER", "11111", 10, "10.00");

            // when + then
            // Query a style that does not exist and verify the stream completes empty.
            StepVerifier.create(beerService.findAllBeersByBeerStyle("STOUT"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given null style when findAllBeersByBeerStyle then emit bad request error")
        void givenNullStyle_whenFindAllBeersByBeerStyle_thenEmitBadRequestError() {
            // when + then
            // Null style is invalid input according to the current service contract.
            StepVerifier.create(beerService.findAllBeersByBeerStyle(null))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    })
                    .verify();
        }

        @Test
        @DisplayName("Given blank style when findAllBeersByBeerStyle then emit bad request error")
        void givenBlankStyle_whenFindAllBeersByBeerStyle_thenEmitBadRequestError() {
            // when + then
            // Blank style is also invalid input according to the current service contract.
            StepVerifier.create(beerService.findAllBeersByBeerStyle("   "))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    })
                    .verify();
        }
    }

    @Nested
    class FindAllBeersTests {

        @Test
        @DisplayName("Given mixed beers when findAllBeers then return all persisted beers")
        void givenMixedBeers_whenFindAllBeers_thenReturnAllPersistedBeers() {
            // given
            saveDirectly("Galaxy Cat", "IPA", "11111", 10, "10.00");
            saveDirectly("Dark Peak", "STOUT", "22222", 5, "15.00");
            saveDirectly("Golden Day", "LAGER", "33333", 12, "9.99");

            // when + then
            // Calling findAllBeers should return all beers currently persisted.
            StepVerifier.create(beerService.findAllBeers().collectList())
                    .assertNext(results -> assertThat(results).hasSize(3))
                    .verifyComplete();
        }
    }

    @Nested
    class UpdateBeerTests {

        @Test
        @DisplayName("Given existing beer when updateBeerById then fully update mutable fields")
        void givenExistingBeer_whenUpdateBeerById_thenFullyUpdateMutableFields() {
            // given
            Beer savedBeer = saveDirectly("Old Name", "Old Style", "11111", 1, "1.00");

            BeerDTO updateDto = BeerDTO.builder()
                    .beerName("New Name")
                    .beerStyle("New Style")
                    .upc("22222")
                    .quantityOnHand(100)
                    .price(BigDecimal.valueOf(99.99))
                    .build();

            // when + then
            // Perform a complete update and verify returned DTO fields.
            StepVerifier.create(beerService.updateBeerById(savedBeer.getBeerId(), Mono.just(updateDto)))
                    .assertNext(updatedDto -> {
                        assertThat(updatedDto.getBeerId()).isEqualTo(savedBeer.getBeerId());
                        assertThat(updatedDto.getBeerName()).isEqualTo("New Name");
                        assertThat(updatedDto.getBeerStyle()).isEqualTo("New Style");
                        assertThat(updatedDto.getUpc()).isEqualTo("22222");
                        assertThat(updatedDto.getQuantityOnHand()).isEqualTo(100);
                        assertThat(updatedDto.getPrice()).isEqualByComparingTo("99.99");
                    })
                    .verifyComplete();

            // then
            // Reload from repository and verify the update really persisted.
            StepVerifier.create(beerRepository.findById(savedBeer.getBeerId()))
                    .assertNext(reloadedBeer -> {
                        assertThat(reloadedBeer.getBeerName()).isEqualTo("New Name");
                        assertThat(reloadedBeer.getBeerStyle()).isEqualTo("New Style");
                        assertThat(reloadedBeer.getUpc()).isEqualTo("22222");
                        assertThat(reloadedBeer.getQuantityOnHand()).isEqualTo(100);
                        assertThat(reloadedBeer.getPrice()).isEqualByComparingTo("99.99");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given missing beer id when updateBeerById then emit not found error")
        void givenMissingBeerId_whenUpdateBeerById_thenEmitNotFoundError() {
            // given
            BeerDTO updateDto = BeerDTO.builder()
                    .beerName("New Name")
                    .beerStyle("New Style")
                    .build();

            // when + then
            // Updating a missing beer should emit 404.
            StepVerifier.create(beerService.updateBeerById("missing-id", Mono.just(updateDto)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .verify();
        }
    }

    @Nested
    class PartialUpdateBeerTests {

        @Test
        @DisplayName("Given existing beer when partialUpdateBeerById then update only non-null fields")
        void givenExistingBeer_whenPartialUpdateBeerById_thenUpdateOnlyNonNullFields() {
            // given
            Beer savedBeer = saveDirectly("Original Name", "Original Style", "11111", 10, "10.00");

            BeerDTO patchDto = BeerDTO.builder()
                    .beerName("Patched Name")
                    .price(BigDecimal.valueOf(15.50))
                    .build();

            // when + then
            // Apply a partial update and verify only provided fields changed.
            StepVerifier.create(beerService.partialUpdateBeerById(savedBeer.getBeerId(), Mono.just(patchDto)))
                    .assertNext(updatedDto -> {
                        assertThat(updatedDto.getBeerName()).isEqualTo("Patched Name");
                        assertThat(updatedDto.getBeerStyle()).isEqualTo("Original Style");
                        assertThat(updatedDto.getUpc()).isEqualTo("11111");
                        assertThat(updatedDto.getQuantityOnHand()).isEqualTo(10);
                        assertThat(updatedDto.getPrice()).isEqualByComparingTo("15.50");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given existing beer when partialUpdateBeerById with zero quantity then persist zero quantity")
        void givenExistingBeer_whenPartialUpdateBeerByIdWithZeroQuantity_thenPersistZeroQuantity() {
            // given
            Beer savedBeer = saveDirectly("Original Name", "Original Style", "11111", 10, "10.00");

            BeerDTO patchDto = BeerDTO.builder()
                    .quantityOnHand(0)
                    .build();

            // when + then
            // Update quantity to zero and verify zero is treated as a valid update.
            StepVerifier.create(beerService.partialUpdateBeerById(savedBeer.getBeerId(), Mono.just(patchDto)))
                    .assertNext(updatedDto -> assertThat(updatedDto.getQuantityOnHand()).isEqualTo(0))
                    .verifyComplete();

            // then
            // Reload from repository and verify zero persisted.
            StepVerifier.create(beerRepository.findById(savedBeer.getBeerId()))
                    .assertNext(reloadedBeer -> assertThat(reloadedBeer.getQuantityOnHand()).isEqualTo(0))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given missing beer id when partialUpdateBeerById then emit not found error")
        void givenMissingBeerId_whenPartialUpdateBeerById_thenEmitNotFoundError() {
            // given
            BeerDTO patchDto = BeerDTO.builder()
                    .beerName("Patched Name")
                    .build();

            // when + then
            // Patching a missing beer should emit 404.
            StepVerifier.create(beerService.partialUpdateBeerById("missing-id", Mono.just(patchDto)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .verify();
        }
    }

    @Nested
    class DeleteBeerTests {

        @Test
        @DisplayName("Given existing beer when deleteBeerById then delete beer and return deleted dto")
        void givenExistingBeer_whenDeleteBeerById_thenDeleteBeerAndReturnDeletedDto() {
            // given
            Beer savedBeer = saveDirectly("To Delete", "Stout", "55555", 3, "7.25");

            // when + then
            // Delete through the service and verify the returned deleted DTO.
            StepVerifier.create(beerService.deleteBeerById(savedBeer.getBeerId()))
                    .assertNext(deletedDto -> {
                        assertThat(deletedDto.getBeerId()).isEqualTo(savedBeer.getBeerId());
                        assertThat(deletedDto.getBeerName()).isEqualTo("To Delete");
                    })
                    .verifyComplete();

            // then
            // Verify the entity no longer exists in the repository.
            StepVerifier.create(beerRepository.findById(savedBeer.getBeerId()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Given missing beer id when deleteBeerById then emit not found error")
        void givenMissingBeerId_whenDeleteBeerById_thenEmitNotFoundError() {
            // when + then
            // Deleting a missing beer should emit 404.
            StepVerifier.create(beerService.deleteBeerById("missing-id"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .verify();
        }
    }

    @Nested
    class ExistsBeerTests {

        @Test
        @DisplayName("Given existing and missing beer ids when existsBeerById then reflect real database state")
        void givenExistingAndMissingBeerIds_whenExistsBeerById_thenReflectRealDatabaseState() {
            // given
            Beer savedBeer = saveDirectly("Exists Beer", "Pilsner", "77777", 4, "6.99");

            // when + then
            // Existing id should return true.
            StepVerifier.create(beerService.existsBeerById(savedBeer.getBeerId()))
                    .expectNext(true)
                    .verifyComplete();

            // Missing id should return false.
            StepVerifier.create(beerService.existsBeerById("missing-id"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    private Beer saveDirectly(String name, String style, String upc, Integer quantityOnHand, String price) {
        // given
        // Helper method to persist a real Beer entity for integration test setup.
        Beer beer = Beer.builder()
                .beerName(name)
                .beerStyle(style)
                .upc(upc)
                .quantityOnHand(quantityOnHand)
                .price(new BigDecimal(price))
                .build();

        Beer savedBeer = beerRepository.save(beer).block();
        assertThat(savedBeer).isNotNull();
        assertThat(savedBeer.getBeerId()).isNotNull();
        return savedBeer;
    }
}