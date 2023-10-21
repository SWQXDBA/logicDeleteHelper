package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExprGroup;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlDeleteStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LogicDeleteDeleteVisitor extends SQLASTVisitorAdapter {

    LogicDeleteConfig config;

    DbType dbType;

    SQLUpdateStatement result;

    public LogicDeleteDeleteVisitor(LogicDeleteConfig config, DbType dbType) {
        this.config = config;
        this.dbType = dbType;
    }

    @Override
    public boolean visit(SQLDeleteStatement deleteStatement) {
        /*
         * 拼接条件 筛选出未被逻辑删除的数据。
         * 只有未被逻辑删除的数据才能被update 否则可能发生意外的变更。
         * 如果是delete person,或者delete from person 此时getFrom返回null(被优化成tableSource了)
         * 只有delete person from person时 getFrom才不会是null
         */
        final LogicDeleteTableSourceFilter filter = new LogicDeleteTableSourceFilter(config, dbType);
        AtomicReference<SQLExpr> reference = new AtomicReference<>(deleteStatement.getWhere());
        if (deleteStatement.getFrom() != null) {
            filter.handleDispatch(deleteStatement.getFrom(), reference);
        } else if (deleteStatement.getTableSource() != null) {
            filter.handleDispatch(deleteStatement.getTableSource(), reference);
        }


        //将要更新的表分析出来 用于后续构建update语句。
        //注意 分析的应该是tableSource 而不是from

        SQLUpdateStatement sqlUpdateStatement = new SQLUpdateStatement(dbType);

        sqlUpdateStatement.setTableSource(deleteStatement.getTableSource());
        sqlUpdateStatement.setFrom(deleteStatement.getFrom());
        sqlUpdateStatement.setWhere(reference.get());
        //mysql的delete中有order by 但是update语句中好像不支持，先放着吧。
        if (dbType == DbType.mysql) {
            final MySqlDeleteStatement mySqlDeleteStatement = (MySqlDeleteStatement) deleteStatement;
            sqlUpdateStatement.setOrderBy(mySqlDeleteStatement.getOrderBy());
        }


        //接下来要拼接真正的set了
        final List<SQLExprTableSource> targetTables = getTargetTables(deleteStatement.getTableSource());

        for (SQLExprTableSource targetTable : targetTables) {
            String name;
            if (targetTable.getAlias() != null) {
                name = targetTable.getAlias();
            } else {
                name = targetTable.getTableName();
            }
            final List<String> updateSetSqls = config.doLogicDeleteSql(name);
            for (String updateSetSql : updateSetSqls) {
                final SQLUpdateSetItem sqlExpr = SQLUtils.toUpdateSetItem(updateSetSql, dbType);
                sqlUpdateStatement.addItem(sqlExpr);
            }
        }
        this.result = sqlUpdateStatement;

        return super.visit(deleteStatement);

    }

    private List<SQLExprTableSource> getTargetTables(SQLTableSource root) {
        List<SQLExprTableSource> res = new ArrayList<>();
        if (root instanceof SQLJoinTableSource) {
            final SQLJoinTableSource joinTableSource = (SQLJoinTableSource) root;
            res.addAll(getTargetTables(joinTableSource.getLeft()));
            res.addAll(getTargetTables(joinTableSource.getRight()));
        } else if (root instanceof SQLExprTableSource) {
            res.add((SQLExprTableSource) root);
        }
        return res;
    }


    public SQLUpdateStatement getResult() {
        return result;
    }
}
