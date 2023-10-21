package org.swqxdba.logicDelete;

import java.util.List;
import java.util.Set;

public interface LogicDeleteConfig {

    /**
     * 是否需要对该sql进行处理。 注意 传入的可能是多条合并的sql语句。
     */
    boolean shouldInterceptSql(String sql);

    /**
     * 如何编写筛选数据的条件语句(传入的tableName可能实际是alias 不过无需关心)<br/>
     * example:
     * <p/>
     * (tableName)=> tableName+".deleted = false";
     */
    String filterDataSql(String tableName);

    /**
     * 是否对该表进行逻辑删除处理。<br/>
     * 注意 对于连接查询,或者嵌套的子查询，会对其中每个表/每次连接都调用该方法进行细致的判断。
     */
    boolean shouldInterceptTable(String tableName);

    /**
     * 逻辑删除所依赖的字段。
     * <br/>example: ["deleted"] 表示逻辑删除功能依赖了deleted这个字段。
     * <br/>如果全部相关的字段已经拥有手动指定的条件，会忽略该表的逻辑删除功能(不包括delete的转换，delete一定会被转换成update语句)。
     * <br/>
     *
     * @return 逻辑删除所依赖的字段，如果返回null/emptyList表示无条件进行逻辑删除处理。
     */
    Set<String> logicDeleteDependentFields(String tableName);

    /**
     * 执行逻辑删除时的赋值 不需要指定where条件,转换时会把delete语句的条件拼上去。
     * <br/>
     * example:<br/>
     * return Arrays.asList(tableName+".deleted = 1","version = version+1");
     * <p>
     *     原始sql: delete table where id = ?<br/>
     *     改写sql: update table set table.deleted = 1, version = version+1 where id = ? and table.deleted = 0
     * </p>
     */
    List<String> doLogicDeleteSql(String tableName);

    /**
     * 是否格式化sql语句。
     */
    default boolean formatSql() {
        return false;
    }

    default boolean logTranslatedSql(){
        return false;
    }


}
