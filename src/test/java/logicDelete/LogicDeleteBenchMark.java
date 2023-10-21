package logicDelete;

import org.swqxdba.logicDelete.LogicDeleteConfigExample;
import org.swqxdba.logicDelete.LogicDeleteHandler;

import java.sql.SQLException;

public class LogicDeleteBenchMark {
    public static void main(String[] args) {
        final LogicDeleteHandler logicDeleteHandler =
                new LogicDeleteHandler(new LogicDeleteConfigExample(), null);
        // 这里有5条sql
        String sql = "select p.id,p.name, c.name as country_name from person p " +
                "inner join country c on p.county_id = c.id;" +
                "select c.name as country_name, count(*) as person_count from person p " +
                "inner join country c on p.country_id = c.id " +
                "group by p.country_id;";
        sql += "SELECT ID, NAME, AGE FROM USER WHERE ID = ?;";
        sql += "insert into tab;";
        sql += "update tab set id = 1;";
        sql += "delete tab where id > 10";

        //不使用缓存
        logicDeleteHandler.setCacheImpl((s, stringSupplier) -> stringSupplier.get());
        for (int j = 0; j < 10; j++) {
            {
                long now = System.currentTimeMillis();
                int batchSize = 1000000;
                for (int i = 0; i < batchSize; i++) {
                    logicDeleteHandler.processSql(sql);
                    // System.out.println();
                }
                final long mills = System.currentTimeMillis() - now + 1;//避免 /by zero
                System.out.println(mills + " mills");
                System.out.println(batchSize * 5.0 / mills + "/per mills");
            }
        }
    }

}
