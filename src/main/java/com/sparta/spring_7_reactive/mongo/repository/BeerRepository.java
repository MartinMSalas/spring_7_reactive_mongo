package com.sparta.spring_7_reactive.mongo.repository;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/*
 * Author: M
 * Date: 01-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
public interface BeerRepository extends ReactiveMongoRepository<Beer, String> {

    Flux<Beer> findAllByBeerName(String beerName);

    Flux<Beer> findAllByBeerStyleIgnoreCase(String beerStyle);


}
