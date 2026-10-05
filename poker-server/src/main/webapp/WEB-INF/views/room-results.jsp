<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="pageTitle" value="Room ${room.code()}"/>
<%@ include file="header.jspf" %>

<h1><c:out value="${room.name()}"/> <span class="tag ${room.open() ? 'live' : ''}"><c:out value="${room.status()}"/></span></h1>
<p class="muted">
  Room <code><c:out value="${room.code()}"/></code> &middot;
  blinds <fmt:formatNumber value="${room.smallBlind()}" groupingUsed="true"/> / <fmt:formatNumber value="${room.bigBlind()}" groupingUsed="true"/> &middot;
  starting stack <fmt:formatNumber value="${room.startingStack()}" groupingUsed="true"/> &middot;
  ${room.maxPlayers()} seats &middot;
  created <fmt:formatDate value="${room.created()}" pattern="d MMM yyyy, HH:mm"/>
</p>

<h2>Results</h2>
<c:choose>
  <c:when test="${empty standings}">
    <p class="muted">No hand has been finished in this room yet.<c:if test="${room.open()}"> Reload this page once the game is under way.</c:if></p>
  </c:when>
  <c:otherwise>
    <div class="scroll"><table>
      <thead><tr><th>Player</th><th class="num">Won or lost</th><th class="num">Hands played</th><th class="num">Hands won</th></tr></thead>
      <tbody>
      <c:forEach var="line" items="${standings}">
        <tr>
          <td><c:out value="${line.username()}"/></td>
          <td class="num"><t:net value="${line.net()}"/></td>
          <td class="num"><fmt:formatNumber value="${line.handsPlayed()}" groupingUsed="true"/></td>
          <td class="num"><fmt:formatNumber value="${line.handsWon()}" groupingUsed="true"/></td>
        </tr>
      </c:forEach>
      </tbody>
    </table></div>
  </c:otherwise>
</c:choose>

<c:if test="${not empty hands}">
  <h2>Hands</h2>
  <c:if test="${fn:length(hands) == handsShown}"><p class="muted">The latest ${handsShown} hands are listed.</p></c:if>
  <div class="scroll"><table>
    <thead><tr><th class="num">Hand</th><th>Board</th><th class="num">Pot</th><th>Won by</th><th>Time</th><th></th></tr></thead>
    <tbody>
    <c:forEach var="hand" items="${hands}">
      <tr>
        <td class="num">#${hand.handNo()}</td>
        <td><t:cards cards="${hand.board()}" none="no flop"/></td>
        <td class="num"><fmt:formatNumber value="${hand.totalPot()}" groupingUsed="true"/></td>
        <td><c:out value="${hand.winners()}"/></td>
        <td><fmt:formatDate value="${hand.ended()}" pattern="HH:mm:ss"/></td>
        <td><a href="<c:url value='/hands/${hand.id()}'/>">Replay</a></td>
      </tr>
    </c:forEach>
    </tbody>
  </table></div>
</c:if>

<%@ include file="footer.jspf" %>
