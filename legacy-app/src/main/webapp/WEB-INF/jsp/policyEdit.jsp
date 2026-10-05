<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Policy</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<html:form action="/policy/save" method="post">
  <html:hidden property="id"/>
  <label>Customer id <html:text property="customerId"/></label>
  <label>Product (AUTO, HOME or LIFE) <html:text property="productType"/></label>
  <label>Coverage limit <html:text property="coverageLimit"/></label>
  <label>Premium <html:text property="premium"/></label>
  <label>Start date (yyyy-MM-dd) <html:text property="startDate"/></label>
  <label>End date (yyyy-MM-dd) <html:text property="endDate"/></label>
  <p><html:submit value="Save"/></p>
</html:form>
