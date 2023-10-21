package org.swqxdba.logicDelete;

import java.util.HashSet;
import java.util.Set;

public class LogicDeleteConfigExample implements LogicDeleteConfig {
    @Override
    public boolean shouldInterceptSql(String sql) {
        return true;
    }

    @Override
    public String filterDataSql(String tableName) {
        return tableName + ".deleted = 0";
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
    public String doLogicDeleteSql(String tableName) {
        return "update " + tableName + " set deleted = 1,version = version+1";
    }
}
