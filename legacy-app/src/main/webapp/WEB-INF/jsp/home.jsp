<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.harborclaims.portal.auth.AppUser" %>
<%
    AppUser homeUser = (AppUser) session.getAttribute("currentUser");
    String base = request.getContextPath();
%>
<h2>Welcome, <%= homeUser == null ? "guest" : homeUser.getDisplayName() %></h2>
<ul>
  <li><a href="<%= base %>/customers/list.do">Find a customer</a></li>
  <li><a href="<%= base %>/claims/search.do">Search claims</a></li>
  <% if (homeUser != null && ("ADMIN".equals(homeUser.getRole()) || "ADJUSTER".equals(homeUser.getRole()) || "CSR".equals(homeUser.getRole()))) { %>
  <li><a href="<%= base %>/claim/new.do">File a new claim</a></li>
  <% } %>
</ul>
