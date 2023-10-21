package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 专门用于处理表的条件。通过AtomicReference来传递上级的condition。<br/>
 * 最终添加条件的逻辑详见handleExprTableSource方法。<br/>
 * 如上级传递的condition是on条件 则会把条件拼接在on中。<br/>
 * 如上级传递的condition是where条件 则会把条件拼接在where中。<br/>
 */
public class LogicDeleteTableSourceFilter {
    LogicDeleteConfig config;

    DbType dbType;

    public LogicDeleteTableSourceFilter(LogicDeleteConfig config, DbType dbType) {
        this.config = config;
        this.dbType = dbType;
    }

    /**
     * 核心逻辑: 如果是joinTableSource 则继续递归 如果是exprTableSource则处理条件
     */
    public void handleDispatch(SQLTableSource exprTableSource, AtomicReference<SQLExpr> condition) {
        if (exprTableSource instanceof SQLExprTableSource) {
            handleExprTableSource((SQLExprTableSource) exprTableSource, condition);
        } else if (exprTableSource instanceof SQLJoinTableSource) {
            handleJoinTableSource((SQLJoinTableSource) exprTableSource, condition);
        }
    }


    /**
     * 处理连接表 并且返回where条件。
     * person left join school on person.school_id = school.id 要给school表加上条件
     * <br/>
     * 如果传入的where是null 需要另外生成一个，否则允许直接修改原来的where并返回。
     * @param whereCondition 如where子句 但不一定是where 也可以是上级join的on条件。
     */
    private void handleJoinTableSource(SQLJoinTableSource joinTableSource, AtomicReference<SQLExpr> whereCondition) {
        final SQLJoinTableSource.JoinType joinType = joinTableSource.getJoinType();
        final SQLExpr joinCondition = joinTableSource.getCondition();
        final AtomicReference<SQLExpr> joinWrapper = new AtomicReference<>(joinCondition);
        switch (joinType) {
            case INNER_JOIN:
            case JOIN:
            case LEFT_OUTER_JOIN://左连接 左表条件在where中 右表条件在on中
                //inner join的左表条件加在where中 右表条件加在on中
                handleDispatch(joinTableSource.getLeft(), whereCondition);
                handleDispatch(joinTableSource.getRight(), joinWrapper);
                break;
            case COMMA:
                //普通连接 条件要加在外层的where中 而不是on中
                handleDispatch(joinTableSource.getLeft(), whereCondition);
                handleDispatch(joinTableSource.getRight(), whereCondition);
                break;
            case FULL_OUTER_JOIN:
                handleDispatch(joinTableSource.getLeft(), joinWrapper);
                handleDispatch(joinTableSource.getRight(), joinWrapper);
                break;
            case RIGHT_OUTER_JOIN:
                handleDispatch(joinTableSource.getLeft(), joinWrapper);
                handleDispatch(joinTableSource.getRight(), whereCondition);
                break;
        }
        final SQLExpr newCondition = joinWrapper.get();
        if (newCondition != null) {
            joinTableSource.setCondition(newCondition);
        }
    }


    /**
     * 处理简单的表 比如 Person p
     */
    private void handleExprTableSource(SQLExprTableSource tableSource, AtomicReference<SQLExpr> where) {
        final SQLExpr currentCondition = where.get();
        if (!LogicDeleteUtil.needHandlerCondition(tableSource, currentCondition, config)) {
            return;
        }
        final SQLExpr sqlExpr = getTableFilterExpr(tableSource);
        //没有条件 则新建条件
        if (currentCondition == null) {
            where.set(sqlExpr);
        } else {
            //当前已经有了条件则用and拼接
            final SQLBinaryOpExpr and = new SQLBinaryOpExpr(dbType);
            and.setLeft(currentCondition);
            and.setRight(sqlExpr);
            and.setOperator(SQLBinaryOperator.BooleanAnd);
            where.set(and);
        }
    }

    /**
     * 获取该表的过滤条件 比如 person->person.deleted = 0
     */
    private SQLExpr getTableFilterExpr(SQLTableSource tableSource) {
        if (!(tableSource instanceof SQLExprTableSource)) {
            return null;
        }
        //一个表达式 表 而不是子查询表
        final SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) tableSource;
        String tableName = null;
        //尝试获取别名
        if (sqlExprTableSource.getAlias() != null) {
            tableName = sqlExprTableSource.getAlias();
        } else {
            //尝试获取真实的表名
            final SQLExpr expr = sqlExprTableSource.getExpr();
            if (expr instanceof SQLIdentifierExpr) {
                tableName = ((SQLIdentifierExpr) expr).getName();
            }
        }
        //不符合预期的格式 没能获得表名 则跳过
        if (tableName == null) {
            return null;
        }
        final String sql = config.filterDataSql(tableName);
        return SQLUtils.toSQLExpr(sql, dbType);
    }
}
