package com.sparta.spring_7_reactive.mongo.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * Author: M
 * Date: 28-Mar-26
 * Project Name: r2dbc
 * Description: beExcellent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeerDTO {

    private String beerId;
    @NotBlank(message = "Beer name is required")
    private String beerName;

    @NotBlank(message = "Beer style is required")
    private String beerStyle;

    @NotBlank(message = "UPC is required")
    private String upc;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Quantity on hand is required")
    @Min(value = 0, message = "Quantity on hand must be 0 or greater")
    private Integer quantityOnHand;

    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;


}
