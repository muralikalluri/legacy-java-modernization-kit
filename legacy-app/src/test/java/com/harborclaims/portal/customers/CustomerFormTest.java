package com.harborclaims.portal.customers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.struts.action.ActionErrors;
import org.junit.Test;

public class CustomerFormTest {

    private CustomerForm valid() {
        CustomerForm f = new CustomerForm();
        f.setFirstName("Test");
        f.setLastName("Person");
        f.setEmail("test.person@example.test");
        return f;
    }

    @Test
    public void minimalValidFormHasNoErrors() {
        assertTrue(valid().validate(null, null).isEmpty());
    }

    @Test
    public void requiredFieldsAreChecked() {
        ActionErrors errors = new CustomerForm().validate(null, null);
        assertEquals(3, errors.size());
    }

    @Test
    public void badEmailPhoneStateAndDobAreRejected() {
        CustomerForm f = valid();
        f.setEmail("not-an-email");
        f.setPhone("abc");
        f.setState("ABC");
        f.setDateOfBirth("2999-01-01");
        assertEquals(4, f.validate(null, null).size());
    }

    @Test
    public void dobMustBeStrictlyFormatted() {
        CustomerForm f = valid();
        f.setDateOfBirth("1990-1-5");
        assertEquals(1, f.validate(null, null).size());
        f.setDateOfBirth("1990-01-05");
        assertTrue(f.validate(null, null).isEmpty());
    }

    @Test
    public void toCustomerTrimsAndUppercasesState() {
        CustomerForm f = valid();
        f.setFirstName("  Test ");
        f.setState("hb");
        Customer c = f.toCustomer();
        assertEquals("Test", c.getFirstName());
        assertEquals("HB", c.getState());
        assertEquals(0, c.getId());
    }
}
