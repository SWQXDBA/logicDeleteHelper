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
