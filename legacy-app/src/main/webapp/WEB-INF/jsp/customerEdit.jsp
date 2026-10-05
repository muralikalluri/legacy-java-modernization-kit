<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Customer</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<html:form action="/customers/save" method="post">
  <html:hidden property="id"/>
  <label>First name <html:text property="firstName"/></label>
  <label>Last name <html:text property="lastName"/></label>
  <label>Email <html:text property="email"/></label>
  <label>Phone <html:text property="phone"/></label>
  <label>Date of birth (yyyy-MM-dd) <html:text property="dateOfBirth"/></label>
  <label>Address <html:text property="addressLine"/></label>
  <label>City <html:text property="city"/></label>
  <label>State <html:text property="state" size="2"/></label>
  <label>Postal code <html:text property="postalCode"/></label>
  <p><html:submit value="Save"/></p>
</html:form>
