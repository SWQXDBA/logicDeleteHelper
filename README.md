# 逻辑删除拦截器
本项目用于提供逻辑删除的通用实现，ORM无关，可以在JDBC层面对各种sql进行改写。  

本项目提供了一个简单的包装数据源(LogicDeleteDatasource)  
用于包装原始数据源，然后拦截所有sql语句实现逻辑删除。



## 使用方式
您需要包装原始的数据源，然后使用包装过的数据源。  

您需要手动实现一个配置类：`LogicDeleteConfig`  
有一个示例实现： `LogicDeleteConfigExample` 您可以参考它
```java
class Demo{
    public DataSource wrapper(DataSource dataSource){
        LogicDeleteConfig config = new LogicDeleteConfigExample();
        LogicDeleteHandler handler = new LogicDeleteHandler(config, DbType.mysql);
        LogicDeleteDatasource logicDeleteDatasource = new LogicDeleteDatasource(dataSource,handler);
    }
}
```

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
## update语句
update语句会在where中拼接上逻辑删除条件，避免逻辑删除的数据被意外更新。
支持基本的多表update
## delete

delete将被改写成update语句。  
同时会应用逻辑删除的查询条件，保证已被删除的数据不会被该update影响。

delete语句的改写支持多张表,如delete a,b from a,b where xxx  
但是只有最低限度的支持。  
不支持在delete中进行groupBy join limit等操作。  
*尽量别在delete中搞骚操作。保持简单。*

## 自动忽略手动指定的条件

如果您手动在where/on子句中指定了逻辑删除所依赖字段  
(LogicDeleteConfig.logicDeleteDependentFields)  
的条件，那么这个表的条件不会被改写：

---
select * from person where person.deleted = 0  
此时检测到指定了person.delete字段 所以不会改写 (实际上会重新生成sql 但是逻辑不变)
---
select * from person,student where person.deleted = 0
> 改写sql: select * from person,student where person.deleted = 0 and ***student.deleted = 0***

由于只有person表存在手动指定的逻辑删除字段条件，而student表没有，所以会进行改写，给student表添加条件。

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