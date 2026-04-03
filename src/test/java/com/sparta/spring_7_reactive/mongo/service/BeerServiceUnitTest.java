package com.sparta.spring_7_reactive.mongo.service;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.mapper.BeerMapper;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import com.sparta.spring_7_reactive.mongo.repository.BeerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;



import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
/*
 * Author: M
 * Date: 02-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@ExtendWith(MockitoExtension.class)
public class BeerServiceUnitTest {

    @Mock
    BeerRepository beerRepository;

    @Mock
    BeerMapper beerMapper;

    @InjectMocks
    BeerServiceImpl beerService;

    private Beer beer;
    private BeerDTO beerDTO;

    @BeforeEach
    void setUp() {
        beer = buildBeer(
                "beer-1",
                "Galaxy Cat",
                "IPA",
                "12345",
                10,
                "12.99"
        );

        beerDTO = buildBeerDTO(
                "beer-1",
                "Galaxy Cat",
                "IPA",
                "12345",
                10,
                "12.99"
        );
    }

    @Nested
    class SaveBeerTests {

        @Test
        @DisplayName("saveBeer should sanitize beerId and audit fields before repository save")
        void saveBeerShouldSanitizeServerManagedFields() {
            BeerDTO incomingDto = BeerDTO.builder()
                    .beerId("client-id")
                    .beerName("New Beer")
                    .beerStyle("Lager")
                    .upc("99999")
                    .quantityOnHand(20)
                    .price(BigDecimal.valueOf(8.99))
                    .createdDate(LocalDateTime.now().minusDays(10))
                    .lastModifiedDate(LocalDateTime.now().minusDays(5))
                    .build();

            Beer mappedBeer = Beer.builder()
                    .beerId("client-id")
                    .beerName("New Beer")
                    .beerStyle("Lager")
                    .upc("99999")
                    .quantityOnHand(20)
                    .price(BigDecimal.valueOf(8.99))
                    .createdDate(LocalDateTime.now().minusDays(10))
                    .lastModifiedDate(LocalDateTime.now().minusDays(5))
                    .build();

            Beer savedBeer = buildBeer("generated-id", "New Beer", "Lager", "99999", 20, "8.99");
            BeerDTO savedDto = buildBeerDTO("generated-id", "New Beer", "Lager", "99999", 20, "8.99");

            when(beerMapper.beerDtoToBeer(incomingDto)).thenReturn(mappedBeer);
            when(beerRepository.save(any(Beer.class))).thenReturn(Mono.just(savedBeer));
            when(beerMapper.beerToBeerDto(savedBeer)).thenReturn(savedDto);

            StepVerifier.create(beerService.saveBeer(Mono.just(incomingDto)))
                    .assertNext(result -> {
                        assertThat(result.getBeerId()).isEqualTo("generated-id");
                        assertThat(result.getBeerName()).isEqualTo("New Beer");
                    })
                    .verifyComplete();

            ArgumentCaptor<Beer> captor = ArgumentCaptor.forClass(Beer.class);
            verify(beerRepository).save(captor.capture());
            verify(beerMapper).beerDtoToBeer(incomingDto);
            verify(beerMapper).beerToBeerDto(savedBeer);

            Beer beerPassedToSave = captor.getValue();
            assertThat(beerPassedToSave.getBeerId()).isNull();
            assertThat(beerPassedToSave.getCreatedDate()).isNull();
            assertThat(beerPassedToSave.getLastModifiedDate()).isNull();
            assertThat(beerPassedToSave.getBeerName()).isEqualTo("New Beer");
            assertThat(beerPassedToSave.getBeerStyle()).isEqualTo("Lager");
        }
    }

    @Nested
    class FindTests {

        @Test
        @DisplayName("findBeerById should return dto when beer exists")
        void findBeerByIdShouldReturnDto() {
            when(beerRepository.findById("beer-1")).thenReturn(Mono.just(beer));
            when(beerMapper.beerToBeerDto(beer)).thenReturn(beerDTO);

            StepVerifier.create(beerService.findBeerById("beer-1"))
                    .assertNext(result -> {
                        assertThat(result.getBeerId()).isEqualTo("beer-1");
                        assertThat(result.getBeerName()).isEqualTo("Galaxy Cat");
                    })
                    .verifyComplete();

            verify(beerMapper).beerToBeerDto(beer);
        }

        @Test
        @DisplayName("findBeerById should emit 404 when beer is missing")
        void findBeerByIdShouldEmit404() {
            when(beerRepository.findById("missing-id")).thenReturn(Mono.empty());

            StepVerifier.create(beerService.findBeerById("missing-id"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode().value()).isEqualTo(404);
                        assertThat(ex.getReason()).contains("missing-id");
                    })
                    .verify();

            verify(beerMapper, never()).beerToBeerDto(any());
        }

        @Nested
        class FindAllBeersByBeerNameTests {

            @Test
            @DisplayName("Given existing beers with matching name when findAllBeersByBeerName then return mapped dto flux")
            void givenExistingBeersWithMatchingName_whenFindAllBeersByBeerName_thenReturnMappedDtoFlux() {
                // given
                Beer beer1 = Beer.builder()
                        .beerId("beer-1")
                        .beerName("Galaxy Cat")
                        .beerStyle("IPA")
                        .build();

                Beer beer2 = Beer.builder()
                        .beerId("beer-2")
                        .beerName("Galaxy Cat")
                        .beerStyle("NEIPA")
                        .build();

                BeerDTO dto1 = BeerDTO.builder()
                        .beerId("beer-1")
                        .beerName("Galaxy Cat")
                        .beerStyle("IPA")
                        .build();

                BeerDTO dto2 = BeerDTO.builder()
                        .beerId("beer-2")
                        .beerName("Galaxy Cat")
                        .beerStyle("NEIPA")
                        .build();

                when(beerRepository.findAllByBeerName("Galaxy Cat"))
                        .thenReturn(Flux.just(beer1, beer2));

                when(beerMapper.beerToBeerDto(beer1)).thenReturn(dto1);
                when(beerMapper.beerToBeerDto(beer2)).thenReturn(dto2);

                // when + then
                StepVerifier.create(beerService.findAllBeersByBeerName("Galaxy Cat"))
                        .expectNext(dto1)
                        .expectNext(dto2)
                        .verifyComplete();

                verify(beerRepository).findAllByBeerName("Galaxy Cat");
                verify(beerMapper).beerToBeerDto(beer1);
                verify(beerMapper).beerToBeerDto(beer2);
            }

            @Test
            @DisplayName("Given no beers with matching name when findAllBeersByBeerName then return empty flux")
            void givenNoBeersWithMatchingName_whenFindAllBeersByBeerName_thenReturnEmptyFlux() {
                // given
                when(beerRepository.findAllByBeerName("Missing Beer"))
                        .thenReturn(Flux.empty());

                // when + then
                StepVerifier.create(beerService.findAllBeersByBeerName("Missing Beer"))
                        .verifyComplete();

                verify(beerRepository).findAllByBeerName("Missing Beer");
                verifyNoInteractions(beerMapper);
            }
        }

        @Nested
        class FindAllBeersByBeerStyleTest {

            @Mock
            BeerRepository beerRepository;

            @Mock
            BeerMapper beerMapper;

            @InjectMocks
            BeerServiceImpl beerService;

            @Nested
            class FindAllBeersByBeerStyleTests {

                @Test
                @DisplayName("Given valid style when findAllBeersByBeerStyle then return mapped dto flux")
                void givenValidStyle_whenFindAllBeersByBeerStyle_thenReturnMappedDtoFlux() {
                    // given
                    // Prepare two Beer entities that match the requested style.
                    Beer beer1 = Beer.builder()
                            .beerId("beer-1")
                            .beerName("Galaxy Cat")
                            .beerStyle("IPA")
                            .price(BigDecimal.valueOf(10.00))
                            .build();

                    Beer beer2 = Beer.builder()
                            .beerId("beer-2")
                            .beerName("Hazy Sky")
                            .beerStyle("ipa")
                            .price(BigDecimal.valueOf(12.50))
                            .build();

                    // Prepare their DTO equivalents.
                    BeerDTO dto1 = BeerDTO.builder()
                            .beerId("beer-1")
                            .beerName("Galaxy Cat")
                            .beerStyle("IPA")
                            .price(BigDecimal.valueOf(10.00))
                            .build();

                    BeerDTO dto2 = BeerDTO.builder()
                            .beerId("beer-2")
                            .beerName("Hazy Sky")
                            .beerStyle("ipa")
                            .price(BigDecimal.valueOf(12.50))
                            .build();

                    // Mock repository and mapper behavior.
                    when(beerRepository.findAllByBeerStyleIgnoreCase("IPA"))
                            .thenReturn(Flux.just(beer1, beer2));

                    when(beerMapper.beerToBeerDto(beer1)).thenReturn(dto1);
                    when(beerMapper.beerToBeerDto(beer2)).thenReturn(dto2);

                    // when + then
                    // Verify the service emits both mapped DTOs and completes normally.
                    StepVerifier.create(beerService.findAllBeersByBeerStyle("IPA"))
                            .expectNext(dto1)
                            .expectNext(dto2)
                            .verifyComplete();
                }

                @Test
                @DisplayName("Given valid style with no matches when findAllBeersByBeerStyle then return empty flux")
                void givenValidStyleWithNoMatches_whenFindAllBeersByBeerStyle_thenReturnEmptyFlux() {
                    // given
                    // Mock repository to emit no beers.
                    when(beerRepository.findAllByBeerStyleIgnoreCase("STOUT"))
                            .thenReturn(Flux.empty());

                    // when + then
                    // The service should return an empty Flux and complete without error.
                    StepVerifier.create(beerService.findAllBeersByBeerStyle("STOUT"))
                            .verifyComplete();

                    // Mapper should never be called because there are no beers to map.
                    verifyNoInteractions(beerMapper);
                }

                @Test
                @DisplayName("Given all beers requested when findAllBeers then return all mapped dto items")
                void givenAllBeersRequested_whenFindAllBeers_thenReturnAllMappedDtoItems() {
                    // given
                    Beer beer1 = Beer.builder().beerId("beer-1").beerName("Galaxy Cat").beerStyle("IPA").build();
                    Beer beer2 = Beer.builder().beerId("beer-2").beerName("Dark Peak").beerStyle("STOUT").build();
                    Beer beer3 = Beer.builder().beerId("beer-3").beerName("Golden Day").beerStyle("LAGER").build();

                    BeerDTO dto1 = BeerDTO.builder().beerId("beer-1").beerName("Galaxy Cat").beerStyle("IPA").build();
                    BeerDTO dto2 = BeerDTO.builder().beerId("beer-2").beerName("Dark Peak").beerStyle("STOUT").build();
                    BeerDTO dto3 = BeerDTO.builder().beerId("beer-3").beerName("Golden Day").beerStyle("LAGER").build();

                    when(beerRepository.findAll()).thenReturn(Flux.just(beer1, beer2, beer3));
                    when(beerMapper.beerToBeerDto(beer1)).thenReturn(dto1);
                    when(beerMapper.beerToBeerDto(beer2)).thenReturn(dto2);
                    when(beerMapper.beerToBeerDto(beer3)).thenReturn(dto3);

                    // when + then
                    StepVerifier.create(beerService.findAllBeers())
                            .expectNext(dto1)
                            .expectNext(dto2)
                            .expectNext(dto3)
                            .verifyComplete();
                }

                @Test
                @DisplayName("Given repository error when findAllBeersByBeerStyle then propagate error")
                void givenRepositoryError_whenFindAllBeersByBeerStyle_thenPropagateError() {
                    // given
                    when(beerRepository.findAllByBeerStyleIgnoreCase("IPA"))
                            .thenReturn(Flux.error(new RuntimeException("DB error")));

                    // when + then
                    // Verify the service does not swallow the repository failure.
                    StepVerifier.create(beerService.findAllBeersByBeerStyle("IPA"))
                            .expectError(RuntimeException.class)
                            .verify();
                }

                @Test
                @DisplayName("Given two matching beers when findAllBeersByBeerStyle then call mapper once per beer")
                void givenTwoMatchingBeers_whenFindAllBeersByBeerStyle_thenCallMapperOncePerBeer() {
                    // given
                    Beer beer1 = Beer.builder().beerId("beer-1").beerName("Galaxy Cat").beerStyle("IPA").build();
                    Beer beer2 = Beer.builder().beerId("beer-2").beerName("Hazy Sky").beerStyle("IPA").build();

                    BeerDTO dto1 = BeerDTO.builder().beerId("beer-1").beerName("Galaxy Cat").beerStyle("IPA").build();
                    BeerDTO dto2 = BeerDTO.builder().beerId("beer-2").beerName("Hazy Sky").beerStyle("IPA").build();

                    when(beerRepository.findAllByBeerStyleIgnoreCase("IPA"))
                            .thenReturn(Flux.just(beer1, beer2));

                    when(beerMapper.beerToBeerDto(beer1)).thenReturn(dto1);
                    when(beerMapper.beerToBeerDto(beer2)).thenReturn(dto2);

                    // when
                    StepVerifier.create(beerService.findAllBeersByBeerStyle("IPA"))
                            .expectNext(dto1)
                            .expectNext(dto2)
                            .verifyComplete();

                    // then
                    // Verify mapper interaction count.
                    verify(beerMapper, times(1)).beerToBeerDto(beer1);
                    verify(beerMapper, times(1)).beerToBeerDto(beer2);
                }

                @Test
                @DisplayName("Given null style when findAllBeersByBeerStyle then emit bad request error")
                void givenNullStyle_whenFindAllBeersByBeerStyle_thenEmitBadRequestError() {
                    // when + then
                    StepVerifier.create(beerService.findAllBeersByBeerStyle(null))
                            .expectErrorSatisfies(error -> {
                                assertThat(error).isInstanceOf(ResponseStatusException.class);
                                ResponseStatusException ex = (ResponseStatusException) error;
                                assertThat(ex.getStatusCode().value()).isEqualTo(400);
                            })
                            .verify();

                    verifyNoInteractions(beerRepository);
                    verifyNoInteractions(beerMapper);
                }

                @Test
                @DisplayName("Given blank style when findAllBeersByBeerStyle then emit bad request error")
                void givenBlankStyle_whenFindAllBeersByBeerStyle_thenEmitBadRequestError() {
                    // when + then
                    StepVerifier.create(beerService.findAllBeersByBeerStyle("   "))
                            .expectErrorSatisfies(error -> {
                                assertThat(error).isInstanceOf(ResponseStatusException.class);
                                ResponseStatusException ex = (ResponseStatusException) error;
                                assertThat(ex.getStatusCode().value()).isEqualTo(400);
                            })
                            .verify();

                    verifyNoInteractions(beerRepository);
                    verifyNoInteractions(beerMapper);
                }
            }
        }

        @Test
        @DisplayName("findAllBeers should return all beers mapped to dto")
        void findAllBeersShouldReturnMappedFlux() {
            Beer beer2 = buildBeer("beer-2", "Hazy Sky", "NEIPA", "22222", 5, "14.99");
            BeerDTO dto2 = buildBeerDTO("beer-2", "Hazy Sky", "NEIPA", "22222", 5, "14.99");

            when(beerRepository.findAll()).thenReturn(Flux.just(beer, beer2));
            when(beerMapper.beerToBeerDto(beer)).thenReturn(beerDTO);
            when(beerMapper.beerToBeerDto(beer2)).thenReturn(dto2);

            StepVerifier.create(beerService.findAllBeers())
                    .expectNext(beerDTO)
                    .expectNext(dto2)
                    .verifyComplete();
        }
    }

    @Nested
    class CompleteUpdateTests {

        @Test
        @DisplayName("updateBeerById should overwrite all mutable fields")
        void updateBeerByIdShouldOverwriteMutableFields() {
            Beer existingBeer = buildBeer("beer-1", "Old Name", "Old Style", "11111", 1, "1.00");

            BeerDTO incomingDto = BeerDTO.builder()
                    .beerName("New Name")
                    .beerStyle("New Style")
                    .upc("99999")
                    .quantityOnHand(100)
                    .price(BigDecimal.valueOf(99.99))
                    .build();

            Beer savedBeer = buildBeer("beer-1", "New Name", "New Style", "99999", 100, "99.99");
            BeerDTO savedDto = buildBeerDTO("beer-1", "New Name", "New Style", "99999", 100, "99.99");

            when(beerRepository.findById("beer-1")).thenReturn(Mono.just(existingBeer));
            when(beerRepository.save(any(Beer.class))).thenReturn(Mono.just(savedBeer));
            when(beerMapper.beerToBeerDto(savedBeer)).thenReturn(savedDto);

            StepVerifier.create(beerService.updateBeerById("beer-1", Mono.just(incomingDto)))
                    .assertNext(result -> {
                        assertThat(result.getBeerName()).isEqualTo("New Name");
                        assertThat(result.getBeerStyle()).isEqualTo("New Style");
                        assertThat(result.getUpc()).isEqualTo("99999");
                        assertThat(result.getQuantityOnHand()).isEqualTo(100);
                        assertThat(result.getPrice()).isEqualByComparingTo("99.99");
                    })
                    .verifyComplete();

            ArgumentCaptor<Beer> captor = ArgumentCaptor.forClass(Beer.class);
            verify(beerRepository).save(captor.capture());

            Beer beerPassedToSave = captor.getValue();
            assertThat(beerPassedToSave.getBeerId()).isEqualTo("beer-1");
            assertThat(beerPassedToSave.getBeerName()).isEqualTo("New Name");
            assertThat(beerPassedToSave.getBeerStyle()).isEqualTo("New Style");
            assertThat(beerPassedToSave.getUpc()).isEqualTo("99999");
            assertThat(beerPassedToSave.getQuantityOnHand()).isEqualTo(100);
            assertThat(beerPassedToSave.getPrice()).isEqualByComparingTo("99.99");
        }

        @Test
        @DisplayName("updateBeerById should emit 404 when beer is missing")
        void updateBeerByIdShouldEmit404WhenMissing() {
            BeerDTO incomingDto = BeerDTO.builder()
                    .beerName("New Name")
                    .build();

            when(beerRepository.findById("missing-id")).thenReturn(Mono.empty());

            StepVerifier.create(beerService.updateBeerById("missing-id", Mono.just(incomingDto)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    })
                    .verify();

            verify(beerRepository, never()).save(any());
        }
    }

    @Nested
    class PartialUpdateTests {

        @Test
        @DisplayName("partialUpdateBeerById should update only non-null fields")
        void partialUpdateBeerByIdShouldUpdateOnlyNonNullFields() {
            Beer existingBeer = buildBeer("beer-1", "Original Name", "Original Style", "11111", 10, "10.00");

            BeerDTO patchDto = BeerDTO.builder()
                    .beerName("Patched Name")
                    .price(BigDecimal.valueOf(15.50))
                    .build();

            Beer savedBeer = buildBeer("beer-1", "Patched Name", "Original Style", "11111", 10, "15.50");
            BeerDTO savedDto = buildBeerDTO("beer-1", "Patched Name", "Original Style", "11111", 10, "15.50");

            when(beerRepository.findById("beer-1")).thenReturn(Mono.just(existingBeer));
            when(beerRepository.save(any(Beer.class))).thenReturn(Mono.just(savedBeer));
            when(beerMapper.beerToBeerDto(savedBeer)).thenReturn(savedDto);

            StepVerifier.create(beerService.partialUpdateBeerById("beer-1", Mono.just(patchDto)))
                    .assertNext(result -> {
                        assertThat(result.getBeerName()).isEqualTo("Patched Name");
                        assertThat(result.getBeerStyle()).isEqualTo("Original Style");
                        assertThat(result.getUpc()).isEqualTo("11111");
                        assertThat(result.getQuantityOnHand()).isEqualTo(10);
                        assertThat(result.getPrice()).isEqualByComparingTo("15.50");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("partialUpdateBeerById should allow quantityOnHand to be updated to zero")
        void partialUpdateBeerByIdShouldAllowZeroQuantity() {
            Beer existingBeer = buildBeer("beer-1", "Original Name", "Original Style", "11111", 10, "10.00");

            BeerDTO patchDto = BeerDTO.builder()
                    .quantityOnHand(0)
                    .build();

            Beer savedBeer = buildBeer("beer-1", "Original Name", "Original Style", "11111", 0, "10.00");
            BeerDTO savedDto = buildBeerDTO("beer-1", "Original Name", "Original Style", "11111", 0, "10.00");

            when(beerRepository.findById("beer-1")).thenReturn(Mono.just(existingBeer));
            when(beerRepository.save(any(Beer.class))).thenReturn(Mono.just(savedBeer));
            when(beerMapper.beerToBeerDto(savedBeer)).thenReturn(savedDto);

            StepVerifier.create(beerService.partialUpdateBeerById("beer-1", Mono.just(patchDto)))
                    .assertNext(result -> assertThat(result.getQuantityOnHand()).isEqualTo(0))
                    .verifyComplete();
        }

        @Test
        @DisplayName("partialUpdateBeerById should emit 404 when beer is missing")
        void partialUpdateBeerByIdShouldEmit404WhenMissing() {
            BeerDTO patchDto = BeerDTO.builder()
                    .beerName("Patched Name")
                    .build();

            when(beerRepository.findById("missing-id")).thenReturn(Mono.empty());

            StepVerifier.create(beerService.partialUpdateBeerById("missing-id", Mono.just(patchDto)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    })
                    .verify();

            verify(beerRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteTests {

        @Test
        @DisplayName("deleteBeerById should delete and return deleted dto")
        void deleteBeerByIdShouldDeleteAndReturnDeletedDto() {
            when(beerRepository.findById("beer-1")).thenReturn(Mono.just(beer));
            when(beerRepository.delete(beer)).thenReturn(Mono.empty());
            when(beerMapper.beerToBeerDto(beer)).thenReturn(beerDTO);

            StepVerifier.create(beerService.deleteBeerById("beer-1"))
                    .assertNext(result -> {
                        assertThat(result.getBeerId()).isEqualTo("beer-1");
                        assertThat(result.getBeerName()).isEqualTo("Galaxy Cat");
                    })
                    .verifyComplete();

            verify(beerRepository).delete(beer);
            verify(beerMapper).beerToBeerDto(beer);
        }

        @Test
        @DisplayName("deleteBeerById should emit 404 when beer is missing")
        void deleteBeerByIdShouldEmit404WhenMissing() {
            when(beerRepository.findById("missing-id")).thenReturn(Mono.empty());

            StepVerifier.create(beerService.deleteBeerById("missing-id"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        ResponseStatusException ex = (ResponseStatusException) error;
                        assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    })
                    .verify();

            verify(beerRepository, never()).delete(any());
        }
    }

    @Nested
    class ExistsTests {

        @Test
        @DisplayName("existsBeerById should return true when beer exists")
        void existsBeerByIdShouldReturnTrue() {
            when(beerRepository.existsById("beer-1")).thenReturn(Mono.just(true));

            StepVerifier.create(beerService.existsBeerById("beer-1"))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("existsBeerById should return false when beer does not exist")
        void existsBeerByIdShouldReturnFalse() {
            when(beerRepository.existsById("missing-id")).thenReturn(Mono.just(false));

            StepVerifier.create(beerService.existsBeerById("missing-id"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    private Beer buildBeer(String id, String name, String style, String upc, Integer qoh, String price) {
        return Beer.builder()
                .beerId(id)
                .beerName(name)
                .beerStyle(style)
                .upc(upc)
                .quantityOnHand(qoh)
                .price(new BigDecimal(price))
                .createdDate(LocalDateTime.now().minusDays(1))
                .lastModifiedDate(LocalDateTime.now())
                .build();
    }

    private BeerDTO buildBeerDTO(String id, String name, String style, String upc, Integer qoh, String price) {
        return BeerDTO.builder()
                .beerId(id)
                .beerName(name)
                .beerStyle(style)
                .upc(upc)
                .quantityOnHand(qoh)
                .price(new BigDecimal(price))
                .createdDate(LocalDateTime.now().minusDays(1))
                .lastModifiedDate(LocalDateTime.now())
                .build();
    }
}

