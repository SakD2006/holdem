<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="pageTitle" value="Leaderboard"/>
<%@ include file="header.jspf" %>

<h1>Leaderboard</h1>
<p class="muted">Chips won less chips lost, over every hand played on this server.</p>

<c:choose>
  <c:when test="${empty entries}">
    <p>No hands have been played yet.</p>
  </c:when>
  <c:otherwise>
    <div class="scroll"><table>
      <thead><tr><th class="num">#</th><th>Player</th><th class="num">Total won</th><th class="num">Hands played</th><th class="num">Hands won</th></tr></thead>
      <tbody>
      <c:forEach var="entry" items="${entries}">
        <tr class="${entry.rank() == 1 ? 'first' : ''}">
          <td class="num"><c:out value="${entry.rank()}"/></td>
          <td><c:out value="${entry.username()}"/></td>
          <td class="num"><t:net value="${entry.totalNet()}"/></td>
          <td class="num"><fmt:formatNumber value="${entry.handsPlayed()}" groupingUsed="true"/></td>
          <td class="num"><fmt:formatNumber value="${entry.handsWon()}" groupingUsed="true"/></td>
        </tr>
      </c:forEach>
      </tbody>
    </table></div>
  </c:otherwise>
</c:choose>

<%@ include file="footer.jspf" %>
