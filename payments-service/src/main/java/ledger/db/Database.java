package ledger.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final DSLContext dsl;

    public Database(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);

        this.dataSource = new HikariDataSource(config);
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public DSLContext dsl() {
        return dsl;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}