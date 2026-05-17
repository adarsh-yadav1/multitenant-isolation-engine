package com.saas.multitenant.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${datasource.master.url}") String url,
            @Value("${datasource.master.username}") String username,
            @Value("${datasource.master.password}") String password,
            @Value("${datasource.master.hikari.pool-name:MasterPool}") String poolName,
            @Value("${datasource.master.hikari.maximum-pool-size:10}") int maximumPoolSize,
            @Value("${datasource.master.hikari.minimum-idle:2}") int minimumIdle) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName(poolName);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        return dataSource;
    }
}
