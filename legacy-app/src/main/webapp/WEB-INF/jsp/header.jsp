<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.harborclaims.portal.auth.AppUser" %>
<%
    AppUser navUser = (AppUser) session.getAttribute("currentUser");
    String ctx = request.getContextPath();
%>
<div id="header">
  <div>
    <strong>Harbor Claims Portal</strong>
    <% if (navUser != null) { %>
      &nbsp;&nbsp;
      <a href="<%= ctx %>/home.do">Home</a>
      <a href="<%= ctx %>/customers/list.do">Customers</a>
      <a href="<%= ctx %>/claims/search.do">Claims</a>
      <a href="<%= ctx %>/policies/list.do">Policies</a>
      <% String navRole = navUser.getRole(); %>
      <% if ("ADMIN".equals(navRole) || "SUPERVISOR".equals(navRole) || "AUDITOR".equals(navRole)) { %>
      <a href="<%= ctx %>/reports/index.do">Reports</a>
      <% } %>
      <% if ("ADMIN".equals(navRole)) { %>
      <a href="<%= ctx %>/admin/index.do">Admin</a>
      <% } %>
    <% } %>
  </div>
  <div>
    <% if (navUser != null) { %>
      <span id="whoami"><%= navUser.getUsername() %> (<%= navUser.getRole() %>)</span>
      &nbsp;<a href="<%= ctx %>/logout.do">Sign out</a>
    <% } %>
  </div>
</div>
