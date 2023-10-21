package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogicDeleteSelectVisitor extends SQLASTVisitorAdapter {
    private static final Logger log = LoggerFactory.getLogger(LogicDeleteHandler.class);
    LogicDeleteConfig filter;

    DbType dbType;

    public LogicDeleteSelectVisitor(LogicDeleteConfig filter, DbType dbType) {
        this.filter = filter;
        this.dbType = dbType;
    }

    @Override
    public boolean visit(SQLSelect sqlSelect) {
        final SQLTableSource from = sqlSelect.getQueryBlock().getFrom();
        handleSelectWhereCondition(sqlSelect,from);
        return super.visit(sqlSelect);
    }

    /**
     * <p/>拼接where条件(不包括join on的条件)<br/>
     * <p/>如果是from简单的表 则直接拼接。如果是from是各种join 则依据情况递归找出其中的驱动表
     * <p/> select * from person => where person.deleted = 0
     * <br/> select * from person,teacher => where person.deleted = 0 and teacher.deleted = 0
     * <p/>特殊情况: select * from person,teacher,student [这个是两个嵌套的JoinType.COMMA类型 需要递归解析 相当于select * from (person,teacher),student)]

     *  <br/><br/> select * from person left join teacher =>
     * <br/> select * from person left join teacher where person.deleted = 0
     */
    private void handleSelectWhereCondition(SQLSelect sqlSelect, SQLTableSource from){
        //单表select
        if(from instanceof SQLExprTableSource){
            final SQLExpr tableFilterExpr = getTableFilterExpr(from);
            final SQLExpr condition = sqlSelect.getQueryBlock().getWhere();
            //被主动忽略了
            if (!LoginDeleteUtil.needHandlerCondition((SQLExprTableSource) from, condition, filter)) {
                return ;
            }
            if(tableFilterExpr!=null){
                sqlSelect.addWhere(tableFilterExpr);
            }
        }else if(from instanceof SQLJoinTableSource){//连表
            final SQLJoinTableSource joinSource = (SQLJoinTableSource) from;

            switch (joinSource.getJoinType()){
                case COMMA:case INNER_JOIN://.连接 比如select * from person,student,teacher;
                    handleSelectWhereCondition(sqlSelect, joinSource.getLeft());
                    handleSelectWhereCondition(sqlSelect,joinSource.getRight());
                    break;
                case LEFT_OUTER_JOIN:
                    handleSelectWhereCondition(sqlSelect,joinSource.getLeft());
                    break;
                case RIGHT_OUTER_JOIN:
                    handleSelectWhereCondition(sqlSelect,joinSource.getRight());
                    break;
            }

        }

    }

    /**
     * 处理join 根据joinType分别对不同侧进行处理
     */
    @Override
    public boolean visit(SQLJoinTableSource join) {
        final SQLJoinTableSource.JoinType joinType = join.getJoinType();

        switch (joinType){
            case LEFT_OUTER_JOIN:
                addJoinConditionIfNeed(join.getRight(), join);
                break;
            case RIGHT_OUTER_JOIN:
                addJoinConditionIfNeed(join.getLeft(), join);
                break;
            case FULL_OUTER_JOIN:
                addJoinConditionIfNeed(join.getLeft(), join);
                addJoinConditionIfNeed(join.getRight(), join);
                break;
        }

        return super.visit(join);
    }

    /**
     * 尝试添加过滤条件
     */
    private void addJoinConditionIfNeed(SQLTableSource table, SQLJoinTableSource join) {
        //SQLExprTableSource表示一个真实的表?
        if (table instanceof SQLExprTableSource) {

            //被主动忽略了
            if (!LoginDeleteUtil.needHandlerCondition((SQLExprTableSource) table, join.getCondition(), filter)) {
                return;
            }
            //获取过滤条件
            final SQLExpr tableFilterExpr = getTableFilterExpr(table);
            if (tableFilterExpr != null) {
                join.addCondition(tableFilterExpr);
            }
        }
    }


    /**
     * TODO 加缓存...
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
        final String sql = filter.filterDataSql(tableName);
        return SQLUtils.toSQLExpr(sql, dbType);
    }
}
