package org.swqxdba.logicDelete;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.logging.Logger;

public class LogicDeleteDatasource implements DataSource {

    DataSource dataSource;

    LogicDeleteHandler logicDeleteHandler;



    public LogicDeleteDatasource(DataSource dataSource, LogicDeleteHandler logicDeleteHandler) {
        this.dataSource = dataSource;
        this.logicDeleteHandler = logicDeleteHandler;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return new LoginDeleteConnection(dataSource.getConnection(),logicDeleteHandler);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return new LoginDeleteConnection(dataSource.getConnection(username,password),logicDeleteHandler);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return dataSource.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        dataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }
}
