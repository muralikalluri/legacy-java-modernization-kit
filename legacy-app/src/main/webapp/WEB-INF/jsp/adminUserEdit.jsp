<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>User</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<html:form action="/admin/userSave" method="post">
  <html:hidden property="id"/>
  <label>Username (new users only) <html:text property="username"/></label>
  <label>Password (blank keeps the current one) <html:text property="password"/></label>
  <label>Display name <html:text property="displayName"/></label>
  <label>Role
    <html:select property="role">
      <html:options name="roleNames"/>
    </html:select>
  </label>
  <label>Active
    <html:select property="active">
      <html:option value="true">Yes</html:option>
      <html:option value="false">No</html:option>
    </html:select>
  </label>
  <p><html:submit value="Save"/></p>
</html:form>
