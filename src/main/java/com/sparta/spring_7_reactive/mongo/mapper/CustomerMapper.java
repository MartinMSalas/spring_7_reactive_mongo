package com.sparta.spring_7_reactive.mongo.mapper;


import com.sparta.spring_7_reactive.mongo.domain.Customer;
import com.sparta.spring_7_reactive.mongo.model.CustomerDTO;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/*
 * Author: M
 * Date: 29-Mar-26
 * Project Name: r2dbc
 * Description: beExcellent
 */
@Mapper(
        componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface CustomerMapper {

    Customer customerDtoToCustomer(CustomerDTO customerDTO);

    CustomerDTO customerToCustomerDto(Customer customer);
}
