package com.pakgopay.common.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
public class DataSourceConnectionLogger {

    private final Environment environment;
    private final DataSource primaryDataSource;
    private final JdbcTemplate primaryJdbcTemplate;
    private final ObjectProvider<DataSource> secondaryDataSourceProvider;
    private final ObjectProvider<JdbcTemplate> secondaryJdbcTemplateProvider;

    public DataSourceConnectionLogger(
            Environment environment,
            DataSource primaryDataSource,
            @Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
            @Qualifier("secondaryDataSource") ObjectProvider<DataSource> secondaryDataSourceProvider,
            @Qualifier("secondaryJdbcTemplate") ObjectProvider<JdbcTemplate> secondaryJdbcTemplateProvider) {
        this.environment = environment;
        this.primaryDataSource = primaryDataSource;
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.secondaryDataSourceProvider = secondaryDataSourceProvider;
        this.secondaryJdbcTemplateProvider = secondaryJdbcTemplateProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConnections() {
        log.info("datasource properties, primary.url={}, primary.username={}, secondary.enabled={}, secondary.url={}, secondary.username={}",
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("pakgopay.datasource.secondary.enabled"),
                environment.getProperty("pakgopay.datasource.secondary.url"),
                environment.getProperty("pakgopay.datasource.secondary.username"));

        logMetadata("primary", primaryDataSource);
        logConnection("primary", primaryJdbcTemplate);

        DataSource secondaryDataSource = secondaryDataSourceProvider.getIfAvailable();
        JdbcTemplate secondaryJdbcTemplate = secondaryJdbcTemplateProvider.getIfAvailable();
        if (secondaryDataSource != null) {
            logMetadata("secondary", secondaryDataSource);
        }
        if (secondaryJdbcTemplate != null) {
            logConnection("secondary", secondaryJdbcTemplate);
        }
    }

    private void logMetadata(String label, DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            log.info("datasource metadata, label={}, jdbcUrl={}, jdbcUser={}",
                    label, connection.getMetaData().getURL(), connection.getMetaData().getUserName());
        } catch (Exception e) {
            log.error("datasource metadata failed, label={}, message={}", label, e.getMessage());
        }
    }

    private void logConnection(String label, JdbcTemplate jdbcTemplate) {
        try {
            String currentUser = jdbcTemplate.queryForObject("select current_user", String.class);
            String database = jdbcTemplate.queryForObject("select current_database()", String.class);
            String schema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            String searchPath = jdbcTemplate.queryForObject("select current_setting('search_path')", String.class);
            String collectionOrder = jdbcTemplate.queryForObject(
                    "select to_regclass('public.collection_order')", String.class);
            String payOrder = jdbcTemplate.queryForObject(
                    "select to_regclass('public.pay_order')", String.class);
            String merchantInfo = jdbcTemplate.queryForObject(
                    "select to_regclass('public.merchant_info')", String.class);
            log.info("datasource check, label={}, currentUser={}, database={}, schema={}, searchPath={}, public.collection_order={}, public.pay_order={}, public.merchant_info={}",
                    label, currentUser, database, schema, searchPath, collectionOrder, payOrder, merchantInfo);
        } catch (Exception e) {
            log.error("datasource check failed, label={}, message={}", label, e.getMessage());
        }
    }

    /**
     * Log current hikari pool metrics for quick troubleshooting.
     */
    public void logHikariPoolStats(String scene) {
        logHikariPoolStats("primary", primaryDataSource, scene);
        DataSource secondaryDataSource = secondaryDataSourceProvider.getIfAvailable();
        if (secondaryDataSource != null) {
            logHikariPoolStats("secondary", secondaryDataSource, scene);
        }
    }

    private void logHikariPoolStats(String label, DataSource dataSource, String scene) {
        DataSource actual = unwrapDataSource(dataSource);
        if (!(actual instanceof HikariDataSource hikariDataSource)) {
            log.info("hikari pool stats skipped, label={}, scene={}, reason=not_hikari", label, scene);
            return;
        }
        try {
            HikariPoolMXBean mxBean = hikariDataSource.getHikariPoolMXBean();
            if (mxBean == null) {
                log.info("hikari pool stats skipped, label={}, scene={}, reason=mxbean_null", label, scene);
                return;
            }
            log.info(
                    "hikari pool stats, label={}, scene={}, poolName={}, active={}, idle={}, pending={}, total={}",
                    label,
                    scene,
                    hikariDataSource.getPoolName(),
                    mxBean.getActiveConnections(),
                    mxBean.getIdleConnections(),
                    mxBean.getThreadsAwaitingConnection(),
                    mxBean.getTotalConnections());
        } catch (Exception e) {
            log.warn("hikari pool stats failed, label={}, scene={}, message={}", label, scene, e.getMessage());
        }
    }

    private DataSource unwrapDataSource(DataSource dataSource) {
        DataSource current = dataSource;
        while (current instanceof TimingDataSource timingDataSource) {
            current = timingDataSource.getDelegate();
        }
        return current;
    }
}
