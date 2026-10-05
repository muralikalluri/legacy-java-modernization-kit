package com.harborclaims.modern.customers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomerValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private static CustomerForm valid() {
        return new CustomerForm("", "Test", "Person", "test.person@example.test", "", "", "", "", "", "");
    }

    private static List<String> errors(CustomerForm f) {
        return CustomerValidator.validate(f, TODAY);
    }

    @Test
    void aMinimalFormIsValid() {
        assertEquals(List.of(), errors(valid()));
    }

    @Test
    void everyMissingRequiredFieldIsReportedWithItsRawPropertyName() {
        assertEquals(List.of("firstName is required", "lastName is required", "email is required"), errors(CustomerForm.empty()));
        assertEquals(List.of("firstName is required"), errors(new CustomerForm("", "   ", "P", "a@b.cc", "", "", "", "", "", "")));
    }

    @Test
    void aNullFieldFromAMissingParameterIsTreatedAsBlank() {
        assertEquals(List.of("firstName is required", "lastName is required", "email is required"),
                errors(new CustomerForm(null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void tooLongValuesNameTheLimit() {
        CustomerForm f = new CustomerForm("", "a".repeat(51), "b".repeat(51), "x@y.zz", "", "", "c".repeat(121), "d".repeat(61), "", "9".repeat(11));
        assertEquals(List.of("firstName must be at most 50 characters", "lastName must be at most 50 characters",
                "postalCode must be at most 10 characters", "addressLine must be at most 120 characters", "city must be at most 60 characters"),
                errors(f));
    }

    @Test
    void anOverlongEmailIsNotValidRatherThanTooLong() {
        assertEquals(List.of("Email address is not valid"), errors(new CustomerForm("", "A", "B", "c".repeat(115) + "@x.test", "", "", "", "", "", "")));
    }

    @ParameterizedTest
    @CsvSource({"a@b", "no-at-sign.example.test", "a b@c.dd", "@c.dd", "a@.dd"})
    void badEmails(String email) {
        assertEquals(List.of("Email address is not valid"), errors(new CustomerForm("", "A", "B", email, "", "", "", "", "", "")));
    }

    @Test
    void phoneStateAndDateRules() {
        assertEquals(List.of("Phone number is not valid"), errors(withPhone("abc")));
        assertEquals(List.of(), errors(withPhone("+1 (555) 010-2030")));
        assertEquals(List.of("State must be two letters"), errors(withState("ABC")));
        assertEquals(List.of("State must be two letters"), errors(withState("N")));
        assertEquals(List.of(), errors(withState("ny")));
    }

    private static CustomerForm withPhone(String p) {
        return new CustomerForm("", "A", "B", "a@b.cc", p, "", "", "", "", "");
    }

    private static CustomerForm withState(String s) {
        return new CustomerForm("", "A", "B", "a@b.cc", "", "", "", "", s, "");
    }

    @ParameterizedTest
    @CsvSource({"1990-02-30", "1990-1-1", "01/02/1990", "2026-10-06", "2999-12-31", "1980-01-1x", "19900101", "1990-13-01"})
    void invalidDatesOfBirth(String dob) {
        assertEquals(List.of("Date of birth must be a past date in yyyy-MM-dd format"),
                errors(new CustomerForm("", "A", "B", "a@b.cc", "", dob, "", "", "", "")), dob);
    }

    @Test
    void todayIsAValidDateOfBirthAsInLegacy() {
        assertEquals(List.of(), errors(new CustomerForm("", "A", "B", "a@b.cc", "", "2026-10-05", "", "", "", "")));
        assertEquals(List.of(), errors(new CustomerForm("", "A", "B", "a@b.cc", "", "1990-01-02", "", "", "", "")));
    }

    @ParameterizedTest
    @CsvSource({"abc", "+5", "-1", "1.5", "0x1", "1234567890", "1 2"})
    void anIdThatIsNotOneToNineDigitsIsInvalid(String id) {
        assertEquals(List.of("Invalid id"), errors(new CustomerForm(id, "A", "B", "a@b.cc", "", "", "", "", "", "")), id);
    }

    @Test
    void aPaddedIdAndZeroAreAccepted() {
        assertEquals(List.of(), errors(new CustomerForm(" 5 ", "A", "B", "a@b.cc", "", "", "", "", "", "")));
        assertEquals(List.of(), errors(new CustomerForm("000", "A", "B", "a@b.cc", "", "", "", "", "", "")));
        assertEquals(List.of(), errors(new CustomerForm("123456789", "A", "B", "a@b.cc", "", "", "", "", "", "")));
    }

    @Test
    void validPastDateIsStrict() {
        assertTrue(CustomerValidator.validPastDate("2024-02-29", TODAY));
        assertFalse(CustomerValidator.validPastDate("2025-02-29", TODAY));
    }
}
