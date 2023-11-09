package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swqxdba.logicDelete.util.JLogicUtil;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LogicDeleteHandler {
    private static final Logger log = LoggerFactory.getLogger(LogicDeleteHandler.class);

    LogicDeleteConfig config;

    DbType dbType;

    BiFunction<String, Supplier<String>, String> cacheImpl = new BiFunction<String, Supplier<String>, String>() {
        final ConcurrentHashMap<String, String> sqlCache = new ConcurrentHashMap<>();

        @Override
        public String apply(String sql, Supplier<String> stringSupplier) {
            if (sqlCache.size() > 1000) {
                sqlCache.clear();
            }
            return sqlCache.computeIfAbsent(sql, key -> stringSupplier.get());
        }
    };

    public LogicDeleteHandler(LogicDeleteConfig config, DbType dbType) {
        this.config = config;
        this.dbType = dbType;
    }

    /**
     * 设置缓存实现
     */
    public void setCacheImpl(BiFunction<String, Supplier<String>, String> cacheImpl) {
        this.cacheImpl = cacheImpl;
    }

    public String processSql(String oldSqlLines) {

        //通过threadLocal禁用了逻辑删除
        if(!JLogicUtil.whetherHandleEnable()){
            return oldSqlLines;
        }

        if (!config.shouldInterceptSql(oldSqlLines)) {
            return oldSqlLines;
        }
        String parsedSql = cacheImpl.apply(oldSqlLines, () -> translate(oldSqlLines));

        if (config.logTranslatedSql()) {
            log.info("old sql: " + oldSqlLines);
            log.info("logic delete sql:: " + parsedSql);
        }
        return parsedSql;

    }

    protected String translate(String oldSqlLines) {
        List<SQLStatement> statementList = SQLUtils.parseStatements(oldSqlLines, dbType);
        statementList = statementList.stream().map(this::handlerEach).collect(Collectors.toList());
        String sql = SQLUtils.toSQLString(statementList, dbType);
        if (config.formatSql()) {
            sql = SQLUtils.format(sql, dbType, new SQLUtils.FormatOption(false, false));
        }
        return sql;
    }


    private SQLStatement handlerEach(SQLStatement sqlStatement) {
        if (sqlStatement instanceof SQLDeleteStatement) {
            return replaceDelete((SQLDeleteStatement) sqlStatement);
        }
        if (sqlStatement instanceof SQLUpdateStatement) {
            return replaceUpdate((SQLUpdateStatement) sqlStatement);
        }
        if (sqlStatement instanceof SQLSelectStatement) {
            return replaceSelect((SQLSelectStatement) sqlStatement);
        }
        return sqlStatement;
    }

    private SQLStatement replaceDelete(SQLDeleteStatement deleteStatement) {
        final LogicDeleteDeleteVisitor visitor = new LogicDeleteDeleteVisitor(config, dbType);
        deleteStatement.accept(visitor);
        return visitor.getResult();
    }

    private SQLStatement replaceUpdate(SQLUpdateStatement sqlUpdateStatement) {
        sqlUpdateStatement.accept(new LogicDeleteUpdateVisitor(config, dbType));
        return sqlUpdateStatement;
    }


    private SQLStatement replaceSelect(SQLSelectStatement sqlUpdateStatement) {
        final LogicDeleteSelectVisitor logicDeleteSelectVisitor = new LogicDeleteSelectVisitor(config, dbType);
        sqlUpdateStatement.accept(logicDeleteSelectVisitor);
        return sqlUpdateStatement;
    }
}
