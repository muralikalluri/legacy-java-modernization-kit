package com.harborclaims.portal.admin;

import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class AdminUserForm extends ActionForm {

    private static final long serialVersionUID = 1L;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");

    private String id = "";
    private String username = "";
    private String password = "";
    private String displayName = "";
    private String role = "";
    private String active = "true";

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if (!blank(id) && !id.trim().matches("[0-9]{1,9}")) {
            errors.add("id", new ActionMessage("error.id.invalid"));
        }
        if (blank(id)) {
            if (!USERNAME.matcher(username == null ? "" : username.trim()).matches()) {
                errors.add("username", new ActionMessage("error.admin.username.invalid"));
            }
            if (blank(password)) {
                errors.add("password", new ActionMessage("error.required", "password"));
            }
        }
        if (blank(displayName)) {
            errors.add("displayName", new ActionMessage("error.required", "displayName"));
        } else if (displayName.trim().length() > 80) {
            errors.add("displayName", new ActionMessage("error.too.long", "displayName", 80));
        }
        if (blank(role)) {
            errors.add("role", new ActionMessage("error.required", "role"));
        }
        if (password != null && password.length() > 64) {
            errors.add("password", new ActionMessage("error.too.long", "password", 64));
        }
        return errors;
    }

    private boolean blank(String s) {
        return s == null || s.trim().length() == 0;
    }

    public int idAsInt() {
        return blank(id) ? 0 : Integer.parseInt(id.trim());
    }

    public boolean isActiveFlag() {
        return !"false".equals(active);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }
}
