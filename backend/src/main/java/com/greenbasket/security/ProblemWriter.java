package com.greenbasket.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes RFC 7807 problem bodies from servlet-filter code, where controller advice does not apply. */
@Component
public class ProblemWriter {

    private final ObjectMapper mapper;

    public ProblemWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletResponse res, int status, String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        res.setStatus(status);
        res.setContentType("application/problem+json");
        mapper.writeValue(res.getOutputStream(), body);
    }
}
