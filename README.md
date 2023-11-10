# 逻辑删除拦截器
本项目用于提供逻辑删除的通用实现，ORM无关，可以在JDBC层面对各种sql进行改写。  

本项目提供了一个简单的包装数据源(LogicDeleteDatasource)  
用于包装原始数据源，然后拦截所有sql语句实现逻辑删除。



## 使用方式
您需要包装原始的数据源，然后使用包装过的数据源。  

您需要手动实现一个配置类：`LogicDeleteConfig`  
有一个示例实现： `LogicDeleteConfigExample` 您可以参考它
```java
package org.swqxdba.logicDelete;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogicDeleteConfigExample implements LogicDeleteConfig {
    @Override
    public boolean shouldInterceptSql(String sql) {
        return true;
    }

    @Override
    public String filterDataSql(String tableOrAliasName) {
        return tableOrAliasName + ".deleted = 0";
    }

    @Override
    public boolean shouldInterceptTable(String tableName) {
        return true;
    }

    @Override
    public Set<String> logicDeleteDependentFields(String tableName) {
        return new HashSet<String>(){{add("deleted");}};
    }

    @Override
    public List<String> doLogicDeleteSql(String tableOrAliasName) {
        return Arrays.asList(tableOrAliasName+".deleted = 1","version = version+1");
    }
}

```
在这个配置中，我们规定了所有表的逻辑删除字段为deleted,   
逻辑删除的判断条件为 table.deleted = 0    
执行逻辑删除时 需要把table.delete 设置为1，version设置为version+1  

接下来配置这个数据源给项目使用。
```java
class Demo{
    public DataSource wrapper(DataSource dataSource){
        LogicDeleteConfig config = new LogicDeleteConfigExample();
        LogicDeleteHandler handler = new LogicDeleteHandler(config, DbType.mysql);
        LogicDeleteDatasource logicDeleteDatasource = new LogicDeleteDatasource(dataSource,handler);
    }
}
```
# 改写规则
## select语句

select语句中 会给表拼接条件。  
如果是left join中引入的表 会将条件拼接在on子句中。否则拼接在where子句中。  
因为如果应用在了where条件中，那么left join后的那个字段可能是null 可能导致数据被意外地过滤掉。

支持的join： 

* inner join    (如select * from table inner join table2)
* join    (如select * from table join table2)
* left join    (如select * from table left join table2)
* full join    (如select * from table full join table2)
* right join  (如select * from table right join table2)
* 默认join    (如select * from table,table2)

此外 exists,各种嵌套子查询语句也支持。

示例:
```java
    @Test
    void subQueryTest() {
        //注意这里有意地在left后面的on中调换了条件的顺序 来检测是否挑选了合适的表来添加条件。
        //left outer join会被替换成 left join
        String sql = "select * from (select id from person) t where t.id > 50";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("select * from ( select id from person " +
                "where person.deleted = 0 ) t " +
                "where t.id > 50", handler);
    }

```

## update语句
update语句会在where中拼接上逻辑删除条件，避免逻辑删除的数据被意外更新。
支持基本的多表update  

示例:
```java
    @Test
    public void simpleUpdateTest(){
        String sql = "update person set name = ? where id > 1";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person set name = ? where id > 1 " +
                "and person.deleted = 0", handler);
    }

```

## delete

delete将被改写成update语句。  
同时会应用逻辑删除的查询条件，保证已被删除的数据不会被该update影响。

delete语句的改写支持多张表,如delete a,b from a,b where xxx  
但是只有最低限度的支持。  
不支持在delete中进行groupBy join limit等操作。  
*尽量别在delete中搞骚操作。保持简单。*

示例:
```java
    @Test
    public void deleteFromTest(){
        String sql = "delete from person where person.id > 5";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person " +
                "set person.deleted = 1, version = version + 1 " +
                "where person.id > 5 and person.deleted = 0", handler);
    }

```
注意，这里对person拼接了逻辑删除的查询条件，保证已被逻辑删除的数据不会被该update影响。

---

## 自动忽略手动指定的条件

### 示例1
如果您手动在where/on子句中指定了逻辑删除所依赖字段  
(LogicDeleteConfig.logicDeleteDependentFields)的条件，  
那么这个表的条件不会被改写：

>select * from person where person.deleted = 0  
此时检测到指定了person.delete字段 所以不会改写 (实际上会重新生成sql 但是逻辑不变)

### 示例2
由于只有person表存在手动指定的逻辑删除字段条件，而student表没有，
所以会进行改写，给student表添加条件:

>select * from person,student where person.deleted = 0  
改写sql: select * from person,student where person.deleted = 0 and ***student.deleted = 0***


## 获取表信息 部分表应用逻辑删除
如果您想对项目中的部分表应用逻辑删除逻辑，一个简单的判断方法是看看这个表是否有逻辑删除字段。  
您可以参考`JLogicUtil.resolveMysqlTables()`来获取表信息。
获取表信息后维护到您的`LogicDeleteConfig`对象中，然后在实现`shouldInterceptTable`方法中进行判断

伪代码例子: 
```java
import com.alibaba.druid.sql.ast.statement.SQLShowOutlinesStatement;

class Demo {
    public DataSource wrapper(DataSource dataSource) {
        LogicDeleteConfig config = new LogicDeleteConfigExample() {
            Map<String, List<String>> tableFields;
            {
                tableFields = LogicUtil.resolveMysqlTables(dataSource.getConnection());
            }
            @Override
            public boolean shouldInterceptTable(String tableName){
                for(String column:tableFields.get(tableName)){
                    //如果有deleted字段 则应用逻辑删除
                    if(column.equals("deleted")){
                        return true;
                    }
                }
                return false;
            }
        };
        LogicDeleteHandler handler = new LogicDeleteHandler(config, DbType.mysql);
        LogicDeleteDatasource logicDeleteDatasource = new LogicDeleteDatasource(dataSource, handler);
    }
}
```
## 测试sql语句
如果您对生成的sql逻辑不放心 可以简单地进行测试来获得生成的sql  
通过LogicDeleteHandler.processSql来对一条sql进行改写 返回改写后的sql
```java
import org.swqxdba.logicDelete.LogicDeleteHandler;

class Demo {
    public String testSql(String sql) {
        LogicDeleteConfig config = new LogicDeleteConfigExample();
        LogicDeleteHandler handler = new LogicDeleteHandler(config, DbType.mysql);
        return handler.processSql(sql);
    }
}
```


## sql缓存
为了避免大量对重复语句的逻辑删除转换，  
有一个粗暴的sql缓存实现 ConcurrentHashMap  
```java
    BiFunction<String, Supplier<String>,String> cacheImpl = new BiFunction<String, Supplier<String>, String>() {
        final ConcurrentHashMap<String, String> sqlCache = new ConcurrentHashMap<>();
        @Override
        public String apply(String sql, Supplier<String> stringSupplier) {
            if(sqlCache.size()>1000){
                sqlCache.clear();
            }
            return sqlCache.computeIfAbsent(sql,key->stringSupplier.get());
        }
    };

```

您可以使用LogicDeleteHandler.setCacheImpl()来替换实现。 如使用caffeine。  

这是一个BiFunction，第一个参数是处理前的原始sql，第二个参数是Supplier用来提供转换后的sql。  
返回的是处理过的sql语句。  

## 性能
一般而言性能是够用的。  
每毫秒可以处理几百条简单sql。  (sql越复杂越慢)


详见 LogicDeleteBenchMark  
在zen3 (5700x) cpu的测试时 测试结果可以达到 520条sql/毫秒  

此外缓存可以大幅提升性能。