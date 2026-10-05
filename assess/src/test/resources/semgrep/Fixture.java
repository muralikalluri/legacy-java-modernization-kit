package fixture;

import java.sql.Connection;
import java.sql.Statement;

class Fixture {

    static final String COLS = "id, name";

    // POSITIVE: a String parameter concatenated into a statement that is executed
    void tainted(Connection con, String name) throws Exception {
        Statement st = con.createStatement();
        st.executeQuery("SELECT * FROM t WHERE n = '" + name + "'");
    }

    // NEGATIVE: constants only
    void constants(Connection con) throws Exception {
        Statement st = con.createStatement();
        st.executeQuery("SELECT " + COLS + " FROM t");
    }

    // NEGATIVE: bound parameters, and a helper that takes the SQL text itself
    void prepared(Connection con, String name) throws Exception {
        con.prepareStatement("SELECT * FROM t WHERE n = ?").setString(1, name);
        helper(con, "SELECT 1");
    }

    void helper(Connection con, String sql) throws Exception {
        con.prepareStatement(sql).executeQuery();
    }

    // POSITIVE: prepareStatement given a concatenated non-constant
    void badPrepared(Connection con, String name) throws Exception {
        con.prepareStatement("SELECT * FROM t WHERE n = '" + name + "'");
    }

    // NEGATIVE: constant literals joined across lines
    void literals(Connection con) throws Exception {
        con.prepareStatement("UPDATE t SET a = ?, " + "b = ? WHERE id = ?");
    }

    // POSITIVE and NEGATIVE password checks
    boolean plaintext(User user, String given) {
        return user.getPassword().equals(given);
    }

    boolean unrelatedEquals(String a, String b) {
        return a.equals(b);
    }

    interface User {
        String getPassword();
    }
}
