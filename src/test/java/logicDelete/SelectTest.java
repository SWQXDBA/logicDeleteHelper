package logicDelete;


import com.alibaba.druid.DbType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.swqxdba.logicDelete.LogicDeleteConfigExample;
import org.swqxdba.logicDelete.LogicDeleteHandler;

public class SelectTest {
    final LogicDeleteHandler logicDeleteHandler =
            new LogicDeleteHandler(new LogicDeleteConfigExample() {
                @Override
                public boolean formatSql() {
                    return true;
                }
            }, DbType.mysql);

    //多表直接连接测试
    @Test
    void multiTableSelectTest() {

        String sql = "select * from person,person p2,student,teacher";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("select * from person, person p2, student, teacher " +
                "where " +
                "person.deleted = 0 " +
                "and p2.deleted = 0 " +
                "and student.deleted = 0 " +
                "and teacher.deleted = 0", handler);
    }

    //三表 left join测试
    @Test
    void threeTableLeftJoinTest() {

        //注意这里有意地在left后面的on中调换了条件的顺序 来检测是否挑选了合适的表来添加条件。
        //left outer join会被替换成 left join
        String sql = "select f.name,p1.name as childrenName,p2.name as fatherName " +
                "from person p1 left outer join person p2 on p1.father_id = p2.id " +
                "left join family f on f.id = p1.family_id";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("select f.name, p1.name as childrenName, p2.name as fatherName " +
                "from person p1 left join person p2 on p1.father_id = p2.id " +
                "and p2.deleted = 0 " +
                "left join family f on f.id = p1.family_id " +
                "and f.deleted = 0 " +
                "where p1.deleted = 0", handler);

    }

    //sub query测试
    //子查询出来的子表与逻辑删除无关 所以t不应该被拼接条件
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

    //filter优化：已手动指定的条件不再修改
    @Test
    void ignoreExistConditionTest() {
        {
            String sql = "select * from person p where p.deleted = 0 and id > 3";
            final String handler = logicDeleteHandler.processSql(sql);
            Assertions.assertEquals("select * from person p where p.deleted = 0 and id > 3", handler);

        }
        {
            String sql = "select * from person p where id > 3";
            final String handler = logicDeleteHandler.processSql(sql);
            Assertions.assertEquals("select * from person p where id > 3 and p.deleted = 0", handler);
        }
    }


    //多列in测试
    @Test
    void multiColumnInConditionTest() {
        String sql = "select * from person p where (p.id,p.name) in ((1,2),(3,4))";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("select * from person p where (p.id, p.name) in ((1, 2), (3, 4)) and p.deleted = 0", handler);
    }

    //exist
    @Test
    void existSqlTest() {
        String sql = "select * from person p where exists (select * from school where p.school_id = school.id)";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("select * from person p where exists ( select * from school where p.school_id = school.id and school.deleted = 0 ) and p.deleted = 0", handler);
    }

    @Test
    void groupByTest(){
        String sql = "select p.name, max(p.age) from person p group by p.name order by max(p.age) desc";
        final String handler = logicDeleteHandler.processSql(sql);
        Assertions.assertEquals("select p.name, max(p.age) from person p " +
                "where p.deleted = 0 " +
                "group by p.name order by max(p.age) desc",handler);
    }
}
