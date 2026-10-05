package com.harborclaims.modern.customers;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The customer form rules and message texts of the legacy app, reproduced exactly (they are part of the characterisation
 * contract). The raw property name is part of "is required" and "must be at most" messages, as in legacy.
 */
public final class CustomerValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\- ]{0,20}$");
    private static final Pattern STATE = Pattern.compile("^[A-Za-z]{2}$");
    private static final Pattern ID = Pattern.compile("[0-9]{1,9}");
    private static final Pattern ISO_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private CustomerValidator() {
    }

    /** All problems, in legacy order. {@code todayUtc} makes today a valid date of birth, as in legacy. */
    public static List<String> validate(CustomerForm raw, LocalDate todayUtc) {
        CustomerForm f = raw.normalized();
        List<String> errors = new ArrayList<>();
        required(errors, f.firstName(), "firstName", 50);
        required(errors, f.lastName(), "lastName", 50);
        if (blank(f.email())) {
            errors.add("email is required");
        } else if (f.email().trim().length() > 120 || !EMAIL.matcher(f.email().trim()).matches()) {
            errors.add("Email address is not valid");
        }
        if (!blank(f.phone()) && !PHONE.matcher(f.phone().trim()).matches()) {
            errors.add("Phone number is not valid");
        }
        if (!blank(f.dateOfBirth()) && !validPastDate(f.dateOfBirth().trim(), todayUtc)) {
            errors.add("Date of birth must be a past date in yyyy-MM-dd format");
        }
        if (!blank(f.state()) && !STATE.matcher(f.state().trim()).matches()) {
            errors.add("State must be two letters");
        }
        tooLong(errors, f.postalCode(), "postalCode", 10);
        tooLong(errors, f.addressLine(), "addressLine", 120);
        tooLong(errors, f.city(), "city", 60);
        if (!blank(f.id()) && !ID.matcher(f.id().trim()).matches()) {
            errors.add("Invalid id");
        }
        return errors;
    }

    private static void required(List<String> errors, String value, String field, int max) {
        if (blank(value)) {
            errors.add(field + " is required");
        } else if (value.trim().length() > max) {
            errors.add(field + " must be at most " + max + " characters");
        }
    }

    private static void tooLong(List<String> errors, String value, String field, int max) {
        if (!blank(value) && value.trim().length() > max) {
            errors.add(field + " must be at most " + max + " characters");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Strict yyyy-MM-dd, a real calendar date, not after today. */
    static boolean validPastDate(String s, LocalDate todayUtc) {
        if (s.length() != 10 || !ISO_DATE.matcher(s).matches()) {
            return false;
        }
        try {
            return !LocalDate.parse(s).isAfter(todayUtc);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
