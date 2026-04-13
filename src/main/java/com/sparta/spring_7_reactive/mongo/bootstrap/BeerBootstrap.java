package com.sparta.spring_7_reactive.mongo.bootstrap;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.repository.BeerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/*
 * Author: M
 * Date: 11-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BeerBootstrap implements CommandLineRunner {

    private final BeerRepository beerRepository;

    @Override
    public void run(String... args) {
        log.info("Starting beer bootstrap");

        long beerCount = beerRepository.count().blockOptional().orElse(0L);
        log.info("Current beer count: {}", beerCount);

        if (beerCount < 3) {
            int beersNeeded = (int) (3 - beerCount);

            List<Beer> seedBeers = List.of(
                    Beer.builder()
                            .beerName("Galaxy Cat")
                            .beerStyle("PALE_ALE")
                            .upc("0631234200036")
                            .price(new BigDecimal("12.99"))
                            .quantityOnHand(122)
                            .build(),
                    Beer.builder()
                            .beerName("Crank")
                            .beerStyle("IPA")
                            .upc("0631234200037")
                            .price(new BigDecimal("13.99"))
                            .quantityOnHand(392)
                            .build(),
                    Beer.builder()
                            .beerName("Sunshine City")
                            .beerStyle("WHEAT")
                            .upc("0631234200038")
                            .price(new BigDecimal("11.99"))
                            .quantityOnHand(144)
                            .build()
            );

            beerRepository.saveAll(seedBeers.stream().limit(beersNeeded).toList())
                    .doOnNext(beer -> log.info("Loaded beer: {}", beer.getBeerName()))
                    .doOnComplete(() -> log.info("Beer bootstrap completed"))
                    .blockLast();
        } else {
            log.info("Beer bootstrap skipped. There are already {} beers in the database.", beerCount);
        }
    }
}