package org.swqxdba.logicDelete;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public class LogicDeleteUtil {

    /**
     * SQLExprTableSource 通常为数据库中的一张表。如Person p
     * 根据表名来判断是否需要附加条件。必须传入原始的表名 而不是别名。
     */
    public static boolean needHandlerCondition(SQLExprTableSource tableSource, SQLExpr condition, LogicDeleteConfig filter) {
        //如无已有条件 则直接添加。
        if (condition == null) {
            return true;
        }
        final String tableName = tableSource.getName().getSimpleName();
        //根据表名判断是否需要处理该表
        if (!filter.shouldInterceptTable(tableName)) {
            return false;
        }

        //获取所有的依赖字段
        final Set<String> fields = filter.logicDeleteDependentFields(tableName);
        if (fields == null || fields.isEmpty()) {
            return true;
        }

        Deque<SQLObject> queue = new ArrayDeque<>(condition.getChildren());
        while (!queue.isEmpty()) {
            final SQLObject one = queue.pop();
            if (one instanceof SQLExpr) {
                queue.addAll(((SQLExpr) one).getChildren());
            }
            //指的是select * from person where deleted = 0 此时deleted没有前缀
            if (one instanceof SQLIdentifierExpr) {
                //此时有歧义 不处理
                //fields.remove(((SQLIdentifierExpr) one).getSimpleName());
                //fields.remove(((SQLIdentifierExpr) one).getName());

            } else if (one instanceof SQLPropertyExpr) { //指的是select * from person where person.deleted = 0 此时deleted有一个前缀
                // 当表有别名时 one的owner的名字就是表的别名 否则one的owner的名字为表名。
                String alias = tableSource.getAlias();
                if (alias == null) {
                    alias = tableName;
                }
                //判断one的owner和预期的一不一样 一样则说明已存在条件了。
                if (((SQLPropertyExpr) one).getOwnernName().equals(alias)) {
                    fields.remove(((SQLPropertyExpr) one).getSimpleName());
                    fields.remove(((SQLPropertyExpr) one).getName());
                }

            }
        }
        //全部条件满足(为空了) 则不做替换，不为空则做替换。
        return !fields.isEmpty();
    }





}
