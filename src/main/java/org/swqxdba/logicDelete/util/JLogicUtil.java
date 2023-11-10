package org.swqxdba.logicDelete.util;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class JLogicUtil {

    /**
     * 在一个代码块中禁用逻辑删除拦截 注意 jpa等框架可能在commit时候才执行sql 请保证代码块内的sql在方法返回之前执行完毕
     */
    public static void closeHandleScope(Runnable runnable) {
        try {
            JLogicThreadLocal.enableHandle.set(false);
            runnable.run();
        } finally {
            JLogicThreadLocal.enableHandle.set(true);
        }
    }

    /**
     * 在一个代码块中禁用逻辑删除拦截 注意 jpa等框架可能在commit时候才执行sql 请保证代码块内的sql在方法返回之前执行完毕
     */
    public static <T> T closeHandleScope(Supplier<T> supplier) {
        try {
            JLogicThreadLocal.enableHandle.set(false);
            return supplier.get();
        } finally {
            JLogicThreadLocal.enableHandle.set(true);
        }
    }


    /**
     * @return 当前线程是否需要进行逻辑删除处理
     */
    public static boolean whetherHandleEnable() {
        Boolean enable = JLogicThreadLocal.enableHandle.get();
        return enable == null || enable;
    }

    /**
     * 设置当前线程是否需要进行逻辑删除处理
     */
    public static void setHandleEnableState(boolean enableState) {
        JLogicThreadLocal.enableHandle.set(enableState);
    }

    public static Map<String, List<String>> resolveMysqlTables(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, null, null);

            Map<String, List<String>> tableColumns = new HashMap<>();
            while (columns.next()) {
                String tableName = columns.getString("TABLE_NAME");
                List<String> columnsList = tableColumns.computeIfAbsent(tableName, k -> new ArrayList<>());
                columnsList.add(columns.getString("COLUMN_NAME"));
            }
            return tableColumns;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static void printRows(ResultSet resultSet) {
        try {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (resultSet.next()) {
                for (int i = 0; i < columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i + 1);
                    String value = resultSet.getString(i + 1);
                    System.out.println(columnName + ":" + value);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
