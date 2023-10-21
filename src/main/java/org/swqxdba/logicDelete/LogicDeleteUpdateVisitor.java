package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;

import java.util.concurrent.atomic.AtomicReference;

public class LogicDeleteUpdateVisitor extends SQLASTVisitorAdapter {
    LogicDeleteConfig config;

    DbType dbType;



    public LogicDeleteUpdateVisitor(LogicDeleteConfig filter, DbType dbType) {
        this.config = filter;
        this.dbType = dbType;
    }

    @Override
    public boolean visit(SQLUpdateStatement sqlUpdateStatement) {
        final SQLTableSource tableSource = sqlUpdateStatement.getTableSource();
        SQLExpr where = sqlUpdateStatement.getWhere();
        final AtomicReference<SQLExpr> conditionWrapper = new AtomicReference<>(where);
        final LogicDeleteTableSourceFilter filter = new LogicDeleteTableSourceFilter(config, dbType);
        filter.handleDispatch(tableSource, conditionWrapper);
        where = conditionWrapper.get();
        if (where != null) {
            sqlUpdateStatement.setWhere(where);
        }
        return super.visit(sqlUpdateStatement);
    }


}
