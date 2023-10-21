package logicDelete;

import com.alibaba.druid.DbType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.swqxdba.logicDelete.LogicDeleteConfigExample;
import org.swqxdba.logicDelete.LogicDeleteHandler;

public class UpdateTest {
    final LogicDeleteHandler logicDeleteHandler =
            new LogicDeleteHandler(new LogicDeleteConfigExample() {
                @Override
                public boolean formatSql() {
                    return true;
                }
            }, DbType.mysql);

    /**
     * 简单更新测试
     */
    @Test
    public void simpleUpdateTest(){
        String sql = "update person set name = ? where id > 1";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person set name = ? where id > 1 " +
                "and person.deleted = 0", handler);
    }


    /**
     * 没有默认条件的更新 测试
     */
    @Test
    public void noDefaultConditionTest(){
        String sql = "update person set name = ? ";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update person set name = ? where person.deleted = 0", handler);
    }

    /**
     * 多表连接时的update语句测试
     */
    @Test
    public void joinUpdateTest(){
        String sql = "update person p,children cu join school ch on p.school_id = school.school_id set p.name = ? where school.school_id = 1";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update (person p, children cu) join school ch on p.school_id = school.school_id and ch.deleted = 0 " +
                "set p.name = ? where school.school_id = 1 and p.deleted = 0 and cu.deleted = 0", handler);
    }

    /**
     * 已手动制定了逻辑删除字段时 忽略条件测试。
     */
    @Test
    public void ignoreExistConditionTest(){
        String sql = "update person p,children cu join school ch on p.school_id = school.school_id set p.name = ? where p.deleted = 0 and school.school_id = 1";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("update (person p, children cu) join school ch on p.school_id = school.school_id and ch.deleted = 0 " +
                "set p.name = ? where p.deleted = 0 and school.school_id = 1 and cu.deleted = 0", handler);
    }


}
