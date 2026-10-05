package com.harborclaims.modern.customers;

/** A customer row. Dates are already formatted yyyy-MM-dd by the query. */
public record Customer(int id, String firstName, String lastName, String email, String phone, String dateOfBirth,
        String addressLine, String city, String state, String postalCode) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
