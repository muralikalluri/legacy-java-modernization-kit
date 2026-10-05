<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Sign in</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<html:form action="/loginSubmit" method="post">
  <label>Username <html:text property="username"/></label>
  <label>Password <html:password property="password"/></label>
  <p><html:submit value="Sign in"/></p>
</html:form>
