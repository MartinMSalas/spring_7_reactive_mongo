package com.sparta.spring_7_reactive.mongo.service;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/*
 * Author: M
 * Date: 01-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
public interface BeerService {

    Mono<BeerDTO> saveBeer(Mono<BeerDTO> beerDtoMono);

    Mono<BeerDTO> findBeerById(String beerId);

    Flux<BeerDTO> findAllBeers();

    Flux<BeerDTO> findAllBeersByBeerName(String beerName);

    Flux<BeerDTO> findAllBeersByBeerStyle(String beerStyle);

    Flux<BeerDTO> findAllBeersByBeerStyle();

    Mono<BeerDTO> updateBeerById(String beerId, Mono<BeerDTO> beerDtoMono);

    Mono<BeerDTO> partialUpdateBeerById(String beerId, Mono<BeerDTO> beerDtoMono);

    Mono<BeerDTO> deleteBeerById(String beerId);

    Mono<Boolean> existsBeerById(String beerId);
}
