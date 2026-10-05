package com.saksham.poker.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.saksham.poker.common.api.ApiError;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.server.bootstrap.AppContext;
import com.saksham.poker.server.db.User;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What every API servlet shares: reading and writing JSON, finding the logged-in user, and turning
 * any {@link PokerException} into an error answer with the matching HTTP status. Subclasses override
 * {@link #handleGet} or {@link #handlePost} and simply throw when something is wrong.
 */
public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(BaseServlet.class);

    /** The largest request body accepted, in characters. */
    private static final int MAX_BODY = 8 * 1024;

    @Override
    protected final void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            switch (request.getMethod()) {
                case "GET":
                    handleGet(request, response);
                    break;
                case "POST":
                    handlePost(request, response);
                    break;
                default:
                    methodNotAllowed(request, response);
                    break;
            }
        } catch (PokerException e) {
            JsonSupport.writeError(response, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("{} {} failed", request.getMethod(), request.getRequestURI(), e);
            JsonSupport.writeError(response, ErrorCode.INTERNAL,
                    "Something went wrong on the server. Try again; if it keeps happening, check the server log.");
        }
    }

    /** Answers a GET. The default refuses it. */
    protected void handleGet(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        methodNotAllowed(request, response);
    }

    /** Answers a POST. The default refuses it. */
    protected void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        methodNotAllowed(request, response);
    }

    private static void methodNotAllowed(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        JsonSupport.write(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, new ApiError(
                ErrorCode.INVALID_REQUEST, request.getMethod() + " is not supported at this address."));
    }

    /** The server's shared services. */
    protected AppContext app() {
        return AppContext.from(getServletContext());
    }

    /** The user the login filter found for this request. */
    protected static User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthFilter.USER_ATTRIBUTE);
    }

    /**
     * Reads the request body as JSON.
     *
     * @throws InvalidRequestException if the body is missing, too large or not the expected JSON
     */
    protected static <T> T readJson(HttpServletRequest request, Class<T> type)
            throws InvalidRequestException, IOException {
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[1024];
        try (Reader reader = request.getReader()) {
            int read;
            while ((read = reader.read(buffer)) > 0) {
                body.append(buffer, 0, read);
                if (body.length() > MAX_BODY) {
                    throw new InvalidRequestException("The request is too large.");
                }
            }
        }
        try {
            T value = body.toString().isBlank() ? null : JsonSupport.MAPPER.readValue(body.toString(), type);
            if (value == null) {
                throw new InvalidRequestException("The request has no JSON body.");
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("The request body is not the JSON this address expects.");
        }
    }

    protected static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        JsonSupport.write(response, status, body);
    }
}
