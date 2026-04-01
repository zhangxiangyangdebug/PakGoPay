package com.pakgopay.common.config;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DataSource wrapper used to record connection acquire latency.
 */
public class TimingDataSource extends AbstractDataSource {

    private final DataSource delegate;

    public TimingDataSource(DataSource delegate, String label) {
        this.delegate = delegate;
    }

    public DataSource getDelegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }
}
