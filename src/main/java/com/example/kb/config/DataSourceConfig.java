package com.example.kb.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 两个数据源：MySQL 是主库（MyBatis-Plus 用），PostgreSQL 只拿来读写向量。
 * 把 MySQL 标成 @Primary，MyBatis-Plus 的自动配置才认得出来。
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource mysqlDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("kb.vector.datasource")
    public DataSourceProperties vectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource vectorDataSource(
            @Qualifier("vectorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * 主库 JdbcTemplate 必须显式声明：容器里一旦存在 JdbcTemplate 类型的 bean
     * （下面的 vectorJdbcTemplate 就是），JdbcTemplateAutoConfiguration 会整体退让，
     * 自动配置的 jdbcTemplate 不再创建，按类型注入会直接报 NoSuchBeanDefinition。
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("mysqlDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
