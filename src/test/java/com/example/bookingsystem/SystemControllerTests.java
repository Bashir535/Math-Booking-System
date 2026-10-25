package com.example.bookingsystem;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.bookingsystem.controller.SystemController;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SystemControllerTests {

    @Test
    void databaseOutageReturns503WithoutInternalDetails() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("private database details"));
        var result = new SystemController(source).health();
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        assertThat(result.getBody())
            .containsEntry("status", "DOWN")
            .containsEntry("database", "DOWN");
        assertThat(result.getBody().toString()).doesNotContain("private database details");
    }
}
