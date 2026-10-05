<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="pageTitle" value="Hand #${hand.handNo()}"/>
<%@ include file="header.jspf" %>

<p class="crumbs"><a href="<c:url value='/rooms/${hand.roomCode()}'/>">&larr; <c:out value="${hand.roomName()}"/></a></p>
<h1>Hand #${hand.handNo()}</h1>
<p class="muted">
  Room <code><c:out value="${hand.roomCode()}"/></code> &middot;
  <fmt:formatDate value="${ended}" pattern="d MMM yyyy, HH:mm:ss"/> &middot;
  pot <fmt:formatNumber value="${hand.totalPot()}" groupingUsed="true"/>
</p>

<div class="board">
  <span class="caption">Board</span>
  <t:cards cards="${board}" none="The hand ended before the flop"/>
</div>

<h2>Players</h2>
<div class="scroll"><table>
  <thead><tr><th class="num">Seat</th><th>Player</th><th>Cards</th><th class="num">Started with</th><th class="num">Won or lost</th></tr></thead>
  <tbody>
  <c:forEach var="player" items="${replay.players()}">
    <tr class="${player.won() ? 'first' : ''}">
      <td class="num">${player.seat()}</td>
      <td><c:out value="${player.username()}"/><c:if test="${player.button()}"> <span class="tag">Dealer</span></c:if><c:if test="${player.won()}"> <span class="tag live">Winner</span></c:if></td>
      <td><t:cards cards="${player.cards()}" none="not shown"/></td>
      <td class="num"><fmt:formatNumber value="${player.startStack()}" groupingUsed="true"/></td>
      <td class="num"><t:net value="${player.net()}"/></td>
    </tr>
  </c:forEach>
  </tbody>
</table></div>

<h2>What happened</h2>
<div class="streets">
<c:forEach var="street" items="${replay.streets()}">
  <section class="street">
    <h3><c:out value="${street.name()}"/> <c:if test="${not empty street.cards()}"><t:cards cards="${street.cards()}"/></c:if></h3>
    <c:choose>
      <c:when test="${empty street.steps()}">
        <p class="muted">No betting: the players still in were all-in.</p>
      </c:when>
      <c:otherwise>
        <ol>
          <c:forEach var="step" items="${street.steps()}">
            <li><strong><c:out value="${step.username()}"/></strong> <c:out value="${step.text()}"/></li>
          </c:forEach>
        </ol>
      </c:otherwise>
    </c:choose>
  </section>
</c:forEach>
</div>

<%@ include file="footer.jspf" %>
