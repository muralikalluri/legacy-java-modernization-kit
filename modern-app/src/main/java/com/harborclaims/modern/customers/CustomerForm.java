package com.harborclaims.modern.customers;

/** The customer form as submitted. Every field is a String so a blank number can never silently become zero. */
public record CustomerForm(String id, String firstName, String lastName, String email, String phone, String dateOfBirth,
        String addressLine, String city, String state, String postalCode) {

    public static CustomerForm empty() {
        return new CustomerForm("", "", "", "", "", "", "", "", "", "");
    }

    public static CustomerForm of(Customer c) {
        return new CustomerForm(String.valueOf(c.id()), c.firstName(), c.lastName(), c.email(), n(c.phone()), n(c.dateOfBirth()),
                n(c.addressLine()), n(c.city()), n(c.state()), n(c.postalCode()));
    }

    /** A copy where every null (a missing request parameter) becomes an empty string. */
    public CustomerForm normalized() {
        return new CustomerForm(n(id), n(firstName), n(lastName), n(email), n(phone), n(dateOfBirth), n(addressLine), n(city), n(state), n(postalCode));
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
