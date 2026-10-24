package br.com.fiap.postech.carworkshop.shared.infrastructure.exception.mapper;

import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
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

class ConflictExceptionMapperTest {

    private ConflictExceptionMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ConflictExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/work-orders/1/cancel"));
        Field field = ConflictExceptionMapper.class.getDeclaredField("uriInfo");
        field.setAccessible(true);
        field.set(mapper, uriInfo);
    }

    @Test
    void toResponse_returns409() {
        Response response = mapper.toResponse(new ConflictException("already repaired"));

        assertEquals(409, response.getStatus());
        assertInstanceOf(StandardError.class, response.getEntity());
    }

    @Test
    void toResponse_carriesTheMessage() {
        Response response = mapper.toResponse(new ConflictException("already repaired"));

        assertEquals("already repaired", ((StandardError) response.getEntity()).getError());
    }
}
