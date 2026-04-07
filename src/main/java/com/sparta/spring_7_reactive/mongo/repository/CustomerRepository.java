package com.sparta.spring_7_reactive.mongo.repository;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/*
 * Author: M
 * Date: 01-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
public interface CustomerRepository extends ReactiveMongoRepository<Beer, String> {
}
