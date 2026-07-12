package com.uniajc.demo.accesodatos;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConexionBd {
    private static final String DB_PASSWORD = "sintetico-uno";
    private static final String CLAVE_BD = "sintetico-dos";

    public Connection conectar() throws Exception {
        String pass = "sintetico-tres";
        return DriverManager.getConnection("jdbc:mysql://localhost/demo", "root", pass);
    }
}
