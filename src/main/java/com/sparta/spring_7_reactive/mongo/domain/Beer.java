package com.sparta.spring_7_reactive.mongo.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * Author: M
 * Date: 01-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
public class Beer {

    @Id
    private String beerId;

    private String beerName;
    private String beerStyle;
    private String upc;
    private Integer quantityOnHand;
    private BigDecimal price;

    @CreatedDate
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
}