import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** 启动前使用与后端相同的 JDBC 配置验证登录，不修改数据库。 */
class CheckDatabase {
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("user", System.getenv("MALL_DATASOURCE_USERNAME"));
        properties.setProperty("password", System.getenv("MALL_DATASOURCE_PASSWORD"));
        String url = System.getenv("MALL_DATASOURCE_URL");
        // URL 参数优先级更高，因此在末尾覆盖超时，避免启动无限等待。
        url += (url.contains("?") ? "&" : "?") + "connectTimeout=5000&socketTimeout=5000";
        try (var connection = DriverManager.getConnection(url, properties);
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) System.exit(1);
            }
            System.out.println("MySQL login and query succeeded.");
        } catch (SQLException ex) {
            // 不输出异常消息，避免驱动将连接地址或账号写进启动日志。
            System.err.println("MySQL check failed. SQLState=" + ex.getSQLState()
                    + ", code=" + ex.getErrorCode()
                    + ". Check mall.local.env, database availability and account permissions.");
            System.exit(1);
        }
    }
}
