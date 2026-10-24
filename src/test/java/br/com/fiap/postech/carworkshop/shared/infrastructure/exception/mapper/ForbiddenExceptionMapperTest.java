package br.com.fiap.postech.carworkshop.shared.infrastructure.exception.mapper;

import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.shared.infrastructure.exception.StandardError;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForbiddenExceptionMapperTest {

    private ForbiddenExceptionMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ForbiddenExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/tracking/1"));
        Field field = ForbiddenExceptionMapper.class.getDeclaredField("uriInfo");
        field.setAccessible(true);
        field.set(mapper, uriInfo);
    }

    @Test
    void toResponse_returns403() {
        Response response = mapper.toResponse(new ForbiddenException("belongs to another customer"));

        assertEquals(403, response.getStatus());
        assertInstanceOf(StandardError.class, response.getEntity());
    }

    @Test
    void toResponse_carriesTheMessage() {
        Response response = mapper.toResponse(new ForbiddenException("belongs to another customer"));

        assertEquals("belongs to another customer", ((StandardError) response.getEntity()).getError());
    }
}
