package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.statement.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LogicDeleteHandler {
    private static final Logger log = LoggerFactory.getLogger(LogicDeleteHandler.class);

    LogicDeleteConfig config;

    DbType dbType;


    public LogicDeleteHandler(LogicDeleteConfig config, DbType dbType) {
        this.config = config;
        this.dbType = dbType;
    }

    static ConcurrentHashMap<String, String> sqlCache = new ConcurrentHashMap<>();

    public String handler(String oldSqlLines) {

        if (!config.shouldInterceptSql(oldSqlLines)) {
            return oldSqlLines;
        }
        final String parsedSql = sqlCache.computeIfAbsent(oldSqlLines, key -> {
            List<SQLStatement> statementList = SQLUtils.parseStatements(oldSqlLines, dbType);
            statementList = statementList.stream().map(this::handlerEach).collect(Collectors.toList());
            String sql = SQLUtils.toSQLString(statementList, dbType);
            if (config.formatSql()) {
                sql = SQLUtils.format(sql, dbType, new SQLUtils.FormatOption(false, false));
            }
            return sql;
        });
        if(config.logTranslatedSql()){
            log.info("old sql: "+oldSqlLines);
            log.info("logic delete sql:: " + parsedSql);
        }
        return parsedSql;

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
        final String tableName = deleteStatement.getTableName().getSimpleName();
        if (!config.shouldInterceptTable(tableName)) {
            return deleteStatement;
        }

        final String string = config.doLogicDeleteSql(tableName);
        final SQLStatement sqlStatement = SQLUtils.toStatementList(string, dbType).get(0);
        if (!(sqlStatement instanceof SQLUpdateStatement)) {
            throw new LogicDeleteParserException("delete -> update failure " +
                    "because not provide a update statement of sql: " + string);
        }
        final SQLUpdateStatement updateStatement = (SQLUpdateStatement) sqlStatement;
        final SQLExpr where = deleteStatement.getWhere();
        updateStatement.addCondition(where);
        return updateStatement;
    }

    private SQLStatement replaceUpdate(SQLUpdateStatement sqlUpdateStatement) {

        final String tableName = sqlUpdateStatement.getTableName().getSimpleName();
        if (!config.shouldInterceptTable(tableName)) {
            return sqlUpdateStatement;
        }
        final SQLExpr where = sqlUpdateStatement.getWhere();

        sqlUpdateStatement.setWhere(replaceWhere((SQLExprTableSource) sqlUpdateStatement.getTableSource(), where));
        return sqlUpdateStatement;
    }

    private SQLExpr replaceWhere(SQLExprTableSource tableSource, SQLExpr where) {

        if (!LoginDeleteUtil.needHandlerCondition(tableSource, where, config)) {
            return where;
        }
        final SQLBinaryOpExpr and = new SQLBinaryOpExpr(dbType);
        and.setLeft(where);
        final String filterSql = config.filterDataSql(tableSource.getName().getSimpleName());
        and.setRight(SQLUtils.toSQLExpr(filterSql, dbType));
        and.setOperator(SQLBinaryOperator.BooleanAnd);
        return and;
    }


    private SQLStatement replaceSelect(SQLSelectStatement sqlUpdateStatement) {
        final LogicDeleteSelectVisitor logicDeleteSelectVisitor = new LogicDeleteSelectVisitor(config, dbType);
        sqlUpdateStatement.accept(logicDeleteSelectVisitor);
        return sqlUpdateStatement;
    }
}
