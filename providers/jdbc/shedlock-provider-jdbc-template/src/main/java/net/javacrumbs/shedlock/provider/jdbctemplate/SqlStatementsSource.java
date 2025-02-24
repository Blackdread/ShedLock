/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

class SqlStatementsSource {
    protected final Configuration configuration;

    private static final Logger logger = LoggerFactory.getLogger(SqlStatementsSource.class);

    SqlStatementsSource(Configuration configuration) {
        this.configuration = configuration;
    }

    static SqlStatementsSource create(Configuration configuration) {
        String databaseProductName = getDatabaseProductName(configuration);

        if (configuration.getUseDbTime()) {
            switch (databaseProductName) {
                case "PostgreSQL" -> {
                    logger.debug("Using PostgresSqlServerTimeStatementsSource");
                    return new PostgresSqlServerTimeStatementsSource(configuration);
                }
                case "Microsoft SQL Server" -> {
                    logger.debug("Using MsSqlServerTimeStatementsSource");
                    return new MsSqlServerTimeStatementsSource(configuration);
                }
                case "Oracle" -> {
                    logger.debug("Using OracleServerTimeStatementsSource");
                    return new OracleServerTimeStatementsSource(configuration);
                }
                case "MySQL" -> {
                    logger.debug("Using MySqlServerTimeStatementsSource");
                    return new MySqlServerTimeStatementsSource(configuration);
                }
                case "MariaDB" -> {
                    logger.debug("Using MySqlServerTimeStatementsSource (for MariaDB)");
                    return new MySqlServerTimeStatementsSource(configuration);
                }
                case "HSQL Database Engine" -> {
                    logger.debug("Using HsqlServerTimeStatementsSource");
                    return new HsqlServerTimeStatementsSource(configuration);
                }
                case "H2" -> {
                    logger.debug("Using H2ServerTimeStatementsSource");
                    return new H2ServerTimeStatementsSource(configuration);
                }
                default -> {
                    if (databaseProductName.startsWith("DB2")) {
                        logger.debug("Using Db2ServerTimeStatementsSource");
                        return new Db2ServerTimeStatementsSource(configuration);
                    }
                    throw new UnsupportedOperationException("DB time is not supported for '" + databaseProductName + "'");
                }
            }
        } else {
            if ("PostgreSQL".equals(databaseProductName)) {
                logger.debug("Using PostgresSqlServerTimeStatementsSource");
                return new PostgresSqlStatementsSource(configuration);
            } else {
                logger.debug("Using SqlStatementsSource");
                return new SqlStatementsSource(configuration);
            }
        }
    }

    private static String getDatabaseProductName(Configuration configuration) {
        try {
            return configuration.getJdbcTemplate().execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            logger.debug("Can not determine database product name " + e.getMessage());
            return "Unknown";
        }
    }

    @NonNull
    Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
        return Map.of(
            "name", lockConfiguration.getName(),
            "lockUntil", timestamp(lockConfiguration.getLockAtMostUntil()),
            "now", timestamp(ClockProvider.now()),
            "lockedBy", configuration.getLockedByValue(),
            "unlockTime", timestamp(lockConfiguration.getUnlockTime())
        );
    }

    @NonNull
    private Object timestamp(Instant time) {
        TimeZone timeZone = configuration.getTimeZone();
        if (timeZone == null) {
            return Timestamp.from(time);
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(Date.from(time));
            calendar.setTimeZone(timeZone);
            return calendar;
        }
    }


    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, :lockUntil, :now, :lockedBy)";
    }


    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :lockUntil, " + lockedAt() + " = :now, " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= :now";
    }

    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :lockUntil WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > :now";
    }

    public String getUnlockStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :unlockTime WHERE " + name() + " = :name";
    }

    String name() {
        return configuration.getColumnNames().getName();
    }

    String lockUntil() {
        return configuration.getColumnNames().getLockUntil();
    }

    String lockedAt() {
        return configuration.getColumnNames().getLockedAt();
    }

    String lockedBy() {
        return configuration.getColumnNames().getLockedBy();
    }

    String tableName() {
        return configuration.getTableName();
    }
}
