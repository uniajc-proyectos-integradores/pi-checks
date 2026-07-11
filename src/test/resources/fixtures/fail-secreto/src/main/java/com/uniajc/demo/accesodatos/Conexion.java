package com.uniajc.demo.accesodatos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexion {
    private static final String URL = "jdbc:mysql://localhost:3306/demo";
    private static final String USUARIO = "root";
    private static final String PASSWORD = "secreto-sintetico-123";

    public Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL, "root", "secreto-sintetico-123");
    }
}
