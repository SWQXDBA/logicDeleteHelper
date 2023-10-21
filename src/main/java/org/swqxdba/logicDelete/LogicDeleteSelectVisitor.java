package org.swqxdba.logicDelete;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * <p/>拼接where条件(不包括join on的条件)<br/>
 * <p/>如果是from简单的表 则直接拼接。如果是from是各种join 则依据情况递归找出其中的驱动表
 * <p/> select * from person => where person.deleted = 0
 * <br/> select * from person,teacher => where person.deleted = 0 and teacher.deleted = 0
 * <p/>特殊情况: select * from person,teacher,student [这个是两个嵌套的JoinType.COMMA类型 需要递归解析 相当于select * from (person,teacher),student)]
 * <p>
 * <br/><br/> select * from person left join teacher =>
 * <br/> select * from person left join teacher where person.deleted = 0
 */
public class LogicDeleteSelectVisitor extends SQLASTVisitorAdapter {
    private static final Logger log = LoggerFactory.getLogger(LogicDeleteHandler.class);
    LogicDeleteConfig config;

    DbType dbType;



    public LogicDeleteSelectVisitor(LogicDeleteConfig config, DbType dbType) {
        this.config = config;
        this.dbType = dbType;
    }

    @Override
    public boolean visit(SQLSelect sqlSelect) {
        final SQLSelectQueryBlock queryBlock = sqlSelect.getQueryBlock();
        final SQLTableSource from = queryBlock.getFrom();
        SQLExpr where = queryBlock.getWhere();
        final AtomicReference<SQLExpr> wrapper = new AtomicReference<>(where);
        new LogicDeleteTableSourceFilter(config, dbType).handleDispatch(from, wrapper);
        where = wrapper.get();
        if (where != null) {
            queryBlock.setWhere(where);
        }
        return super.visit(sqlSelect);
    }




}
