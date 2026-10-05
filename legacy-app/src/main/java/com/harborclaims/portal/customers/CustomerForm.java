package com.harborclaims.portal.customers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

/** All fields are Strings: Struts 1 would turn a blank number into 0. */
public class CustomerForm extends ActionForm {

    private static final long serialVersionUID = 1L;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\- ]{0,20}$");
    private static final Pattern STATE = Pattern.compile("^[A-Za-z]{2}$");

    private String id = "";
    private String firstName = "";
    private String lastName = "";
    private String email = "";
    private String phone = "";
    private String dateOfBirth = "";
    private String addressLine = "";
    private String city = "";
    private String state = "";
    private String postalCode = "";

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        required(errors, firstName, "firstName", 50);
        required(errors, lastName, "lastName", 50);
        if (blank(email)) {
            errors.add("email", new ActionMessage("error.required", "email"));
        } else if (email.trim().length() > 120 || !EMAIL.matcher(email.trim()).matches()) {
            errors.add("email", new ActionMessage("error.email.invalid"));
        }
        if (!blank(phone) && !PHONE.matcher(phone.trim()).matches()) {
            errors.add("phone", new ActionMessage("error.phone.invalid"));
        }
        if (!blank(dateOfBirth) && !validPastDate(dateOfBirth.trim())) {
            errors.add("dateOfBirth", new ActionMessage("error.dob.invalid"));
        }
        if (!blank(state) && !STATE.matcher(state.trim()).matches()) {
            errors.add("state", new ActionMessage("error.state.invalid"));
        }
        if (!blank(postalCode) && postalCode.trim().length() > 10) {
            errors.add("postalCode", new ActionMessage("error.too.long", "postalCode", 10));
        }
        if (!blank(addressLine) && addressLine.trim().length() > 120) {
            errors.add("addressLine", new ActionMessage("error.too.long", "addressLine", 120));
        }
        if (!blank(city) && city.trim().length() > 60) {
            errors.add("city", new ActionMessage("error.too.long", "city", 60));
        }
        if (!blank(id) && !id.trim().matches("[0-9]{1,9}")) {
            errors.add("id", new ActionMessage("error.id.invalid"));
        }
        return errors;
    }

    private void required(ActionErrors errors, String value, String field, int max) {
        if (blank(value)) {
            errors.add(field, new ActionMessage("error.required", field));
        } else if (value.trim().length() > max) {
            errors.add(field, new ActionMessage("error.too.long", field, max));
        }
    }

    private boolean blank(String s) {
        return s == null || s.trim().length() == 0;
    }

    private boolean validPastDate(String s) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        f.setLenient(false);
        try {
            return f.parse(s).before(new java.util.Date()) && s.length() == 10;
        } catch (ParseException e) {
            return false;
        }
    }

    public Customer toCustomer() {
        Customer c = new Customer();
        c.setId(blank(id) ? 0 : Integer.parseInt(id.trim()));
        c.setFirstName(firstName.trim());
        c.setLastName(lastName.trim());
        c.setEmail(email.trim());
        c.setPhone(phone);
        c.setDateOfBirth(dateOfBirth);
        c.setAddressLine(addressLine);
        c.setCity(city);
        c.setState(blank(state) ? state : state.trim().toUpperCase());
        c.setPostalCode(postalCode);
        return c;
    }

    public void load(Customer c) {
        id = String.valueOf(c.getId());
        firstName = c.getFirstName();
        lastName = c.getLastName();
        email = c.getEmail();
        phone = n(c.getPhone());
        dateOfBirth = n(c.getDateOfBirth());
        addressLine = n(c.getAddressLine());
        city = n(c.getCity());
        state = n(c.getState());
        postalCode = n(c.getPostalCode());
    }

    private String n(String s) {
        return s == null ? "" : s;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
}
