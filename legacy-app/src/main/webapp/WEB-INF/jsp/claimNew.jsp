<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>File a new claim</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<html:form action="/claim/save" method="post">
  <label>Policy number <html:text property="policyNumber"/></label>
  <label>Loss date (yyyy-MM-dd) <html:text property="lossDate"/></label>
  <label>Amount <html:text property="amountClaimed"/></label>
  <label>Description <html:textarea property="description" rows="4" cols="50"/></label>
  <p><html:submit value="File claim"/></p>
</html:form>
