package logicDelete;

import com.alibaba.druid.DbType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.swqxdba.logicDelete.LogicDeleteConfigExample;
import org.swqxdba.logicDelete.LogicDeleteHandler;

public class DeleteTest {
    final LogicDeleteHandler logicDeleteHandler =
            new LogicDeleteHandler(new LogicDeleteConfigExample() {
                @Override
                public boolean formatSql() {
                    return true;
                }
            }, DbType.mysql);

    @Test
    public void simpleDeleteTest(){
        String sql = "delete person p where p.id = 1 or person.version = 0 ";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person p set p.deleted = 1, version = version + 1 where (p.id = 1 or person.version = 0) and p.deleted = 0", handler);
    }


    /**
     * 已手动指定了相关字段条件 p.deleted = 6 则不再添加该条件。
     */
    @Test
    public void existsConditionTest(){
        String sql = "delete person p where p.id = 1 or person.version = 0 and p.deleted = 6";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person p set p.deleted = 1, version = version + 1 where p.id = 1 or person.version = 0 and p.deleted = 6", handler);
    }

    @Test
    public void noDefaultConditionTest(){
        String sql = "delete person ";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person " +
                "set person.deleted = 1, version = version + 1 " +
                "where person.deleted = 0", handler);
    }

    /**
     * delete from
     */
    @Test
    public void deleteFromTest(){
        String sql = "delete from person where person.id > 5";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person " +
                "set person.deleted = 1, version = version + 1 " +
                "where person.id > 5 and person.deleted = 0", handler);
    }


    /**
     * 多表测试
     */
    @Test
    public void multiTableDeleteTest(){
        String sql = "delete person,school from person,school where person.id > 5";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person, school " +
                "set person.deleted = 1, version = version + 1, school.deleted = 1, version = version + 1 " +
                "where person.id > 5 and person.deleted = 0 and school.deleted = 0", handler);
    }


}
